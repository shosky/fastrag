# Feature Map — FastRAG 后端特性地图（fastrag-backend）

> 由 idd-onboard 于 2026-09-21 初始化（范围 = `fastrag-backend/` 目录，Role: backend）。
> 证据来源：同日对当前 HEAD（d8f30cd）的两路实测调研（控制器全量计数 / 逐模块 LOC / 4ed27bb→HEAD 漂移归因 / git 60 天热度），特性聚类沿用 2026-09-16 全仓接入时经确认的 18 项决议（2026-09-21 刷新复核维持成立）。
> Chain status 取值：`shape → spec → design → contract → tdd → implement`，暂停时标 `blocked`。
> 证据分层：CERTAIN = 代码直接可观测；INFERRED = 由结构强推断，需阶段末向用户确认；GAP = 代码外信息，不臆造。
> 上位地图：~~[../../docs/feature-map.md](../../docs/feature-map.md)~~（⚠ 根 `docs/` 树已于 2026-09-21 被并行活动删除——legacy 99 篇、上位 feature-map/README/conventions、iam-frontend.md、根会话归档随之丢失，回收站无记录，恢复线索仅 IDEA Local History 或外部备份；链内 ADR/上位文档一律只引要点编号）。
>
> **链产出（2026-09-21 起，idd-onboard 批量链构建，2026-09-24 完成）：** **全部 18 项特性**五阶链文档产出完毕，落本目录 `{spec,design,contract,tdd,implement}/` + 契约共享文件（`contract/interfaces.md` 1709 行 18 章 / `contract/data.md` 1451 行 18 章）；tdd 均为测试设计，**红绿循环未实施**（执行待用户指令）。链构建过程核实登记代码缺陷/债务 **40+ 项**（含多项「必失败 DDL」「零消费死表」「占位引擎」级发现），并勘误地图多处记载（详见各特性条目 ⚠ 标注与 implement 文档 Deviations、会话归档）。

## 模块地图（实测，~68.9k LOC / 15 Maven 模块 / 55 控制器）

```
fastrag-backend
├── fastrag-common      公共：ApiResponse/PageResult、BusinessException、GlobalExceptionHandler、@Loggable（1.1k）
├── fastrag-security    JwtAuthFilter、ApiTokenAuthFilter、@KbAuth KB 级 ACL 切面、KbAccessChecker、OnlyOfficeJwtUtil（0.9k）
├── fastrag-infra       Milvus / MinIO / Neo4j(GraphStore 唯一实现，启动 fail-fast) / RabbitMQ / 邮件（2.5k；Redis 无封装，各模块直连）
├── fastrag-ai          AI 能力层：LLM / Embedding / Rerank / ASR / OCR / Layout（Ollama 兼容网关 WebClient）（2.4k）
├── fastrag-modules/
│   ├── fastrag-iam         2.6k  认证/用户/角色/权限/组织/API Token（7 控制器）
│   ├── fastrag-knowledge   23.6k 知识库核心：文件/解析/分片/AI分片/OnlyOffice/问答对/发布管理（14 控制器，最大）
│   ├── fastrag-retrieval   3.5k  检索 + 查询增强 + 检索日志 + 更新提醒（1 控制器）
│   ├── fastrag-graph-eval  5.0k  图谱 + 基准 + 评测（3 控制器）
│   ├── fastrag-publish     1.0k  版本流转 + 审核工作流（2 控制器）
│   ├── fastrag-application 3.4k  应用/应用配置/对话/工作流（4 控制器）
│   ├── fastrag-agent       8.8k  智能体运行时：Engine+Middleware+State+Docker 沙箱（3 控制器）
│   ├── fastrag-tools       8.9k  工具 / 技能(文件化) / MCP / 外部数据库（4 控制器）
│   ├── fastrag-platform    2.8k  模型管理/系统配置/字典/敏感词/术语/查询规则/上传（8 控制器，含 UploadController）
│   └── fastrag-operation   2.1k  运营分析/审计/反馈/数据挖掘（9 控制器）
└── fastrag-bootstrap   启动 + SchemaInitializer（幂等增量 DDL，替代 Flyway）（0.5k）
```

---

# 特性清单（20 项 = 2026-09-16 决议 18 项 + 2026-09-25 新增 #19 new-conversation + 2026-09-26 新增 #20 qa-generation）

## 1. IAM 认证与账号权限 — iam

- **代码证据：** `fastrag-modules/fastrag-iam`（7 控制器：Auth、ApiToken、KbAcl、Org、Permission、Personnel、Role，端点 ~40）+ `fastrag-security`（JwtAuthFilter、ApiTokenAuthFilter、KbAuthAspect、KbAccessChecker、OnlyOfficeJwtUtil）；表 sys_user/sys_role/sys_permission/sys_org/sys_api_token/kb_acl 等 13+
- **可能 REQ 区域：** 密码+验证码+微信扫码登录、注册/重置密码、RBAC（角色-权限-菜单/API 三层，权限页勾选驱动）、组织树、人员管理、KB 级 ACL（owner>editor>viewer）、API Token
- **Depends on:** 无（全系统前提）
- **Shared by:** 全部其余 17 项特性（JwtAuthFilter + @KbAuth 两层消费）
- **Depth signal:** 2.6k LOC + security 0.9k；60 天热度 70 次变更（第 2 位；根仓 09-16 版记 87，原口径不明 ‹GAP›）；登录方式 3 种
- **Priority:** 高（依赖根）
- **Chain status:** implement（文档链齐，2026-09-21）— spec（由上位仓搬入；上位副本已随根 docs/ 树删除丢失）/ design / contract（共享文件 `## iam` 节，6 接口）/ tdd / implement 五阶齐备；tdd 为测试设计，红绿循环未执行。前端链另起（iam-frontend.md 已丢失，待前端文档树重建）。pending：14 条 INFERRED 已全部代裁闭环（2026-09-24：R1 深证/R2 现状/R3 实证/R4 决议；搬入复核差异清单已逐条裁决，均在 `spec/iam.md`）

## 2. 知识库管理 — kb-core

- **代码证据：** KbController(`/api/kb`)、KbCategoryController、KbTagController（均在 fastrag-knowledge）；表 kb/kb_category/kb_tag/kb_tag_relation/kb_listener*；kb_* 表 38 张的主干
- **可能 REQ 区域：** 知识库 CRUD 与分类/标签、知识库列表分页、工作台聚合入口
- **Depends on:** iam（KbAcl）
- **Shared by:** 其余全部知识域特性
- **Depth signal:** knowledge 模块 23.6k 中的 CRUD 基座
- **Priority:** 高
- **Chain status:** implement（文档链齐，2026-09-21；红绿循环未执行；pending：5 INFERRED + 2 GAP 已代裁闭环（2026-09-24）；登记缺陷 3 项：usage_count 虚增 / data.sql:535 种子引用不存在列 / graph_auto_build 双源不一致）

## 3. 解析策略 — parse-strategy

- **代码证据：** ParseStrategyController(`/api/kb/{kbId}/parse-strategies` + resolve/conflicts)、ParseStrategyMetaController、ParseStrategyTemplateController；表 kb_parse_strategy/kb_compliance_rule；测试 StrategyConfigResolverTest、ParseStrategyServiceImplTest；上位文档 ADR-0001（绑定变更必须重切）
- **可能 REQ 区域：** 策略 CRUD/设默认/模板、扩展名自动匹配（Auto Match）、文档级覆盖、绑定-重切一致性（ADR-0001）、冲突检测；已知缺陷信号：字段必填性三层不一致、死配置
- **Depends on:** kb-core
- **Shared by:** file-ingestion、chunk-management、ai-chunking（advanced JSON 参数链）
- **Depth signal:** 3 控制器 + 2 篇研究文档指出契约不一致
- **Priority:** 高（摄取前置）
- **Chain status:** implement（文档链齐，2026-09-21；红绿循环未执行；pending：4 INFERRED + 5 GAP 已代裁闭环（2026-09-24）；已证实「三层必填不一致」「死配置 kb_compliance_rule」两项缺陷信号，另登记 javadoc 四层 vs 实现两层）

## 4. 文件上传与摄取流水线 — file-ingestion

- **代码证据：** FileController(`/api/kb/{kbId}/files`，**26 个端点**：上传/处理/重试/回收站/复制/移动/下载/markdown 读写/metadata/tags/preview/strategy)、UploadController(`/api/upload`，实际位于 platform 模块)、IngestionConsumer（RabbitMQ `fastrag.ingestion.queue`）、`parser/`（DocumentParserImpl、MediaExtractor/FFmpeg、8 格式解析：PDF/DOCX/PPTX/XLSX/文本/图片 OCR/音频 ASR/视频双路）、StorageServiceImpl(MinIO)；近期漂移：IngestionConsumer 接入 OcrNoiseFilter（嵌入图片 OCR 文本行级清洗）；测试 19 个（parser/ 为主）
- **可能 REQ 区域：** 两阶段上传（MinIO+pending→MQ 异步）、8 格式解析、降级策略、回收站、Markdown 持久化、OCR 噪声清洗
- **Depends on:** kb-core、parse-strategy、fastrag-ai（ASR/OCR）、infra（MinIO/MQ）
- **Shared by:** chunk-management、ai-chunking、onlyoffice-editing、knowledge-graph、retrieval
- **Depth signal:** 全仓第一热度（knowledge 模块 60 天 218 次变更）；26 端点最大控制器
- **Priority:** 高
- **Chain status:** implement（文档链齐，2026-09-21；红绿循环未执行；pending：8 INFERRED + 6 GAP 已代裁闭环（2026-09-24）；登记 enableGraphBuild javadoc 失真、kb_file 4 个无写入方字段）

## 5. 分片管理 — chunk-management

- **代码证据：** ChunkController(`/api/kb/{kbId}/chunks` CRUD+batch-delete+count)、`chunking/`（ChunkingServiceImpl、ParentChunkAssembler、TableModeIgnore）；表 kb_chunk（父子两级，含 title/heading_path 结构感知字段）；测试 ChunkingServiceImplTest、ParentChunkAssemblerTest
- **可能 REQ 区域：** 分片 CRUD/批量删除、父子分片装配（子片召回/父片上下文）、分片查看与手工编辑、重新分片（Re-chunk）
- **Depends on:** file-ingestion、parse-strategy（ADR-0001 绑定一致性）
- **Shared by:** retrieval（向量源）、knowledge-graph（抽取源，经 ExtractionUnitAssembler 重组）、ai-chunking（落库目标）
- **Depth signal:** kb_chunk 为检索最小单元；上位文档 rag-chunk-recall-enhance.md 指出截断召回问题
- **Priority:** 高
- **Chain status:** implement（文档链齐，2026-09-21；红绿循环未执行；pending：4 INFERRED + 2 GAP 已代裁闭环（2026-09-24）；新登记 DDL 双处漂移：idx_content_fulltext 仅在 SchemaInitializer、schema.sql 缺失）

## 6. OnlyOffice 在线编辑 — onlyoffice-editing

- **代码证据：** OnlyOfficeController(`/api/kb/{kbId}/files/{id}/onlyoffice/` config/forcesave/raw/callback)、OnlyOfficeService(+Impl)、fastrag-security 侧 OnlyOfficeJwtUtil（callback 免鉴权面）；docker-compose `onlyoffice-docserver:8.3.3`（:8082）
- **可能 REQ 区域：** 文档在线渲染/编辑、强制保存回调→重分片、JWT 回调鉴权、OO 插件选区桥（插件源码在前端仓 `public/onlyoffice-plugin/`）
- **Depends on:** file-ingestion（MinIO 原文件）、iam（回调 JWT）
- **Shared by:** ai-chunking（选区桥是其分片输入手段）
- **Depth signal:** 独立外部集成（Document Server）+ 安全敏感（callback 免鉴权面）
- **Priority:** 中高
- **Chain status:** implement（文档链齐，2026-09-21；红绿循环未执行；pending：5 INFERRED + 7 GAP 已代裁闭环（2026-09-24）；登记安全债务 5 项：callback key=null 容忍通过、raw 不校验 url key、角色解析近似、下载 URL 无 SSRF 白名单、jwt-enabled 开发态 false）

## 7. AI 分片 — ai-chunking

- **代码证据：** AiChunkController（`/api/kb/{kbId}/files/{id}/ai-chunk/` stream(SSE)/layout(SSE)/ai-chunk-preview/ai-chunk-apply/image-ocr）、AiChunkServiceImpl；近期漂移：PDF 段落装配改进（PdfLayoutAnalyzer +49 / DocumentParserImpl +40，新增 PdfParagraphAssemblyTest +88）；上位文档 design/ai-chunk-preview-semantic.md、pdf-overlay-box-accuracy.md 等
- **可能 REQ 区域：** OCR 解析→结构级跨页合并→段落对齐模型→LLM 自动/手动分片→应用落库；区域框选（跨页拖拽/自动翻页/扫描件 OCR/图片直达分片）；以用户确认边界为唯一事实源替换落库
- **Depends on:** file-ingestion、chunk-management（替换目标）、onlyoffice-editing（INFERRED：office 文档选区桥）、fastrag-ai（OCR/LLM）
- **Shared by:** 无下游（末端加工流）
- **Depth signal:** **全仓最热单一目录**（前端 detail/components 60 天 45 次变更）；最近有效 commit 均为此特性
- **Priority:** 高（最高热度）
- **Chain status:** implement（文档链齐，2026-09-21；红绿循环未执行；pending：6 INFERRED + 2 GAP 已代裁闭环（2026-09-24）；一处旧设计与实现冲突已按代码仲裁——表格原子不可切）

## 8. 问答对管理 — qa-pairs

- **代码证据：** QaPairController(`/api/kb/{kbId}/qa-pairs` CRUD+confirm+import)、QaImportController（模板下载）；表 kb_qa_pair
- **可能 REQ 区域：** QA 对 CRUD、Excel 批量导入、确认生效
- **Depends on:** kb-core
- **Shared by:** retrieval（INFERRED：QA 对是否参与召回未见独立端点，需确认）
- **Depth signal:** 小（2 控制器）
- **Priority:** 低
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：4 INFERRED + 3 GAP；裁决历史疑问——QA 对参与召回（检索链 qa 通道加权 0.3）；登记 6 债务含孤儿 QA 对缺陷）

## 9. 检索与查询增强 — retrieval

- **代码证据：** RetrievalController（15 端点：search/kb chunks count/query suggest/expand-synonyms/query-rules apply/graph expand/logs CRUD/update-remind 全套）、RetrievalServiceImpl（Milvus+MySQL LIKE 混合+Rerank；graph 通道降级容错 `graphHits` 空回退 + `graphChannelDegraded` 记账）、QueryEnhanceServiceImpl；测试 RetrievalServiceImplTest、QueryEnhanceServiceImplTest；已知 stub 缺陷：查询改写执行引擎（上位文档 research/query-rewrite-analysis.md 指出字段契约断裂）
- **可能 REQ 区域：** 混合检索（向量+关键词+重排）、查询改写/同义扩展（仅消费启用词条）/规则应用、检索日志与分析、更新提醒
- **Depends on:** chunk-management（向量）、fastrag-ai（Embedding/Rerank）、platform-config（query_rule 表、term 启用状态）
- **Shared by:** app-center（对话检索，真实注入消费方）、knowledge-graph（expand）；~~benchmark-evaluation~~（⚠ 2026-09-24 裁决勘误：评测自带精简检索管线直连 Embedding/Milvus/Rerank，不经本特性——见 #11）
- **Depth signal:** 3.5k LOC；上位质量提升系列文档 5 篇聚焦于此
- **Priority:** 高
- **Chain status:** implement（文档链齐，2026-09-21；红绿循环未执行；pending：8 INFERRED + 7 GAP 已代裁闭环（2026-09-24）；stub 债务代码实证 8 项：query_rule 零消费、LLM 重排=topK 截断、suggest 占位、更新提醒无调度器等）

## 10. 知识图谱 — knowledge-graph

- **代码证据：** GraphController(`/api/kb/{kbId}/graph` 15 端点：data/stats/search/labels/index build+retry+replay/rank/settings/merge-candidates/merge)、GraphBuildConsumer（MQ `fastrag.graph-build.queue` 并发 5；2026-09 重写：单元化抽取/端点确定性 ID/实体向量回填）、infra GraphStore 接口 + Neo4jGraphStore（**唯一实现，ADR-0002 已落地**：MysqlGraphStore 已删，`neo4j.enabled` 开关移除，Neo4j 启动 fail-fast；接口含 mergeEntity/findMergeCandidates/孤儿清理/实体向量检索与跨分片框架关系补边）；表 kb_graph_* **4 张**（index/stitch/unit/merge_audit——后 3 张为 ADR-0002 后新增，DDL 已于 2026-09-21 补入 init-scripts/schema.sql 与 SchemaInitializer 一致；⚠ 旧记 11 张含已删除的 MySQL 图表 4 张与不存在表，链构建时实测勘误）；`chunking/ExtractionUnitAssembler`（~500 字分片→~2000 字符抽取单元）；`EntityNameGuard` 实体名清洗闸门 + `EntitySimilarity` 向量余弦合并候选；`graph_auto_build` 默认 1；测试 13+（6 个 util 测试 + GraphBuildConsumerPromptTest/UnitTest + GraphServiceImplOrphanFilterTest 等）
- **可能 REQ 区域：** 三元组抽取（单元化）→Neo4j 构建（异步 MQ）、图谱查询/扩展/PPR rank、实体合并与审计、索引重建/replay 零成本重建、mention 三表质量防线、OCR 噪声过滤入图防线
- **Depends on:** chunk-management、fastrag-ai（LLM 抽取/Embedding）、infra（Neo4j/MQ）
- **Shared by:** retrieval（graph expand）、benchmark-evaluation
- **Depth signal:** 5.0k LOC；60 天 74 次变更（第 3 位）；文档最密集的特性（上位 9 篇）；**4ed27bb→HEAD 漂移主战场**（ADR-0002 收尾 + 单元化抽取 v1.2 + 合并治理）；已知历史质量问题（59% 孤立节点）→ 治理线已落地
- **Priority:** 高
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：2 INFERRED + 4 GAP；⚠ 勘误：kb_graph_* MySQL 表实测 **4 张**（index/stitch/unit/merge_audit），本条目旧记 11 张含已删/不存在表；新登记：GraphController 15 端点无 @KbAuth、合并审计表零消费（REQ-D05 契约先行欠账）、kb_graph_index.settings 升级库漂移；⚠ 2026-09-27 新登记缺陷（已修复待随下次重启生效）：upsertUnitRow 的 INSERT 不防同槽位竞态（构建内重试复用旧 row 引用或重切后自动重建并发时，二次 INSERT 撞 kb_graph_unit.uk_unit 中断整文件构建）——已改为撞键降级按槽位更新；当日诱因=重切触发自动重建 + 图谱 LLM 未配置（解析策略 LLM 不在线）致单元批量失败进入重试路径）

## 11. 评测与基准 — benchmark-evaluation

- **代码证据：** BenchmarkController(`/api/kb/{kbId}/benchmarks` CRUD+generate)、EvaluationController(list/get/status/run/delete)、EvaluationExecutionHelper(@Async)；表 kb_benchmark*/kb_evaluation*；测试 BenchmarkResponseParserTest 等 6 个 util 测试
- **可能 REQ 区域：** 基准集管理 + LLM 生成问题、评测运行（异步）、评测结果
- **Depends on:** retrieval、knowledge-graph
- **Shared by:** 无明显下游
- **Depth signal:** graph-eval 模块一半；@Async 异步执行
- **Priority:** 中
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：5 INFERRED + 4 GAP；⚠ 仲裁：评测不走 RetrievalService，自带精简检索管线直连 Embedding/Milvus/Rerank；新登记 schema.sql 4 表同文件双份 DDL、retrieval_metrics JSON/TEXT 双处漂移）

## 12. 发布、版本与知识审核 — publish-review

- **代码证据：** PublishController（版本 CRUD+transition、reviews 提交/approve/reject）与 LogController 在 fastrag-publish（含 aspect）；PublishManageController（publish history/plans/strategy-effect/online+offline-version/reset-configs/reset/update-logs）在 fastrag-knowledge；表 kb_version/kb_publish_history/kb_publish_plan/kb_reset_config/kb_review_* 4 张/kb_update_log；platform 侧 ConfigManageController 的 review-flow/publish-settings 配置端点
- **可能 REQ 区域：** 版本快照与流转（草稿→发布→下线→回滚）、定时发布计划、审核工作流（提交/批准/驳回）、知识库重置
- **Depends on:** kb-core、file-ingestion（INFERRED：版本快照粒度）、platform-config（审核开关）
- **Shared by:** app-center（INFERRED：应用发布记录 app_publish_record）
- **Depth signal:** 后端分散在 2 模块 3 控制器（1.0k）；前端 7 页成族但为孤儿路由（完成度存疑，见上位地图）
- **Priority:** 中
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：3 INFERRED + 5 GAP；两项遗留 INFERRED 升 CERTAIN：版本快照=纯元数据计数不快照内容、审核开关后端零消费无 gate；登记 8 债务：10 端点无 @KbAuth、流转无状态机校验、版本号并发重号、定时发布无执行器、scheduledAt/scheduledTime 字段名失配（前端 PublishPanel.vue:239 vs KbPublishPlan.java:29）致 scheduled_time 亦恒 NULL——2026-09-24 裁决新证）

## 13. 工具 / 技能 / MCP / 外部数据库 — tools-skills-mcp

- **代码证据：** ToolController（CRUD+toggle+test-proxy+api-config+upload+import-json）、SkillController（**18 端点**：文件树/文件 CRUD/依赖/作用域/分享）、McpController（**17 端点**：parse-url/refresh/tools 增删改测）、DbInstanceController（tables/test-conn/**query 直接执行 SQL**/sync）、`executor/`(HttpToolExecutor 等)、`mcp/McpProtocolClient`、`skill/`（AccessManager/DependencyValidator/MarkdownParser）；表 tool*/skill*/mcp_*/db_* 10 张；测试 **14 个（全仓最多）**
- **可能 REQ 区域：** HTTP 工具注册与代理测试、文件化技能（Markdown+frontmatter）、MCP 服务（stdio/SSE）与工具发现、外部 DB 挂载与 SQL 查询（安全面：直接执行）
- **Depends on:** iam
- **Shared by:** app-center（四类绑定 app_tool/skill/mcp/db_binding）、agent（工具执行）
- **Depth signal:** 8.9k LOC；测试最全（⚠ 勘误：实测 16 测试类 137 用例，旧记 14）；SkillController 实数 24 端点（javadoc 记 18）
- **Priority:** 高（是应用/智能体的能力底座）
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：8 INFERRED + 3 GAP；登记：SkillAccessManager 零生产调用（可见性未接线）、SkillMapper 查错列名静默失效、MCP 客户端注释失真（实为 Streamable HTTP）、stdio 仅拒绝桩；⚠ 2026-10-06 技能域专项 grill 新登记 14 项缺陷（运行时断链 D1/D6/D7/D12、安全 D3/D4/D5、未接线 D2/D9/D14、一致性/性能 D8/D10/D11/D13），四批 16 项裁决全部 resolved，修复方案见 [docs/plan/skill-runtime-remediation.md](./plan/skill-runtime-remediation.md)；✅ **修复已实施（同日 /idd-implement M1~M3 TDD）**：14 项缺陷全部修复 + 3 死代码删除（AgentExecutor/SkillToolExecutor/SkillReadonlyBackend 族）+ DDL 双处对齐（新增 SkillDdlParityTest 守护）+ 审计 12 埋点；质量门 = 全仓 16 模块 mvn test BUILD SUCCESS（tools 19 类 226 用例 / agent 160+11skip / application 76，修复前基线全绿零回归）；实现记录见 [implement/tools-skills-mcp.md](./implement/tools-skills-mcp.md) 与 [implement/agent.md](./implement/agent.md) 2026-10-06 增量节；契约偏离（REQ-B05/E01/E02/E04）已登记 implement 文档待下次契约同步。注：SkillAccessManager 零调用与 Mapper 错列名两项旧登记已随 M3-1 消解）

## 14. 模型与平台配置 — platform-config

- **代码证据：** ModelController（CRUD+test-chat/embedding/rerank+import+presets）、ConfigManageController（**~30 端点**：系统 config CRUD/默认值/导入导出/发布与审核开关/安全策略/发布策略/模型训练）、DictionaryController、SensitiveWordController（+模板+批量导入+`checkInput/filterOutput`）、TermController（术语库+启用状态+updateLib）、QueryRuleController、NotificationController、UploadController（`/api/upload`）；表 model/model_training/model_test_report/model_call_log/sys_config(+history)/sys_dictionary/sensitive_word/term_*/query_rule/sys_notification/sys_security_policy/sys_publish_strategy；测试 4 个；近期漂移（数据治理三族）：term_record.status（expandSynonyms 仅消费启用词条）、字典真实字段落库（label/enabled/remark/sort_order，自述此前为硬编码假值）
- **可能 REQ 区域：** 模型接入与连通性测试、系统参数配置、字典/敏感词/术语/查询规则四类数据治理、通知中心
- **Depends on:** iam
- **Shared by:** retrieval（query_rule、term）、fastrag-ai（model 表动态路由）、publish-review（审核配置）、app-center（敏感词过滤）、file-ingestion（上传端点）
- **Depth signal:** 2.8k LOC；64 次变更（60 天第 4）；上位已知缺陷文档 1 篇（假成功缺陷部分修复）
- **Priority:** 中高
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：10 INFERRED + 9 GAP；⚠ 勘误：UploadController 实为单阶段品牌图上传非两阶段；新登记重磅缺陷：model_call_log DDL status 列重复定义（该 CREATE 在 MySQL 必失败）、model 表 context_window/enable_thinking DDL 双处漂移、resetToDefault 死逻辑、train/test 假成功族未修）

## 15. 应用中心 — app-center

- **代码证据：** AppController（CRUD+templates+run）、**AppConfigController（~60 端点，全后端最大配置面**：basic/memory/output/dialog/background/prompt/summary/max-steps/max-turns/retry/max-tokens/triggers+test+run/global-policy 三类/五类绑定/发布记录/dialog-tests CRUD+export/optimizations CRUD+apply+analyze）、AppChatController（**SSE chatStream** + sessions/messages/feedback）；AppServiceImpl 对话输入/输出接敏感词安全过滤（`isSafetyEnabled` 应用级开关，消费 platform 敏感词服务）；表 app_* 22 张；测试 AppConfigIntegrationTest、AppConfigServiceTest 等 3 个
- **可能 REQ 区域：** 应用 CRUD/模板、对话配置全家桶、知识库/工具/技能/MCP/数据库五类绑定、触发器与全局策略、对话测试与优化建议、SSE 对话与会话管理、应用发布、输入/输出安全过滤
- **Depends on:** iam、retrieval、tools-skills-mcp、platform-config、workflow、agent（⚠ 2026-09-24 裁决：耦合度已由 agent/app-center 两链实证厘清——AppServiceImpl:665 经 fakeRun 走 AgentEngine、AppConfigServiceImpl 触发器为占位不调引擎；此句保留为依赖表述）
- **Shared by:** agent（运行入口之一）、operation（chat_session 分析）
- **Depth signal:** 3.4k LOC + 60 端点配置面；50 次变更（60 天）
- **Priority:** 高
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：13 INFERRED + 3 GAP；登记：listConversations 违反 PageResult 宪法（返 {total,items,page,size}）、tokenCount 全链无生产者恒 0、AppDialogOrchestrator ~600 行零测试覆盖、触发器 test/run 为命中计数占位、app.org_id 仅 SchemaInitializer 运行时增补）

## 16. 工作流编排 — workflow

- **代码证据：** WorkflowController（**~35 端点**：CRUD+publish、nodes 增删改移/properties/dimension/test、execute、test-cases、templates、debug、optimizations、migrations、monitor）；表 workflow/wf_node/wf_template/wf_test_case/wf_debug_log/wf_monitor_data/wf_optimization 7 张；上位文档 bpm-design.md（85KB：画布编排/状态机/NodeExecutor）
- **可能 REQ 区域：** 工作流 CRUD/发布、画布节点编排（LLM/检索/重排/模板/代码/条件/起止）、执行与调试、测试用例、监控
- **Depends on:** platform-config（模型）
- **Shared by:** app-center（绑定）
- **Depth signal:** 35 端点 vs 前端仅 1 页（**API 明显超前于 UI**）；⚠ 链已定性（CERTAIN）：执行引擎/调试/监控为**占位**（execute 恒返回硬编码完成态），管理面真实落库；~~`fastrag-bpm` 无 pom 死目录~~（已删除，2026-09-24）
- **Priority:** 中
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：0 INFERRED + 6 GAP——已代裁闭环（2026-09-24）；新登记：data.sql:581 wf_optimization 种子列错位必失败、九权限键种子无消费方、读端点要求 edit 权限）

## 17. 智能体运行时 — agent

- **代码证据：** AgentController（CRUD+default）、AgentConfigController、AgentRunController（runs 创建/取消/**SSE events**/active_run）、`executor/AgentEngine+AgentExecutor`、`backend/`、`context/`、`middleware/`（链式中间件）、`state/`、`event/RunEventPublisher`（⚠ 链勘误：实为本地内存 Map+TODO，非 Redis pub/sub；getEvents 零消费方）；表 agent/agent_run/sandbox_config（⚠ agent/agent_run DDL 仅 SchemaInitializer，schema.sql 缺失；sandbox_config 已双处同步）；测试 20+ 个类 148 用例
- **沙箱实装（2026-09-28，五环链闭环）：** ~~`sandbox/SandboxService` 降级桩~~已删除，实装为沙箱模块族——`sandbox/config/`（SandboxConfigStore：sandbox_config 表 CRUD/校验链/凭据引用/指纹/验证/库存/总开关三级取值）+ `sandbox/SandboxLifecycleService`（(userId,sessionId)→实例绑定落 Redis 无 TTL、acquire 四路径、空闲回收 TTL 1800s、孤儿对账 grace 600s）+ `sandbox/SandboxExecutionService`（7 动作+附件暂存+技能物化+降级三分支）+ `sandbox/provider/`（三适配器缝：Docker=Engine API 直连〔ADR-0006〕、Cube/E2B 预留 UNSUPPORTED）+ `SandboxWorkspaceLayout`（/workspace 布局）+ `EndpointDialGuard`（SSRF 防护）；旧容器内 HTTP 协议废止；集成用例需 Docker daemon（无环境守护跳过 11 条）；遗留：Cube/E2B 控制面接线、app.sandbox_config_id 注入（app-center 链）、fastrag-tools Mcp/Skill 两个测试类既有失配（requireManage 无安全上下文，非沙箱引入）
- **可能 REQ 区域：** 智能体 CRUD/默认智能体、运行（创建/取消/事件流）、中间件链（总结/技能/数据库）、多后端沙箱（配置/绑定/生命周期/降级）、上下文构建
- **Depends on:** tools-skills-mcp（工具/技能执行）、fastrag-ai、platform-config（模型）、Docker daemon（沙箱后端，运行期可选降级）
- **Shared by:** app-center（INFERRED：应用↔智能体调用关系）
- **Depth signal:** 8.8k LOC；架构最独立（Backend+Graph+Middleware+State）；39 次变更（60 天）；前端管理面缺失（INFERRED，见上位地图）
- **Priority:** 中高
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：8 INFERRED + 6 GAP；⚠ 勘误：RunEventPublisher 实为本地内存 Map+TODO 非 Redis pub/sub；⚠ **agent/agent_run 表 DDL 完全缺失于 schema.sql**（仅 SchemaInitializer，硬约定违规）；「应用↔智能体」升 CERTAIN：AppServiceImpl:665 以 fakeRun 走引擎；登记编排层仍用 @Deprecated 旧执行器）

## 18. 运营分析与系统监控 — operation-analytics

- **代码证据：** 9 控制器：HomeController(工作台聚合)、AnalyticsController(kb 分析)、AuditLogController、SysLoginLogController、UnifiedLogController、ChatSessionController、FeedbackController（CRUD+statistics+overview+reply）、DataMiningController（CRUD+run）、ModelMonitorController(overview)；表 chat_session/data_mining_task/user_feedback/sys_audit_log/sys_operation_log/sys_login_log；@Loggable+AOP（common）、SysAuditLogEventListener
- **可能 REQ 区域：** 工作台/主页聚合、知识资产分析、模型监控、用户反馈管理、问答明细、检索日志分析、FAQ/多轮/意图分析、数据挖掘任务、审计三日志
- **Depends on:** 全域（只读消费各域日志与业务表）
- **Shared by:** 无
- **Depth signal:** 2.1k LOC + 9 控制器；52 次变更（60 天）；上位已知缺陷：分页违规 + SQL 拼接 bug（research-operation-pages.md）
- **Priority:** 中低
- **Chain status:** implement（文档链齐，2026-09-23；红绿循环未执行；pending：8 INFERRED + 4 GAP；缺陷信号核实：分页违规=UnifiedLogService 返 IPage 全后端唯一、SQL 拼接=org_id 裸拼+LIMIT 拼接；⚠ 勘误：sys_operation_log 表不存在（三日志=audit/login/kb_log）；chat_session 零写入方为死表；DataMining run 为模拟执行）

## 19. 会话中心与新建对话 — new-conversation

- **代码证据（复用面）**：AppChatController / AppServiceImpl 对话编排与会话账本（app-center 域，旧通道废弃保留）、app_conversation / app_conversation_message 表、model 表路由；对齐目标 WeKnora v0.8.2 sessions 体系（internal/types/session.go SessionLastRequestState:334-347、routes_chat.go:53-82 会话路由组）——本特性为新增行为面（直聊/请求态/会话中心/消息引用），非既有代码重写
- **可能 REQ 区域：** 新建对话与会话中心（按用户全量+置顶+来源标记）、会话请求态（最后输入范围持久化与恢复）、知识库直聊（不挂应用对话、内置缺省、敏感词默认开）、应用预设挂载（App 降级为会话旋钮、范围来源互斥）、LLM 起标题、停止生成、清空/批量删除、消息引用落库（历史可回看）
- **Depends on:** iam（JWT/API Token）、kb-core（KB ACL viewer 逐 KB 校验）、app-center（编排器/会话账本复用）、platform-config（model 路由）、retrieval（检索消费）
- **Shared by:** 无（终端消费特性；fork/rewind、会话附件为其登记的后续扩展）
- **Depth signal:** 2026-09-25 grill 全 5 批 resolved（21 项裁决，`fastrag/.tmp/idd-new-conversation-grill-decisions.md`）；对比调研 [docs/research/weknora-vs-fastrag.md](./research/weknora-vs-fastrag.md)
- **Priority:** 高
- **Chain status:** implement ✅（后端 2026-09-25 M1+M2 全量落地：**模块测试 71/71 绿**（新增 38），[implement/new-conversation.md](./implement/new-conversation.md) 含双入口执行核/解析器/会话中心/控制器与 Deviations 5 条；Tracer Bullet 全链断言零检索/固化/引用投影。遗留：tools 模块 13 个存量测试失败与本特性无关。前端：代码交付 ✅（vue-tsc 零新增错误），contract/ui.md 与 tdd 未做；**联调待启**）

## 20. 问答对提炼 — qa-generation

- **代码证据（复用面）**：kb_qa_pair 表（source=ai/status=draft 预留，QaPairController `/api/kb/{kbId}/qa-pairs`）、qa 召回通道（RetrievalServiceImpl.java:798-839，权重 0.3）、kb_file.processing_mode=qa 预留枚举（KbFile.java:58，消费端无实现 = qa-pairs spec REQ-E03 GAP，本特性清偿）、IngestionConsumer 解析后自动触发先例（:495-510 graph-build）、LLM 生成完整 QA + 防御解析先例（BenchmarkGenerationHelper/BenchmarkResponseParser）、任务状态表先例 kb_graph_index（schema.sql:219-233）
- **可能 REQ 区域**：手动触发（单文件/列表/批量三入口共用参数对话框：条数/自定义指令/replace·append/模型）、qa 模式摄入后自动触发（上传时参数文件级持久化）、父分片粒度 LLM 提炼（prompt 三约束锚定原文）、质量闸门（归一化去重+长度护栏，零额外 LLM 成本）、审核与召回准入（AI draft 确认后才召回——修订 qa-pairs REQ-D04 为 `source='manual' OR status='confirmed'`）、任务状态轮询（每文件一份最新记录）、源分片溯源与悬空降级
- **Depends on:** chunk-management（父分片）、qa-pairs（kb_qa_pair 与确认状态机）、file-ingestion（qa 模式摄入挂钩）、fastrag-ai（LLM）、platform-config（model 表路由）
- **Shared by:** retrieval（召回准入规则变更）、operation（INFERRED：提炼量统计）
- **Depth signal:** 2026-09-26 grill 四批 14 裁决（D1-D14）+ 配置入口 P1-P6 全部已决，落 `fastrag/.tmp/idd-qa-generation-grill-decisions.md`；对比调研 WeKnora——其 Doc2Query 只生成问题不生成答案，本项目做完整问答对
- **Priority:** 中高
- **Chain status:** implement ✅（2026-09-26 spec→design→contract→tdd→implement 五阶同日贯通，2026-09-27 前端 P1-P6 补齐：**模块测试 38/38 绿**（QaGenerationServiceImplTest 27 / QaGenerationConsumerTest 5 / IngestionQaHookTest 3 / QaRecallAdmissionTest 3）+ knowledge/retrieval/bootstrap 全量回归 65 测试类零失败；前端 7 文件（api×2 函数 / QaGenerateDialog 新组件 / chunks·FileTable·FileManager·FileUploader·QaPanel 接线），vue-tsc 触碰文件零新增错误；[implement/qa-generation.md](./implement/qa-generation.md) 含代码文件树/逻辑流/7 条 Deviations/前端交付表；DDL 双处同步（kb_qa_generation_task + kb_qa_pair.source_chunk_id + kb_file.qa_generation_config）。遗留：真库行为级验收 5 项（事务回滚/三分支行级/级联/DDL 对账/真实时钟清扫）与前后端联调待执行）

---

# 跨特性共享代码

| 共享层 | 位置 | 消费方 |
|---|---|---|
| 统一响应/异常/分页 | fastrag-common（ApiResponse、PageResult、GlobalExceptionHandler：业务错误=HTTP 200 + body.code） | 全部 55 控制器 |
| JWT + KB ACL | fastrag-security（JwtAuthFilter、ApiTokenAuthFilter、@KbAuth 切面、KbAccessChecker） | 全部 /api/kb/** 端点 |
| AI 能力层 | fastrag-ai（Llm/Embedding/Rerank/Asr/Ocr/Layout，Ollama 兼容网关 WebClient；EmbeddingService 含瞬态错误 3 次重试） | file-ingestion、ai-chunking、retrieval、graph、benchmark-eval、agent、tools |
| 存储抽象 | infra MilvusService/MinioService/GraphStore(Neo4j 唯一实现，fail-fast)/RabbitMQConfig/MessagePublisher | file-ingestion、chunk、retrieval、graph |
| Redis 直连 | 无 infra 封装，散布 8 处（security/iam/retrieval；⚠ agent RunEventPublisher 经链勘误为本地内存 Map，不计入） | 会话、ACL 缓存 |
| SchemaInitializer | fastrag-bootstrap（幂等增量 DDL）+ init-scripts/schema.sql 全量 | 全部表结构（2026-09-21 已对齐图谱表） |

# 孤儿代码（不隶属任何特性，链外）

- ~~`fastrag-modules/fastrag-bpm/`（无 pom 空目录 + target 残留 137 个陈旧 .class）、`infra/redis|mysql`、`retrieval/engine`、`common/dto|model`、`common/resources`、`security/{handler,resolver}`、`security/src/test` 空包链、`application|platform|publish|tools/model`、`knowledge/{event,manager}` 等 **16 个空目录**~~ **已清理（2026-09-24，A 类纯死物处置）**：bpm 整目录删除（0 git 追踪、0 pom 引用）、空包全删（`fastrag-security/src/test/java` 按 Maven 惯例保留空壳）；`scripts/dbg-pdf-render.mjs`（0 字节）与 `scripts/merge-mapping.cjs`（引用已删文档）一并删除
- init-scripts 中 user_favorite/user_follow/user_recent_visit/user_workspace_layout 4 表**无 Java 实体**（DDL-only 预留，未清理——随下一批迁移脚本处理）
- ~~schema.sql 尾部 4 张已废弃 MySQL 图表 CREATE 与迁移脚本矛盾~~ **已修复（2026-09-21）**：死表 DDL 已删，3 张运行时图谱表已补入

# 优先级与建议构建顺序（依赖前置 × 热度）

```
第 1 批（依赖根）:   1 iam
第 2 批（知识基座）: 2 kb-core → 3 parse-strategy → 4 file-ingestion → 5 chunk-management
第 3 批（当前热点）: 6 onlyoffice-editing → 7 ai-chunking
第 4 批（检索质量）: 9 retrieval → 10 knowledge-graph → 11 benchmark-evaluation
第 5 批（发布治理）: 8 qa-pairs → 20 qa-generation → 12 publish-review
第 6 批（能力底座）: 14 platform-config → 13 tools-skills-mcp
第 7 批（应用层）:   16 workflow → 17 agent → 15 app-center
第 8 批（消费端）:   18 operation-analytics
```

规则：特性 X 的链在其依赖特性链完成前不启动（共享依赖先行）；链编排沿用上位决议（核心 8 项完整链 + 其余 shape-only），如需在普通 commit 信息中追溯：本地图证据基准为 HEAD d8f30cd。
