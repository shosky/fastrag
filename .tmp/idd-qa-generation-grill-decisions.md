# 问答对提炼（QA Generation）— Grill 裁决记录

> 2026-09-26，idd-grill 会话产物；供 /idd-spec 起草 spec 使用。对比调研对象：WeKnora（腾讯开源 RAG）。
> 决议状态标记：〔已决〕/〔待决〕。本文件随批次推进持续追加。

## 背景结论（调研，非决议）

- **WeKnora 部分有**：Doc2Query 式「AI 问题生成」——逐 chunk 生成 3-10 条问题（默认 3），只问题无答案，问题作独立向量索引项回链 chunk；chunk 页可单条编辑/删除/重新生成；知识库级开关控制。FAQ 问答对库为人工录入 + Excel 导入。完整 QA 对生成仅存在于离线评测脚本 `dataset/qa_dataset.py`（产品管线外）。
- **FastRAG 地基已备**：`kb_qa_pair`（question/answer/fileId/source(manual/ai)/status(draft/confirmed)）+ qa 召回通道（权重 0.3，Q/A 拼接文本，`RetrievalServiceImpl.java:798-839`）；`kb_file.processing_mode=qa` 枚举预留、消费端无实现（`docs/spec/qa-pairs.md` REQ-E03 GAP，本次立项即兑现）。可复用先例：ExtractionUnitAssembler（图谱 500字→2000字符重组）、IngestionConsumer 解析后自动触发 graph-build（`IngestionConsumer.java:495-510`）、BenchmarkGenerationHelper LLM 生成完整 QA + BenchmarkResponseParser 多级防御解析、导入问题归一化去重（REQ-C05）。

## 第一批：产品定位与提取粒度（2026-09-26，用户裁决：「用你推荐」）〔已决〕

- **D1 生成物定位 = 完整问答对（问题+答案）**。
  依据：`kb_qa_pair` 已有 answer 列；qa 通道消费 Q/A 拼接文本（`RetrievalServiceImpl.java:820-833`），只生成问题则答案恒空、通道价值归零；WeKnora 只生成问题与其「问题命中→回链 chunk」架构配套，与本项目「QA 对直接作为内容返回」架构不匹配；基准集已有完整 QA 生成先例（BenchmarkGenerationHelper）。
- **D2 提取粒度 = 按父分片提取**，每条 QA 标注来源 fileId。
  依据：父分片按标题层级聚合、逻辑完整，长度适合单次 LLM 调用；天然并行、可断点续跑；答案约束「仅从本分片内容提炼」获得可溯源性与准确性下限。
  拒绝：整篇文档 Map-Reduce（成本翻倍、跨分片汇总幻觉无法闸门拦截）；子分片（~500 字过碎、上下文不足，图谱侧为补救才做了重组装配，父分片本身逻辑完整无需重组层）。
- **D3 触发方式 = 分层组合**：
  ① 文件详情页手动「生成问答对」按钮（含多选批量）为第一优先；
  ② `processing_mode=qa` 的文件在摄入完成后自动触发生成（兑现既有枚举语义，挂钩复用 IngestionConsumer→graph-build 的消息模式）；
  ③ 知识库级自动开关暂不做（避免全库 LLM 烧钱，待真实需求再立项）。

## 第二批：性能与异步机制（2026-09-26，用户裁决：「用你的推荐」）〔已决〕

- **D4 每父分片条数与自定义指令**：默认 3、范围 1-10，手动触发时请求级传参（不做知识库级全局配置）；支持可选自定义指令拼入 prompt。
- **D5 异步载体与并发**：新增 `fastrag.qa-generation.queue`（routing key `qa-generation`，复用 DIRECT_EXCHANGE，MessagePublisher 增 publish 方法），与 graph-build 完全同构（持久化队列 + 即时 ack + KB 级互斥，`GraphBuildConsumer.java:184`）；MQ 消费并发 3（与图谱共用 LLM 网关上游，防挤兑限流）；消息粒度 = 文件级任务；同文件任务进行中重复触发拒绝。
- **D6 重跑语义与失败恢复**：手动触发带 replace（默认）/ append 两模式；replace 仅删该文件 source=ai 旧对（不碰 manual 对，延续「重切保留用户手工数据」原则）；单父分片失败重试 1 次后跳过并记账到结果统计（对齐 QaImportResult 逐行明细风格）；任务结束返回成功/失败分片统计；不做断点续跑状态机。
- **D7 衍生缝隙**：`processing_mode=qa` 文件是摄入后自动触发（D3②），无用户交互时机——其条数/自定义指令取值来源。
  〔已决 2026-09-26，用户「ok 用你推荐」〕：上传对话框选中 qa 模式时展开参数区块（条数+自定义指令；replace/append 对首次生成无意义不出现），参数随上传 emit 上抛、**按文件级存储**（列设计批次4裁决）；qa 模式即用户显式逐文件 opt-in，不做 KB 级缺省配置卡。

## 配置入口（前端放置）（2026-09-26，用户裁决：「ok 用你推荐」）〔已决〕

前端证据：`FileUploader.vue:918-926` qa 模式 radio 已预留（disabled+tooltip「开发中」）；`FileUploader.vue:639-649` 上传 emit 已携带 processingMode；批量操作+参数对话框先例 = 批量打标（`FileManager.vue:538-573,807-823`）；带参对话框组件模式先例 = `StrategyChangeDialog`（v-model+kbId+file+@submitted）；KB 详情 qa tab = `QaPanel.vue`（:44 SOURCE_LABELS 仅映射 manual，:128-137 header-actions）。

| # | 配置项/入口 | 放置位置 | 形式 |
|---|---|---|---|
| P1 | 单文件触发+参数 | `chunks.vue:932-936` 页头「重新分片」旁加「生成问答对」 | 新组件 `QaGenerateDialog.vue`（仿 StrategyChangeDialog：v-model+kbId+file+@submitted）；控件=条数（1-10 缺省3）/自定义指令 textarea/replace·append（缺省 replace） |
| P2 | 列表单文件快捷入口 | `FileTable.vue:443-497` 行内 dropdown 仿「AI分片」加菜单项 | 打开同一 QaGenerateDialog |
| P3 | 多选批量触发 | `FileManager.vue:656-663` 批量操作栏加「生成问答对」（仿批量打标） | 复用同一 QaGenerateDialog，参数应用于全部选中文件 |
| P4 | qa 模式自动触发参数 | `FileUploader.vue:918-926` 解除 qa disabled，选中时展开参数区块（条数/自定义指令，无 replace·append） | 参数随上传 emit 上抛，文件级存储（D7） |
| P5 | 生成结果管理配套 | `QaPanel.vue:44` SOURCE_LABELS 补 `ai` 映射（来源筛选控件已有） | 小改 |
| P6 | 明确不做 | KB 级设置卡片（GraphSettingsPopup 模式） | D3③ 已裁不做库级开关；qa 模式为逐文件 opt-in，per-file 参数足够 |
## 第三批：准确性与审核（2026-09-26，用户裁决：「ok」= 用推荐）〔已决〕

- **D8 生成约束（防幻觉根）**：prompt 三约束——问题自包含模拟真实用户问法；答案仅从本父分片内容提炼、分片无可答题材料时出零条；输出 JSON 数组 `[{question, answer}]`。解析复用 BenchmarkResponseParser 多级防御（剥围栏/截区间/损坏降级）。
- **D9 质量闸门（零额外 LLM 成本）**：①归一化去重（复用 REQ-C05 归一化函数）：文件内 + 与库内既有 QA 对撞题跳过；②长度护栏（问题 <5 字或答案 <2 字丢弃）。不做 LLM 自检（成本翻倍，人工确认已兜底）。
- **D10 召回准入（修订 REQ-D04 口径）**：召回准入规则改为 **`source='manual' OR status='confirmed'`**——manual 对维持现状（创建即隐含信任，REQ-D04 人工语义不破），**AI 对必须经人工确认（复用现有 confirm 单向转移 REQ-B01）才进入召回**。拒绝：全表 confirmed-only（无必要改变现有人工行为）；维持现状（未审核 AI 内容直达检索，审核形同虚设）。〔spec 起草时须显式记录此修订；术语已入 CONTEXT.md「召回准入」〕
- **D11 生成模型**：请求级可选——QaGenerateDialog 与上传 qa 参数区块各放可选模型下拉（model 表列表），留空走 default 网关；qa 模式随文件级参数存储、手动触发随请求传参。与基准生成完全同构（BenchmarkGenerationHelper.java:83-84 请求级+缺省 default、:187-211 model 表自定义路由失败回退）。

## 第四批：数据模型与生命周期（2026-09-26，用户裁决：「继续」= 用推荐）〔已决〕

- **D12 溯源粒度与重切悬空**：`kb_qa_pair` + `source_chunk_id`（nullable，空值兜底文件级溯源）；QaPanel/审核界面提供「查看源分片」跳转；**重切与手动删分片均不级联删 QA 对**（延续 REQ-E04 保留口径——AI 对重生成烧 LLM 钱，不能悄悄清用户资产），悬空引用在展示层降级为仅文件级溯源（跳转按钮隐藏）。拒绝：source_chunk_title 快照列（冗余，悬空场景文件级展示已够）。
- **D13 任务状态与进度**：新表 `kb_qa_generation_task`——`file_id` 主键（upsert，每文件只留最近一次任务，历史靠 @Loggable 审计日志），字段仿 kb_graph_index（`schema.sql:219-233`）：status（idle/running/success/partial/failed，partial 对应 D6 部分分片失败跳过）/total/succeeded/failed_chunks/config 快照 JSON/error/时间戳；前端触发后轮询 `GET /qa-pairs/generation/status?fileId=`，不做 SSE（后台批量任务≠AI分片交互式预览）；该行同时承载 D5「同文件进行中拒绝重复触发」的**持久化互斥**（内存互斥重启即失效）。
- **D14 列变更清单（DDL 双处同步硬约定）**：① `kb_qa_pair` + `source_chunk_id`；② `kb_file` + `qa_generation_config`（JSON：count/customInstructions/model，qa 模式文件专用，兑现 D7 文件级存储）；③ 新表 `kb_qa_generation_task`。三处全部 SchemaInitializer 幂等增补 + `init-scripts/schema.sql` 全量**双处同步**，spec 将双处 DDL 列为验收项（历史漂移教训：idx_content_fulltext 仅单侧、agent 表缺失于 schema.sql）。
- **既有级联口径直接适用（spec 引用即可，无需新裁）**：REQ-E02 文件删除级联删 QA / REQ-E01 KB 删除级联 / REQ-E05 跨库移动不随迁（qa_generation_config 随文件行走，行为自洽）/ REQ-E04 qa 模式文件禁止重切（守卫已有）。

## 会话终止（2026-09-26）

四批 14 项裁决（D1-D14）+ 配置入口 P1-P6 **全部〔已决〕**，决策树走完，无遗留分支。
术语产出：CONTEXT.md 新增「问答对提炼（QA Generation）」「召回准入（Recall Admission）」。
下一步：`/idd-spec` 据本文件起草正式需求规格，两处必须显式记录——① REQ-D04 修订（召回准入改为 `source='manual' OR status='confirmed'`）；② REQ-E03 GAP 清偿（qa 提取流水线由"不实现"转为本特性落地）。
