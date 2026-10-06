# 问答对提炼（qa-generation）— 测试设计

> 契约接口：qa-generation.QaGenerationService（trigger/status/execute，contract/interfaces.md `## qa-generation`）——本特性唯一深模块契约；控制器端点排除、消费/变更点经对应属主链回归。数据契约见 contract/data.md `## qa-generation`。
> **mock 边界（只落在真缝）**：LlmService（design 缝 1）、MessagePublisher（design 缝 2）、Milvus/Embedding/Rerank（检索链既有测试模式）、时钟（陈旧清扫判定注入）；MySQL 用真库（MyBatis-Plus）；复用件不 mock——normalizeQuestion 与 BenchmarkResponseParser 直用真实现；禁 mock 内部模块。
> **LLM 脚本化约定（S2/S3/S5）**：mock LlmService 按分片返回脚本队列（合法 JSON / 空 JSON 数组 / 截断 JSON / 超短产出），并可捕获 prompt 实参作断言——这是把 prompt 三约束（REQ-B02）变成可观察行为的唯一途径。

## 1. 切片规划

| 切片 | 接口组 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 产品裁决前置 | — | **无阻塞** | D1-D14 + P1-P6 已全裁（`fastrag/.tmp/idd-qa-generation-grill-decisions.md`），期望值全部已定；无 HITL 项 |
| S1 触发受理与参数校验 | QaGenerationService.trigger / status | AFK | 契约 5 错误码中 4 个的用例化（E_QA_GEN_PARAM_INVALID / FILE_INVALID / NOT_READY / TASK_RUNNING / QUEUE_UNAVAILABLE） |
| S2 生成编排与事务 | QaGenerationService.execute | AFK | 逐片 LLM→解析→落库；replace/append 语义；收尾事务原子性 |
| S3 质量闸门与判重基线 | execute（经公开接口观察） | AFK | 归一化判重（复用 normalizeQuestion 口径）、护栏阈值、replace/append 判重基线分叉 |
| S4 召回准入三分支 | retrieval.RetrievalSearchEngine（消费点回归，契约归 `## retrieval`） | AFK | 复用 RetrievalServiceImplTest 既有测试缝；**本特性最重要的回归点**（ADR-0005 的可观察面） |
| S5 任务状态机与互斥恢复 | trigger 覆盖语义 / execute 行态门 / 陈旧清扫 | AFK | 持久化互斥、重投防线、30 分钟清扫（时钟注入） |
| S6 摄入挂钩与级联一致性 | IngestionConsumer qa 分支 + file-ingestion / kb-core 公共接口 | AFK | 自动触发三例 + 级联回归（联合上游公共接口） |

## 2. 测试用例

### S1 触发受理与参数校验（contract: qa-generation.QaGenerationService；design: QaGenerationModule）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [受理] 缺省参数时 [落 running 并投递] | 合法文件且解析完成 | trigger(config 全缺省) | 任务行 status=running；config 快照 count=3 / mode=replace / model=null；publishQaGeneration 恰一次；kb_qa_pair 零增删 | A01 | MessagePublisher |
| [受理] count 为 0 或 11 时 [参数拒绝] | 合法文件 | trigger(count=0) 与 trigger(count=11)（两例） | E_QA_GEN_PARAM_INVALID；无任务行落库 | A01 | MessagePublisher |
| [受理] customInstruction 501 字符时 [参数拒绝] | 合法文件 | trigger | E_QA_GEN_PARAM_INVALID | A01 | 同上 |
| [受理] 文件查无或跨库时 [拒绝] | 文件属 KB-B | trigger(kbId=A, fileId=B 的文件) | E_QA_GEN_FILE_INVALID | A01 | 无 |
| [受理] 无父分片时 [未就绪拒绝] | 解析未完成文件 | trigger | E_QA_GEN_NOT_READY | A01 | 无（真库空数据） |
| [受理] 已有进行中任务时 [互斥拒绝且不覆盖] | 任务行 running（心跳新鲜） | trigger | E_QA_GEN_TASK_RUNNING；原任务行原样 | A04 | 无 |
| [受理] MQ 不可用时 [落 failed 并报错] | publisher 不可用/抛异常 | trigger | 任务行 failed（error=队列不可用）+ 业务错误返回；无消息投递 | NFR 异步 | MessagePublisher |
| [查询] 从未触发时 [返回空] | 无任务行 | status | null | D01 | 无 |
| [查询] 有历史任务时 [返回最近一次全貌] | 终态任务行 | status | 四态、三计数、config 快照、error、时间逐字段一致 | D01/D02 | 无 |

### S2 生成编排与事务（contract: qa-generation.QaGenerationService.execute；design: QaGenerationModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [执行] 3 分片每片 3 条时 [success 且产出 9 条] | 3 父分片、LLM 各返回 3 条合法 JSON | execute | 库中 9 条 source=ai + status=draft；source_chunk_id 逐条对应所在分片；任务行 success 3/0/3 计数一致 | B01/B04/C01/D01 | LlmService |
| [执行] prompt 断言时 [三约束与自定义指令可见] | customInstruction="只围绕参数出题" | execute（捕获 LLM prompt 实参） | prompt 含：分片全文、「答案仅来自分片内容」约束要素、自定义指令原文 | B02 | LlmService |
| [执行] 分片无可答题材料时 [零产出且正常计数] | LLM 对该分片返回空数组 | execute | 该分片产出 0 条；计入 succeeded（正常完结非失败） | B02 | LlmService |
| [执行] LLM 返回截断 JSON 时 [重试 1 次后分片失败] | 某分片两次调用均返回坏 JSON | execute | 该分片 failed_chunks=1；任务终态 partial；其余分片产出正常落库 | B03/B04 | LlmService |
| [执行] replace 完成时 [旧 ai 清零 manual 不动] | 该文件旧 ai 2 条 + manual 1 条 | execute(mode=replace) | 旧 ai 0 条、manual 1 条原样、新 ai 落库 | A05 | LlmService |
| [执行] append 完成时 [新旧共存] | 同上 | execute(mode=append) | 旧 ai 2 条仍在 + 新增若干 | A05 | LlmService |
| [执行] 收尾事务失败时 [failed 且 QA 对零变更] | 注入收尾写库失败（约束冲突） | execute(mode=replace) | status=failed；旧 ai 对 2 条全数保留（删旧在收尾事务，崩溃无损） | A05/NFR | LlmService |
| [执行] 全部分片失败时 [failed 且零产出] | 每分片两轮均坏 JSON | execute | status=failed（非 partial）、succeeded=0、库无新增 | B04 | LlmService |

### S3 质量闸门与判重基线（contract: 同上 execute；design: QaGenerationModule 隐藏实现）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [闸门] 全角产出遇库内半角同文时 [判重跳过] | 库内既有半角问题；LLM 返回全角同文 | execute | 该产出跳过计 failed_chunks；落库无重复 | B05 | LlmService |
| [闸门] replace 模式遇同文件旧 ai 同题时 [不入基线照常落库] | 该文件旧 ai 对与 LLM 新产出同题 | execute(replace) | 新产出正常落库（旧对将删不构成撞题） | A05/B05 | LlmService |
| [闸门] append 模式遇同文件旧 ai 同题时 [入基线跳过] | 同题 | execute(append) | 新产出跳过 | A05/B05 | LlmService |
| [闸门] 问题 4 字或答案 1 字时 [护栏丢弃] | LLM 返回超短问答两例 | execute | 丢弃计 failed_chunks、不落库 | B05 | LlmService |
| [闸门] manual 对同题时 [无论模式均跳过] | 库内 manual 同题 | execute(两模式) | 均跳过（manual 恒在判重基线） | B05 | LlmService |

### S4 召回准入三分支（contract: retrieval.RetrievalSearchEngine；design: 检索准入过滤变更点）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [检索] ai draft 命中时 [不返回] | 库内 ai+draft 命中记录 | search（token LIKE 命中） | 结果不含该记录 | C02 | Milvus/Embedding/Rerank（既有模式） |
| [检索] ai confirmed 命中时 [正常返回] | ai+confirmed | search | 出现且 source=qa | C02 | 同上 |
| [检索] manual draft 命中时 [仍返回]（现状口径） | manual+draft | search | 出现（ADR-0005 对 manual 零影响） | C02/D04 | 同上 |
| [检索] qa 通道既有语义时 [零变更回归] | 沿 qa-pairs tdd S4 四例（前置返回/跨词命中/异常降级/融合权重） | search | 逐条断言与 qa-pairs tdd S4 一致 | C02 | 同上 |

### S5 任务状态机与互斥恢复（contract: 同上；design: QaGenerationConsumerModule + 任务账本）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [状态] 分片混合成败时 [partial 终态] | 3 分片 1 失败 | execute 完结 | status=partial、succeeded=2、failed=1 | D01 | LlmService |
| [状态] 二次受理时 [覆盖旧任务行] | 已有终态行 | trigger | 行覆盖为 running、计数清零、config 为新值 | D01 | MessagePublisher |
| [行态门] 重投消息遇终态行时 [静默丢弃] | 任务行已 success | execute（模拟 MQ 重投） | 零产出零删除、计数不变、LlmService 零调用 | A04 | LlmService |
| [行态门] 重投消息遇新任务行时 [旧消息丢弃] | 行已被新 trigger 覆盖（config 不同） | execute(旧 config) | 丢弃；新任务不受扰 | A04 | LlmService |
| [清扫] running 超阈值时 [标执行中断] | running 行 updated_at=31 分钟前 | 陈旧判定触发 | status=failed、error 含「执行中断」；此后 trigger 可受理 | A04 | 时钟注入 |
| [清扫] running 心跳新鲜时 [不动] | updated_at=5 分钟前 | 同上 | 保持 running | A04 | 时钟注入 |
| [心跳] 分片完结时 [计数与 updated_at 即时回写] | 执行中（第二片刚完结） | 中途读 status | 计数反映已完成分片、updated_at 刷新（running 期间可观测进度） | D01/D02 | LlmService |

### S6 摄入挂钩与级联一致性（contract: 变更点登记；design: 摄入挂钩 + 上游契约）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [挂钩] qa 模式文件摄入完成时 [按文件配置自动触发] | kb_file.qa_generation_config 已落库 | 摄入收尾流程 | trigger 被调用恰一次；config 三元组来自文件列、mode=replace | A03 | MessagePublisher |
| [挂钩] chunk 模式文件时 [不触发] | processing_mode=chunk | 同上 | trigger 零调用 | A03 | 同上 |
| [挂钩] 撞互斥时 [摄入主流程不受阻] | 已有 running 任务行 | 摄入收尾 | 摄入正常完结；无新任务行、原行不动 | A03/A04 | 同上 |
| [重切] qa 模式文件请求时 [拒绝]（既有守卫回归） | processing_mode=qa | reChunkFile | badRequest（沿 qa-pairs tdd S5 断言） | E01 | 无 |
| [重切] chunk 模式重切后 [QA 对保留且悬空溯源不报错] | 该文件 ai 对带 source_chunk_id | reChunkFile | QA 对数量不变；按 fileId 查产出列表正常返回（悬空引用不抛错、置空不动） | D03/E01 | 向量组件按既有 |
| [删文件] 硬删时 [QA 对与任务行同批清理] | 该文件 ai 对 + 任务行 | FileService.delete（硬删路径） | kb_qa_pair 与 kb_qa_generation_task 该文件记录均清零 | E02 | MinIO |
| [删库] 时 [全量级联清零] | 多文件 ai/manual 对 + 任务行 | KbService.delete | 两表该 kb 记录清零 | E02 | MinIO/Milvus/Neo4j |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [受理] 互斥拒绝 | qa-generation.QaGenerationService.trigger（E_QA_GEN_TASK_RUNNING） | QaGenerationModule + QaGenerationConsumerModule（持久化互斥） | REQ-A04 |
| [执行] replace 收尾 | qa-generation.QaGenerationService.execute | QaGenerationModule（隐藏实现：收尾事务） | REQ-A05 |
| [闸门] 判重基线分叉 | 同上 | QaGenerationModule（normalizeQuestion 复用） | REQ-B05 |
| [检索] ai draft 不返回 | retrieval.RetrievalSearchEngine（qa 通道准入） | 检索准入过滤变更点（ADR-0005） | REQ-C02 |
| [清扫] 陈旧中断 | qa-generation.QaGenerationService（陈旧恢复不变式） | QaGenerationConsumerModule | REQ-A04 |
| [挂钩] 自动触发 | （变更点登记——IngestionConsumer qa 分支） | 摄入挂钩 | REQ-A03 |

## 4. 待实施说明

- **红绿循环未执行**（本链为测试设计；执行待用户指令，届时从 Tracer Bullet 起步）。
- **Tracer Bullet 建议**：S1「缺省参数受理」→ S2「3 分片 success」→ S4「ai draft 不返回」三例贯通「受理 → 产出 → 准入」最小纵切——这是本特性区别于 qa-pairs 的主纵切，先于其余用例。
- **DDL 双处同步义务不设测试切片**，列为 implement 验收清单项（contract/data.md `## qa-generation` 章首义务条款：kb_qa_generation_task 表 + 两扩列必须 SchemaInitializer 与 schema.sql 双处声明一致）。
- **LLM mock 脚本化基建**：S2/S3/S5 共用「按分片返回队列 + prompt 实参捕获」的 stub，属测试基建先行项（约一处测试工具类，非生产代码）。
- 若执行时 debt 已先行修复或裁决被推翻（如 ADR-0005 准入规则调整），先改对应断言再跑红绿，并回写本文件。
