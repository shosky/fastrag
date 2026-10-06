# 问答对提炼（qa-generation）— Implementation Document

> 2026-09-26 实现。链文档：spec（15 REQ）/ design（ADR-0004/0005）/ contract（interfaces+data `## qa-generation`）/ tdd（7 切片 38 用例）。
> 测试证据：新增 4 测试类 38 用例全绿；knowledge + retrieval + bootstrap 全量回归 65 测试类零失败。

## 1. Code File Map

```
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── controller/QaGenerationController.java   — POST/GET /{fileId} 两端点（@KbAuth editor/viewer + @Loggable）
├── service/QaGenerationService.java         — 契约接口 trigger/status/execute
├── service/impl/QaGenerationServiceImpl.java— 编排深模块：互斥/流水线/闸门/收尾事务/模型路由
├── consumer/QaGenerationConsumer.java       — MQ 消费（即时 ack）+ qa-generation-worker + 进程内互斥 + 陈旧清扫
├── consumer/IngestionConsumer.java          — +triggerQaGeneration（qa 模式自动触发挂钩，包私有测试缝）
├── entity/KbQaGenerationTask.java           — 任务账本实体（file_id 主键 upsert）
├── entity/KbQaPair.java                     — +sourceChunkId
├── entity/KbFile.java                       — +qaGenerationConfig
├── mapper/KbQaGenerationTaskMapper.java     — BaseMapper 透传
├── model/QaGenerationConfig.java            — 参数（normalized() 校验 + hutool JSON 互转）
├── model/QaGenerationTaskView.java          — 任务视图（entity → view 投影）
├── model/FileProcessRequest.java            — +qaGenerationConfig（上传写入侧）
└── service/impl/FileServiceImpl.java        — +任务行删除级联；process() 落 qa 参数列
    service/impl/KbServiceImpl.java          — +任务行删除级联（KB 删除流）
tests（同镜像包）:
├── service/impl/QaGenerationServiceImplTest.java — 27 用例（S1 受理/S2 编排/S3 闸门/S5 行态门+心跳）
├── consumer/QaGenerationConsumerTest.java        — 5 用例（重复消息/参数透传/陈旧清扫双例/丢弃）
├── consumer/IngestionQaHookTest.java             — 3 用例（挂钩三例）
fastrag-modules/fastrag-retrieval/
├── .../service/impl/RetrievalServiceImpl.java    — qaChannelRecall +准入条件（source='manual' OR status='confirmed'）
└── src/test/.../QaRecallAdmissionTest.java       — 3 用例（wrapper SQL 段/参数/confirmed 冒烟）
fastrag-infra/.../rabbitmq/
├── RabbitMQConfig.java   — +fastrag.qa-generation.queue（持久化）+ qa-generation 绑定
└── MessagePublisher.java — +publishQaGeneration（无同步降级，失败即抛）
fastrag-common/.../enums/ActionType.java   — +qa_generation_triggered
fastrag-bootstrap/.../SchemaInitializer.java — +kb_qa_generation_task 建表 + 两列幂等增补
init-scripts/schema.sql                    — +kb_qa_generation_task CREATE + 两列（与上者双处同步）
```

## 2. Interface Implementations

### QaGenerationService.trigger (from contract: qa-generation.QaGenerationService)

**Logic Flow:**
```
1. config = (raw ?? empty).normalized()          — count 缺省 3、越界/指令超长/mode 非法/model 超长 → badRequest（E_QA_GEN_PARAM_INVALID）
2. file = fileMapper.selectById(fileId)
   IF file == null || file.kbId != kbId THEN throw badRequest（E_QA_GEN_FILE_INVALID）
3. parents = 父分片查询（kbId+fileId+chunkType='parent'，按 chunkIndex 排序）
   IF parents 空 AND file.status != 'completed' THEN throw badRequest（E_QA_GEN_NOT_READY）
   ELSE IF parents 空（已完成空文档）THEN 放行，totalChunks=0
4. existing = taskMapper.selectById(fileId)
   IF existing.status == 'running'：
     IF 心跳未超 qa.generation.stale-minutes THEN throw badRequest（E_QA_GEN_TASK_RUNNING，不触碰原行）
     ELSE existing → failed「执行中断（超时无心跳，已自动恢复）」并 updateById（自愈后放行）
5. 任务行 upsert：insert（无旧行）或 updateById 覆盖（running、total=parents.size()、计数清零、config 快照 JSON）
6. TRY publishQaGeneration({kbId, fileId, config})
   CATCH ANY → 任务行落 failed（error=队列不可用…）→ updateById → throw badRequest（E_QA_GEN_QUEUE_UNAVAILABLE）
   （无同步降级——design 缝 2 偏差）
7. RETURN view(task)
```

**Module Interactions:**
- **Calls:** KbFileMapper / KbChunkMapper / KbQaGenerationTaskMapper — 读文件与父分片、任务落账
- **Calls:** MessagePublisher.publishQaGeneration（经 ObjectProvider 延迟解析，断 MessagePublisher→IngestionHandler 构造环）— 投递
- **Called by:** QaGenerationController（HTTP 受理）、IngestionConsumer.triggerQaGeneration（qa 模式自动触发）
- **Shared state:** kb_qa_generation_task（本模块独占写；FileServiceImpl/KbServiceImpl 删除级联删）

**Decisions & Deviations:**
- 审计不在服务层：trigger 的「记录审计」不变式由控制器 @Loggable（ActionType.qa_generation_triggered）承载；摄入自动触发路径走 IngestionConsumer 既有更新日志体系。
- model 参数不做存在性校验（对齐 BenchmarkGenerator 口径），未知模型表现为分片级失败入统计。

**Parent Trace:** trigger → contract qa-generation.QaGenerationService → Design.QaGenerationModule → REQ-A01~A05、NFR 异步持久化。

### QaGenerationService.status (from contract: 同上)

**Logic Flow:**
```
1. task = taskMapper.selectById(fileId)
2. IF null THEN RETURN null（从未触发）
3. RETURN view（四态 + 三计数 + config 反序列化 + error + 时间戳）
```
**Module Interactions:** 仅任务账本表，纯读零副作用。
**Decisions & Deviations:** 无。
**Parent Trace:** status → 同上 → REQ-D01/D02。

### QaGenerationService.execute (from contract: 同上)

**Logic Flow:**
```
1. 行态门：task = selectById(fileId)
   IF task == null || task.status != 'running' THEN log.warn + RETURN（MQ 重投双跑防线，静默丢弃）
2. config.normalized()；parents = 父分片查询；task.totalChunks = parents.size()
3. 判重基线：existing = 按 kbId 全量 QA 对
   FOR EACH p：IF replace AND p.fileId==fileId AND p.source=='ai' THEN 跳过（旧对收尾即删，不入基线）
   ELSE seen.add(normalizeQuestion(p.question))（与 Excel 导入同一归一化函数）
4. FOR EACH chunk IN parents：
   a. prompt = 三约束 + customInstruction + 片段全文（含所属标题）
   b. 双轮尝试：response = callLlm(model, prompt)；parsed = BenchmarkResponseParser.parseQuestions(response)
      两轮均异常 → failed += 1，进入下一分片（REQ-B03/B04）
   c. FOR EACH 产出项：question/answer 取值 trim
      IF question 长度 <5 OR answer 长度 <2 THEN rejected++（护栏）
      ELIF normalizeQuestion(question) 已在 seen THEN rejected++（判重）
      ELSE 缓冲 KbQaPair{source=ai, status=draft, sourceChunkId=chunk.id, 雪花 id}；seen.add
   d. 记账：有产出被接受 → succeeded+=1 且 failed+=rejected；LLM 合法空数组 → succeeded+=1（REQ-B02 零产出正常完结）；
      有解析非空但全被拒 → failed+=rejected
   e. 心跳回写：task.succeeded/failed/updatedAt → updateById
5. 收尾单事务（TransactionTemplate）：
   IF replace THEN DELETE kb_qa_pair WHERE kbId+fileId+source='ai'（删旧在收尾——中途崩溃旧数据无损）
   FOR EACH buffer.insert
   CATCH ANY → 任务行 failed「落库失败」→ updateById → RETURN
6. 四态判定：failed>0 →（succeeded>0 ? partial : failed）；否则 success → updateById 终态
```

**Module Interactions:**
- **Calls:** KbChunkMapper / KbQaPairMapper / KbQaGenerationTaskMapper — 分片读、产出落库、任务回写
- **Calls:** LlmService.chatWithTimeout + JdbcTemplate（model 表 api_url/api_key_ref 路由，失败回退默认网关）— LLM 网关（缝 1）
- **Calls:** BenchmarkResponseParser（graph-eval util，依赖已在 pom:19）、ExcelQaPairImporter.normalizeQuestion — 复用件直用
- **Calls:** TransactionTemplate — 收尾原子性
- **Called by:** QaGenerationConsumer（MQ 调度，进程内互斥由其持有）

**Decisions & Deviations:**
- **failed_chunks 语义 = 失败分片数 + 闸门拦截产出数（合并记账）**——契约与 tdd 文本均要求拦截计入该列；合计数可超过 total_chunks，为有意行为（实体注释已声明）。
- 四态判定实现期由测试发现缺陷修正：初版「failed>0 即 partial」会把全败标成 partial，已改为 succeeded/failed 双因子判定（红绿循环真 RED 捕获）。
- 行态门防重投与进程内互斥双保险分别位于服务/消费者两侧，均落地。

**Parent Trace:** execute → contract 同上 → Design.QaGenerationModule + QaGenerationConsumerModule → REQ-B01~B05、D01/D03、A04。

### 消费入口 handleQaGeneration（contract: 消息结构见 data.md `## qa-generation`）

**Logic Flow:**
```
1. fileId 缺失 → warn + 丢弃
2. sweepStaleTasks：running 且心跳超阈值的行 → failed「执行中断」（持久化互斥自愈）
3. IF !runningFiles.add(fileId) THEN warn + RETURN（进程内互斥）
4. executor(qa-generation-worker).submit：
   TRY execute(kbId, fileId, config)
   CATCH ANY → 任务行兜底落 failed（不向 MQ 抛异常——消息已即时 ack）
   FINALLY runningFiles.remove(fileId)
   提交被拒（RejectedExecutionException）→ 移除互斥 + 兜底 failed
```
**Module Interactions:** @RabbitListener(fastrag.qa-generation.queue, concurrency=3)；config Map 逐字段容错提取。
**Decisions & Deviations:** 无。
**Parent Trace:** → Design.QaGenerationConsumerModule → REQ-A04、NFR 异步持久化/吞吐。

### 变更点：检索准入过滤 / 摄入挂钩 / 上传落列 / 任务行级联

- **RetrievalServiceImpl.qaChannelRecall**：kbId 条件后增加 `.and(source='manual' OR status='confirmed')`；通道其余语义零变更。→ REQ-C02 / ADR-0005。
- **IngestionConsumer**：图谱触发块之后 `processingMode=='qa'`（消息或文件列任一为 qa）→ triggerQaGeneration（解析文件列 config + mode 恒 replace → trigger）；TRY/CATCH 包裹不阻断摄入主流程。→ REQ-A03 / REQ-E03 清偿。
- **FileServiceImpl.process**：qa 模式且请求携带 qaGenerationConfig 时 JSON 落 kb_file.qa_generation_config。→ REQ-A03 上传写入侧。
- **FileServiceImpl.cleanupFileData / KbServiceImpl 删除流**：任务行与 QA 对同批级联物理删除（失败仅告警，沿既有级联容错形态）。→ data.md `## qa-generation` Lifecycle / REQ-E02。

## 3. Decisions & Deviations（汇总）

1. **测试后置于实现脚手架**（构建成本权衡，非教科书 TDD 顺序）；红绿循环以真实形式达成——32 用例首轮 3 红：2 处测试数据缺陷（全角用例漏空格差异；`{broken json` 无 `[` 被防御解析按空数组放行）+ 1 处**实现缺陷**（全败四态误判 partial），修复后全绿。
2. **mock mapper 替代「MySQL 真库」**（tdd 计划口径）：knowledge 模块无真库测试基建（无 @SpringBootTest/Testcontainers，既有 63 测试类全为 Mockito），沿 RetrievalServiceImplTest 可执行先例；事务原子性以回调编排+异常传播断言，真事务语义列 DB 验收。
3. **S4 三分支行为级验证降级**：mock 不执行 SQL，准入规则以 wrapper SQL 段（含 source/status）+ 参数（含 manual/confirmed）断言替代；confirmed 对经通道返回的投影冒烟已覆盖；行级三分支真库验证列验收清单。
4. **S6 级联 4 例未写单测**：级联为与紧邻既有 QA 清理同构的 6 行代码，宿主服务构造依赖 15+ 无测试基建；列真库验收清单。挂钩 3 例已落地（包私有测试缝）。
5. **failed_chunks 合并记账**（失败分片 + 拦截产出）——遵契约/tdd 文本，合计可超 total_chunks，有意行为。
6. **端点形状微调**：状态查询用 `GET /{fileId}` 路径参数（contract data.md 行文的 query 参数形式），RESTful 惯例，前端契约无影响。
7. **上传写入侧扩展**：FileProcessRequest +qaGenerationConfig、process() 落列——REQ-A03 必需，侵入 file-ingestion 链 1 字段 1 行，已记录。

## 4. Testing

- 新增测试 38 例全绿：QaGenerationServiceImplTest 27（S1 受理 9 / S2 编排 8 / S3 闸门 5 / S5 服务面 5）、QaGenerationConsumerTest 5、IngestionQaHookTest 3、QaRecallAdmissionTest 3。
- 回归：fastrag-knowledge + fastrag-retrieval + fastrag-bootstrap 65 测试类零失败（既有 RetrievalServiceImplTest 的 qa 用例经 mock 边界天然不受 wrapper 变更影响）。
- Tracer Bullet（受理→产出→准入纵切）三例位于上述类中并先行通过。
- **待 DB 环境验收清单**（单测 mock 不可达）：①S2 收尾事务真回滚；②S4 三分支行级召回；③S6 重切保留/悬空降级/删文件/删库级联；④DDL 双处同步在真实库的结构对账；⑤陈旧清扫 30 分钟真实时钟。

## 5. 前端交付（2026-09-27，配置入口 P1-P6 全量落地）

| 入口 | 文件 | 内容 |
|---|---|---|
| API 层 | src/api/index.ts | +triggerQaGeneration / getQaGenerationStatus（POST/GET `/kb/{kbId}/qa-pairs/generation/{fileId}`） |
| 参数对话框（共用） | src/views/knowledge/detail/components/QaGenerateDialog.vue（新建） | 条数 1-10 缺省 3 / 自定义指令 ≤500 / replace·append / 模型下拉（getModels，可清空走默认网关）；触发后 3s 轮询任务进度（关弹窗停轮询，后台任务继续）；提示「草稿需在问答对页确认后参与检索」 |
| 入口①单文件（列表） | FileTable.vue + FileManager.vue | 行内「⋯」菜单加「生成问答对」（仅 completed 可点，emit generateQa） |
| 入口②多选批量 | FileManager.vue | 批量操作栏「生成问答对」（过滤 completed，全未完成则警告） |
| 入口③文件详情页 | chunks.vue | 页头「重新分片」旁「生成问答对」按钮；@submitted 联动 loadQaPairs 刷新问答对视图 |
| 入口④上传自动提炼 | FileUploader.vue | 「问答对提取（AI 提炼）」radio 解禁（原 disabled+开发中 tooltip）；qa 参数区块（条数/自定义指令/模型）随 UploadConfig 上抛 → FileManager.handleUpload 转入 process 请求（qaGenerationConfig） |
| 配套 P5 | QaPanel.vue | SOURCE_LABELS + 来源筛选补「AI 提炼」 |

类型检查：vue-tsc 全量比对，**本次触碰的 7 个文件零新增错误**（全仓 159 个既有错误集中于 src/mock 与 admin/application 视图，均为历史债务；QaPanel 3 处经 git diff 证实为改动前已存在）。前端联调（真后端跑通触发→轮询→确认→召回）待后端启动后进行。

## 6. 真库自测（2026-09-27，真实 MySQL/RabbitMQ/Redis/Neo4j + 本机假 OpenAI 协议 LLM）

环境：用户 IDE 后端（旧代码，8081）保持运行不受干扰；本特性新代码以 jar 起于 8083（共享同一 fastrag 库与 MQ），假 LLM 服务器（11434，逐请求递增唯一编号的合法问答 JSON）冒充 ai.gateway。脚本与日志存 `fastrag/.tmp/`（fake-llm.mjs / selftest-flow.mjs / backend-selftest.log）。

| 验收项 | 结果 |
|---|---|
| ④ DDL 双处同步真实库 | ✅ `Table kb_qa_generation_task OK` + `kb_qa_pair.source_chunk_id` / `kb_file.qa_generation_config` 就绪（SchemaInitializer 日志） |
| 端到端主链 | ✅ 触发（95 父分片受理，status=running）→ MQ 消费 → 95 次 LLM 调用 → 任务终态 success 95/95/0（约 3 秒） |
| 落库正确性 | ✅ 285 条（95×3）source=ai / status=draft / sourceChunkId 逐条对应 / 雪花 id |
| ② S4 召回准入（真实库行级） | ✅ 确认前检索「自测问题82」qa 通道命中 **0** 条（ai draft 不召回）；confirm 后同词检索命中 **1** 条（source=qa） |
| 兼容路径 | ✅ 无父分片的老文件触发 → 合法零产出 success（REQ-B02/E_QA_GEN_NOT_READY 分支均验证） |
| ① S2 事务真回滚 / ③ S6 级联 / ⑤ 真实时钟清扫 | 未测（需注入失败/删真实文件/等待时钟），保留为后续验收项 |

残留清理：285 条自测问答对 + 9 行任务账本已删除（仅 source='ai' 与任务行，manual 数据未触碰），复验 remaining=0。
联调提示：**IDE 中运行的旧后端需重启一次**才会加载本特性代码与新 DDL 语义；假 LLM 与 8083 实例已关闭。

## 7. 行为修订（2026-09-27，用户质疑驱动，三处已实现）

1. **提取单位回退**（原 REQ-B01）：文件无父分片（旧管道产物）时回退全部文本分片（排除图片占位）——存量 120+ 老文件从「零产出静默成功」变为可正常提炼；新增用例 `执行_存量无父分片文件时_回退文本分片提取`。
2. **逐片实时落库**（原 REQ-A05/D6 修订）：废弃「内存缓冲 + 收尾单事务」，每分片提炼完成即落库（问答对页实时可见草稿）；replace 的删旧提前至任务开始。代价=中途崩溃残留部分新对（重跑 replace 自愈），收益=长文件产出不再全有或全无。ai 草稿不参与召回（确认闸门）因此中途可见不污染检索。
3. **文档出处补全**：QA 面板新增「来源文档」列（fileName，后端落库自 kb_file.name）；检索 qa 通道命中本就携带 fileId 进对话引用链路。
4. 可观测性：任务终态必打一行 `[QaGeneration] task finished` 日志（含状态/分片/产出统计）；`upsertUnitRow` 撞 `uk_unit` 降级更新（图谱链缺陷，见 feature-map #10 登记）。
5. 测试：35/35 绿（服务 27 含新增回退用例；替换「收尾事务失败」用例为「删旧先于产出」顺序断言）。
