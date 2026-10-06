# WeKnora vs FastRAG 对比研究报告

> **生成时间**: 2026-09-25
> **研究范围**: WeKnora v0.8.2（本地检出 `D:\Workspace\python\github\WeKnora`，[github.com/Tencent/WeKnora](https://github.com/Tencent/WeKnora)）全仓源码调研，与 FastRAG 后端（HEAD d8f30cd，18 项特性）逐维度对比。
> **方法**: 5 路并行只读子代理实读源码（架构/解析分块/检索/Agent 生态/平台工程），所有结论标注 `文件:行号`，明确区分"代码证实"与"仅 README 宣称"；另以网络检索佐证仓库身份与安全演进背景。任务文档存 `.tmp/weknora-research/`（仓外 .tmp，共 5 份）。

---

## 📋 执行摘要

- **WeKnora 是什么**：腾讯开源的 Go 语言企业知识管理框架（Gin + GORM + dig DI + asynq 任务队列 + PostgreSQL/ParadeDB，Python docreader 解析 sidecar，Vue3 前端），能力覆盖 RAG / Agent / Wiki 三线，约 70+ 张表、**1090 个 Go 测试文件（约占非测试文件 48%）**。与 FastRAG 高度同域，但工程路线不同：**FastRAG 功能面更宽（18 特性：工作流/评测/发布审核/OnlyOffice/运营分析），WeKnora 纵深更深（失败路径产品化、可插拔性、多租户、可观测性）**。
- **最大的差距在"失败路径的产品化"**：WeKnora 把每类失败都做成一等机制——事件可重放（Redis append-only + offset 续读）、检索防截断（5× 过检索池 + EmbeddingTopK≥RerankTopK 强制）、rerank 四级降级、ReAct 循环守卫族、DDL 脏状态自愈、fan-in 计数器防悬挂。FastRAG 已登记的 40+ 债务中，至少 5 项（截断召回、内存 Map 事件、DDL 漂移、查询改写 stub、沙箱降级桩）在 WeKnora 有现成的成熟解法。
- **最值得抄的 5 件事（P0）**：① 版本化迁移唯一 DDL 事实源；② StreamManager 事件模型替换 RunEventPublisher 内存 Map；③ RRF 融合 + 过检索池（直接治"截断召回"）；④ ReAct 守卫族 + 工具按能力探测注册；⑤ 解析引擎注册表 + 健康探测 + 自适应分块决策闭环。
- **FastRAG 并非全面落后**：视频双路解析、AI 分片区域框选、跨页段落合并、解析策略模板、Neo4j 图谱治理（单元化抽取/实体合并/PPR rank）、发布审核工作流、运营分析等，WeKnora 均无或更弱。**图谱与文档加工是 FastRAG 的相对优势区；检索管线与 Agent 治理是 WeKnora 的领先区**。
- **一项宪法级冲突需决议**：WeKnora 用真实 HTTP 状态码（400/401/403/409/429…）+ 分段错误码；FastRAG 宪法规定"业务错误 = HTTP 200 + body.code"。这不是谁对的问题，建议作为宪法修订议题走 IDD 决议（论据见 §3.3）。

---

## 一、项目概况对比

| | WeKnora v0.8.2 | FastRAG（HEAD d8f30cd） |
|---|---|---|
| 技术栈 | Go 1.26 + Gin + GORM + dig DI + viper；Python docreader sidecar（gRPC） | Java Spring Boot，15 Maven 模块，~68.9k LOC / 55 控制器 |
| 数据库 | PostgreSQL（默认 ParadeDB pg17）或 SQLite（Lite 模式）；golang-migrate 112 对版本化迁移 | MySQL；schema.sql + SchemaInitializer 幂等 DDL（双处同步，已知漂移缺陷） |
| 异步 | asynq（Redis 基座），11 类队列 / 6 worker 池硬隔离 + 弹性借用 | RabbitMQ（ingestion / graph-build 等队列） |
| 向量检索 | 可插拔 10 引擎（pgvector/ES/OpenSearch/Milvus/Qdrant/Weaviate/腾讯 VectorDB/Doris/sqlite-vec/Neo4j），默认 pgvector+BM25，RRF 融合 | Milvus + MySQL LIKE 混合 + Rerank |
| 对象存储 | 8 后端（MinIO/S3/COS/OSS/TOS/OBS/KS3/local），租户级可配 | MinIO |
| 测试 | 1090 个 `_test.go`（≈48%），中间件/路由/DI 装配均有测试 | 分布不均（tools 137 用例最多，多模块零测试，红绿循环未执行） |
| 可观测 | OpenTelemetry → Langfuse（opt-in 零开销），全链路 span | 无链路追踪 |
| 部署形态 | Docker 全栈（组件级 profile）/ Lite 单二进制（SQLite+内存流）/ Wails 桌面 / helm | docker-compose 全家桶 |
| 生态 | IM 9 平台、网页嵌入 Widget、MCP 双向、Go SDK/CLI、微信小程序、浏览器技能 | OnlyOffice、MCP 客户端、文件化技能 |

---

## 二、维度一：架构与工程基座（WeKnora 领先）

### 2.1 分层与依赖倒置
- 接口集中在 `internal/types/interfaces/`（约 45 个接口文件），router→handler→service→repository 全链路面向接口，容器负责绑定（`internal/router/router.go:30-48`、`internal/container/container.go:116-619`）。
- **优点**：模块间零实现耦合，替换向量库/存储不动业务层。**对 FastRAG**：15 个 Maven 模块间若存在实现类直接依赖，可借鉴"api 子模块只放接口 + 配置类集中装配"。

### 2.2 一套代码三种部署形态（FastRAG 最值得借鉴的架构决策）
- 以 env 为开关的条件装配：无 Redis 即 Lite——任务队列换 `SyncTaskExecutor`（连 ProcessIn/MaxRetry 语义都对齐 asynq，`internal/router/sync_task.go:16-62`）、StreamManager 换内存实现、数据库换 SQLite，业务代码零改动（`internal/container/container.go:440-471`、`internal/stream/factory.go:18-40`）。
- **对 FastRAG**：当前硬绑定 Milvus/MinIO/RabbitMQ/MySQL 全家桶，演示/交付门槛高。可按同一思路做降级装配（RabbitMQ→内存队列、向量库→pgvector/ES），降级实现收敛在组装层一处。

### 2.3 异步任务：六池硬隔离 + 单一事实源队列拓扑
- 每池独立 asynq Server 并发数硬隔离（core/postprocess/enrichment/maintenance/shared/wiki），shared 池弹性借空闲；队列-池-权重-任务类型集中在 `internal/types/task.go:5-66` 注册表；按错误类型定制重试（wiki 锁冲突 15s 固定重试，`internal/router/task.go:111-126`）；死信表 + 断点恢复 + 重启补触发（`container.go:196-197,615`）。
- **对 FastRAG**：RabbitMQ 消费者若共用并发额度，批量导入会饿死交互任务。可按处理阶段分队列 + 独立 consumer 并发 + 一个弹性消费者，拓扑收敛到一个配置类。

### 2.4 流式事件：append-only StreamManager（对 FastRAG 收益最大的单点）
- 接口 `AppendEvent/GetEvents(fromOffset)`，memory/Redis 双实现（`internal/types/interfaces/stream_manager.go:26-70`）；Redis 实现用 List RPush + TTL，SSE 以 100ms ticker 按 offset 增量拉取（`internal/stream/redis_manager.go:71-148`、`internal/handler/session/stream.go:163-186`）；steer（运行中插话）独立子列表 + Lua 原子去重；live-run 标记（SETNX）实现"会话内唯一进行中 run"跨副本互斥（`redis_manager.go:383-415`）。
- 内存实现的注释自认局限："Single-process only... not suitable for a multi-replica deployment"（`memory_manager.go:29-32`）——**承认局限并给出替代，而不是把内存 Map 当终态**。
- **对 FastRAG**：这是 RunEventPublisher（本地内存 Map + TODO，特性 17 已登记缺陷）的直接解法。定义 StreamManager 接口 + Redis List 实现 + SSE 改 offset 轮询，即可获得断线重连、多副本续流、steer 能力。

### 2.5 错误模型与迁移纪律
- 错误 = 类型化 `AppError{Code, Message, Details, HTTPCode}`（`internal/errors/errors.go:56-61`），错误码分段管理（1000 通用/2000 租户/2100 Agent/2200 向量库绑定…），中间件统一渲染 `{success:false,error:{code,message,details}}` 并携带**真实 HTTP 状态码**（`internal/middleware/error_handler.go:12-46`）。
- 迁移 = golang-migrate，112 对 up/down 全配对，**DDL 只存在于 SQL 文件一处**；脏状态自动回退重试并给可操作错误信息（`internal/database/migration.go:161-191`）；"迁移目录可加载性"被写成回归测试（起因是两个 PR 抢同一版本号导致全部部署失败，`migration_versions_test.go:16-27`）；条件迁移用 PG 会话参数实现同一文件服务两种拓扑（`migrations/versioned/000002_embeddings.up.sql:5-9`）。
- **对 FastRAG**：双处 DDL（schema.sql↔SchemaInitializer）已产生多处漂移缺陷（model_call_log 必失败 DDL 等）。WeKnora 的完整答案：**版本化 SQL 唯一事实 + 启动执行 + 目录可加载冒烟测试 + 脏状态修复指引**。SchemaInitializer 可降级为只读对账校验器。

---

## 三、维度二：文档解析与分片（互有胜负，WeKnora 决策闭环更强）

### 3.1 双进程分工：解析只产 Markdown，分块在主服务
- Python docreader 是 gRPC sidecar，只做"文件/URL → Markdown + 图片引用"，**明确不做分块/存储/OCR/VLM**（`docreader/parser/parser.py:33-38`）；生产分块在 Go `internal/infrastructure/chunker/`（Python splitter 自注释让位："The Go splitter is now the production path"，`docreader/splitter/splitter.py:24-26`）。
- gRPC `ReadStream` 流式逐页返回 markdown + 逐张图片，避免千页扫描件超 gRPC 单消息上限，旧版自动回退 unary（`docreader/proto/docreader.proto:9-15`、`internal/infrastructure/docparser/grpc_parser.go:131-137`）。
- **对 FastRAG**：解析在 JVM 内（POI/FFmpeg）有 OOM 与依赖膨胀风险；可借鉴把重解析隔离为独立进程，协议收敛为"Markdown + 资产引用"，格式扩展不牵动分块代码。

### 3.2 解析引擎注册表 + 三级回退 + 健康直通 UI
- 8 个引擎（builtin/simple/anydoc/weknoracloud/mineru/mineru_cloud/paddleocr_vl(_cloud)）+ 远程引擎自动发现（新引擎零 Go 改动自动出现在列表，`internal/infrastructure/docparser/engine_registry.go:90-145`）；回退链"指定引擎→类型默认→builtin"（`docreader/parser/registry.py:68-114`）；`CheckAvailable` 返回 `unavailable_reason`（如"请安装 opendataloader-pdf 与 Java 11+"）直接展示给用户（`registry.py:116-145`）。
- **对 FastRAG**：解析策略体系（模板/自动匹配/冲突检测）FastRAG 更全；但"引擎健康探测 + 不可用原因展示"是 FastRAG 没有的体验层，可补。

### 3.3 自适应分块：画像 → 分层 → 校验 → 兜底 → 诊断预览（核心亮点）
- auto 模式先单遍扫描做文档画像（标题分布/编号章节/中德英文章节标记/页脚/表格/代码占比/语言），据此选择 tier 链：标题感知 → 启发式边界 → 递归分隔符永远压队尾兜底（`chunker/profiler.go:88-241`、`strategy.go:34-58`）。
- 每层输出过 `ValidateChunks` 校验（拒绝"大文档只切 1 块/碎块过多/全部远小于目标/超 2× 目标"四类坏输出），不合格自动降级下一层（`validator.go:19-75`）；`SplitWithDiagnostics` 返回每层拒绝原因，并暴露**分块预览/调试端点**（`internal/handler/chunker_debug.go:186,209`）。
- **对 FastRAG**：当前是"KB 级策略人工选择"；升级方向是 auto 档（画像自动选策略）+ 输出校验器 + 调试预览接口——比人工挑策略稳健，且让分块决策可解释。

### 3.4 面包屑（ContextHeader）与正文分离
- 标题路径生成 `# 章\n## 节` 面包屑存入 `Chunk.ContextHeader` 而**不进 Content**，保持 `End-Start == rune数` 位置不变量（供文档重建/引用高亮）；最终 embedding 内容 = 文档标题 + 面包屑 + 正文三层拼装（`chunker/splitter.go:17-49`、`knowledge_index_content.go:6-18`）；父子面包屑拼接时去重父块尾行（`strategy.go:250-267`）。
- **对 FastRAG**：kb_chunk 的 title/heading_path 若烧进正文会污染向量与原文回放；可改为"embedding 时拼装、存储保持原文"。三点直接可抄：heading_path 前置进 embedding、子块与父块面包屑去重、文档标题进 embedding。

### 3.5 PDF 逐页混合路由与质量防线
- 逐页分类（图面积占比≥0.5 判扫描页）：原生页保留文本层 + glyph 级 XY-cut 多栏重建 + 版面自检（重建破坏引用格式则回退纯文本，`docreader/parser/pdf_parser.py:912-966`）；扫描页交 VLM 做 OCR。
- 噪声防线（对标 FastRAG OcrNoiseFilter 且更全）：跨页重复页眉页脚统计剔除（≥60% 文本页出现的首末短行）、arXiv 水印/页码剔除、图表轴刻度碎剔除（图表区域裁剪为矢量图交 VLM，不再污染文本块）、隐藏文本（render-mode 3）过滤**顺带防隐藏 prompt 注入**（`pdf_parser.py:235-271,110-115`）。
- 工程防御：pdfium 非线程安全→全局锁 + 多进程 forkserver 并行渲染（注释记录真实事故与">1h 串行渲染"教训）；渲染长边上限 2000px；SMask 透明合成白底防全黑图；跨页重复 logo 按 MD5+页占比剔除。
- **对 FastRAG**：OcrNoiseFilter 可补两条规则——跨页重复行统计剔除、图注碎屑归属图片；PDF 可升级逐页路由 + 版面自检；FastRAG 的跨页段落合并/区域框选（ai-chunking）是 WeKnora 没有的优势，保持。

### 3.6 多模态块是一等公民
- 每张嵌入图独立异步任务做 OCR + VLM Caption，产出 `image_ocr` / `image_caption` 两类**独立 chunk 类型**，带 `ParentChunkID` 指回宿主块并进向量索引（`internal/application/service/knowledge/image_multimodal.go:274-377`）；OCR 输出再过 `sanitizeOCRText` 清洗（剥 code fence、HTML→MD、"无文字内容"空回复字典过滤，`ocr_sanitizer.go:30-117`）。
- **对 FastRAG**：图片 OCR 结果可从"混入父块正文"升级为独立块类型 + 父指回宿主；OCR 空回复字典值得照抄。

### 3.7 FastRAG 仍领先的点
- **视频双路解析、音频 ASR（WeKnora 音频仅 ASR 单路转写，视频直接拒绝：`knowledge_process.go:3571-3580`）**。
- 解析策略模板实体（WeKnora 只有 KB 内嵌 `ParserEngineRules` + 文档级覆盖，无可复用模板库）。
- 跨页表格/段落合并（WeKnora PDF 按页拼 `\n\n`，跨页表格会断）。
- 外部数据源同步是 WeKnora 独有（8 个已实现连接器：飞书/钉钉/Confluence/GitLab/Notion/语雀/IMA/RSS；增量游标 checkpoint 续传 + 确定性任务 ID 去重，`internal/datasource/connector.go:11-119`）——FastRAG 若规划同类功能，这两个模式直接可抄。

---

## 四、维度三：检索与 RAG 管线（WeKnora 明显领先，逐条可迁移）

### 4.1 管线全景（每次请求动态组装，`internal/application/service/session_knowledge_qa.go:217-229`）
```
LOAD_HISTORY → MEMORY_RECALL（不调模型、永不报错）→ QUERY_UNDERSTAND（LLM 改写+意图分类，一次 JSON 输出）
→ CHUNK_SEARCH_PARALLEL（chunk 检索 ∥ 图谱实体检索；KB ∥ Web 并发；按 embedding 模型分组共享一次查询向量）
→ [HybridSearch: 过检索池 max(5×TopK,50) 上限 500 → 多引擎 fan-out → 跨引擎分数归一化 → RRF 融合(k=60, 向量0.7/关键词0.3, 租户可配)]
→ [低召回 → 本地规则查询扩展（jieba，≤5 变体，不花 LLM）]
→ CHUNK_RERANK（passage 工程 → 四级阈值降级 → 复合分 0.6模型+0.3基础+0.1来源 → MMR 多样性）
→ CHUNK_MERGE（8 步：去重 → 历史引用注入(Jaccard≥0.15, 折扣0.6, ≤3条) → 父子解析 → 顺序块合并 → FAQ 回填 → 短文本邻块扩展(350/850 rune) → 部分重叠剔除(overlap≥0.85)）
→ FILTER_TOP_K（确定性排序 + 4 级 tie-break）→ INTO_CHAT_MESSAGE → 流式输出 + 引用实时展开
```
闲聊意图走精简管线（跳过检索全链，`session_knowledge_qa.go:195-214`）。

### 4.2 对 FastRAG 的直接可迁移项（按收益排序）

| # | 机制 | WeKnora 证据 | FastRAG 对应痛点 |
|---|---|---|---|
| 1 | **RRF 融合替代分数加权** | `knowledgebase_search_fusion.go:129-187` | Milvus 余弦分与 MySQL LIKE 命中不在同一量纲，硬加权不可比；RRF 只需两路各自排序。QA 通道 0.3 加权可并入 RRF 第三路 |
| 2 | **过检索池 + EmbeddingTopK≥RerankTopK 强制** | `knowledgebase_search.go:20,215-218`；`session_knowledge_qa.go:981`；pgvector 侧 TopK×2 扩展候选 + ef_search 调优（`postgres/repository.go:343-443`） | 已登记"截断召回"问题（rag-chunk-recall-enhance.md）——这是系统性解法 |
| 3 | **Rerank 四级降级链**（阈值→降为×0.7→top-1 fallback→全灭返空；显式 scope 时无条件保留 top-1） | `reranking/rerank.go:192-210,40-45`；模型失败不是错误，原样返回+诊断 | 降低"检索到但被滤光"的静默失败 |
| 4 | **Rerank passage 工程**（去 markdown 噪音；拼文档标题、图片 caption/OCR、已生成问题） | `reranking/passage.go:67-173`（注释给了分数 0.002→0.45 实例） | 成本极低、收益直接 |
| 5 | **MMR 多样性 TopK**（λ=0.7，增量实现 O(k·n)） | `reranking/mmr.go:17-52` | Rerank 后直接截断易出近重复 |
| 6 | **短文本邻块扩展 + 部分重叠剔除** | `chat_pipeline/merge_expand.go:16-19`；`search.go:226-303` | 截断召回的对症补充；chunk 有 Pre/NextID 即可做 |
| 7 | **查询改写+意图分类一次 LLM 调用**（失败保留原 query；闲聊零检索） | `chat_pipeline/query_understand.go:61-165` | 查询改写 stub（query_rule 零消费、suggest 占位）的最小可用品：先做不花钱的规则版扩展（`query_expansion.go:16-175`），LLM 改写做可选开关 |
| 8 | **引用私有句柄协议**：模型只看请求级句柄 cN/wN，引用写 `<ref id="cN"/>`，流中实时展开为公开 `<kb chunk_id=.../>`；未知句柄失败闭合；模型伪造的标签一律删除；chunk 带 StartAt/EndAt 原文坐标供回跳 | `modelcontext/citations.go:14-51,170-302`；`types/chunk.go:147-150` | UUID 不进 prompt（省 token+防泄漏+防伪造引用），FastRAG 引用溯源可直接套用 |
| 9 | **历史引用折扣复用**（多轮指代"那这个呢"） | `chat_pipeline/merge_history.go:17-87` | 多轮对话目前只靠改写消解的第二手段 |
| 10 | **确定性排序 tie-break**（score→knowledgeID→chunkType→chunkIndex→ID） | `chat_pipeline/filter_top_k.go:76-96` | 评测管线同分抖动会污染指标 |
| 11 | **多 KB 按 embedding 模型分组共享一次查询向量** | `chat_pipeline/search.go:382-395` | 多知识库检索的免费优化 |

### 4.4 图谱与 Wiki 对比
- **GraphRAG**：WeKnora 有但较轻——请求路径 LLM 抽实体 → Neo4j `CONTAINS` 子串匹配两跳邻居（`chat_pipeline/extract_entity.go:140-144`、`retriever/neo4j/repository.go:271-295`），且图构建器状态在进程内存（多副本受限）。**FastRAG 的图谱线更深**（单元化抽取/端点确定性 ID/实体向量合并治理/PPR rank/mention 三表防线），此项保持优势；可借鉴的是 WeKnora"图谱命中 chunk 以 MatchTypeGraph 并入主检索结果统一重排"的融合方式。
- **Wiki 自动整理**（WeKnora 独有）：文档入库 30s debounce → LLM 合成实体/概念/摘要/索引页（taxonomy/引用解析/死链清理），检索时 wiki_page chunk 得分 ×1.3（`wiki_ingest.go:30-140`、`chat_pipeline/wiki_boost.go:12-15`）。FastRAG 暂不需要完整 Wiki，但"**预合成层 + 检索加权**"思路可先用于主题摘要卡。
- **评测**：WeKnora 有指标库（Precision/Recall/MRR/MAP/NDCG/BLEU/ROUGE，`service/metric/`）但评测任务存进程内存、重启即丢；**FastRAG 的基准集管理 + 落库评测在此项更完整**。

---

## 五、维度四：Agent / 工具 / MCP / IM（WeKnora 领先最大的维度）

### 5.1 ReAct 运行时：把失败路径全部产品化
- 经典 ReAct 循环（think→analyze→act→observe，`internal/agent/engine.go:701-1048`），治理密度极高：
  - **四类防卡死守卫**：空内容重试≤2 次、无工具调用且内容逐字重复≥2 轮判卡死、连续 3 轮 completion-token 截断放弃、上下文溢出只做一次"压缩后重试"——每类有独立计数器与差异化处置，且带 issue 编号回归测试（`internal/agent/const.go:40-64`、`engine.go:487-501,843-902`）。
  - **上下文治理**：压缩器有停止条件（"无可压缩"/"释放<5% 判失败"）+ Checkpoint 持久化供下轮续起；token 估算按厂商真实 usage 自校准缩放系数（`compaction/compactor.go:87-110`、`calibration.go`）。
  - **steer 插话**：运行中用户插话在轮边界排空注入（`agent/steer.go:38-89`）；取消时已生成的部分内容保留为中间步骤而不当最终答案。
- **对 FastRAG**：AgentEngine 若只有 MaxIterations，至少补齐空响应 nudge + 复读检测 + `finish_reason=length` 拒绝执行工具三类守卫。

### 5.2 工具体系治理
- 注册 **first-wins 防同名劫持**（注释引 GHSA 编号，`tools/registry.go:54-57`）；发给 LLM 的定义按名称排序以命中厂商 prompt cache（`registry.go:120-127`）；执行管线：参数类型矫正 → JSON Schema 校验（失败即返回格式化错误，省一轮浪费执行）→ **输出预算**（默认 16KB，rune 计数防 CJK 逃逸，shell 输出头尾保留）；JSON 修复器判定"截断于半路"则**拒绝执行**而非带病运行（`act.go:420-436`）。
- **并行工具调用 + 显式读写屏障**：只读白名单并发（上限 8），写操作自动成屏障，不需要模型声明依赖（`act.go:292-333`、`execution_policy.go:6-17`）。
- **能力探测注册**：没有沙箱后端的部署，`shell_exec`/`write_sandbox_file` **根本不注册**——"功能不存在"而不是"功能报错"（`tools/definitions.go:38-43`，注释原话"A checkbox would have been a lie"）。
- **对 FastRAG**：Docker 沙箱降级桩（特性 17 已登记缺陷）的正确形态就是这条——能力探测后不注册工具；再加输出预算截断。

### 5.3 MCP 双向 + 目录代理
- client 侧：SSE / HTTP Streamable 完整实现（连接缓存/失效重建/OAuth 全链路 RFC 9728），stdio 一刀切拒绝（`mcp/manager.go:97-100`）——与 FastRAG"stdio 拒绝桩"同构，但 SSE/HTTP 两条路做完整。
- **目录代理模式**：Agent 侧不直接暴露几十个 MCP 工具 schema（注释提到 232 个工具 ≈105k token 的问题），而是 `discover_mcp_tools`（list/describe/search 分页）+ `call_mcp_tool(tool_ref, arguments)` 三段式，`tool_ref` 是请求级句柄，防模型臆造工具名（`tools/mcp_catalog.go:19-116`）。
- server 侧（把知识库开放给 Cursor/Claude）：**per-endpoint 可见性通过 tools/list 过滤器 + tools/call 中间件双层实施**——"allowlist 隐藏不等于不可调用"（`mcpserver/server.go:108-123`）；无状态传输支持多副本；KB 范围逐个重新授权。
- **对 FastRAG**：现有 Streamable HTTP 客户端可直接复用；目录代理模式在 MCP 工具多时比全量暴露更可扩展；"开放知识库给外部 IDE"若列入规划，此架构可直接照抄。

### 5.4 请求级句柄注册表（modelcontext，一石三鸟）
- 检索结果/MCP 工具/文档对外部模型只暴露请求级短句柄（cN/dN/bN/wN/mtN），UUID 不进 prompt：省 token、防注入（句柄不可伪造 + 外部 schema 附注入防御声明）、可审计（Langfuse 同时记录 `model_arguments` 与 `resolved_arguments`）；**未解析句柄 fail-closed**——"幻觉/过期 token 永远到不了持久层、外部服务或路由决策"（`modelcontext/registry.go:1-73`、`act.go:546-550`）。
- **对 FastRAG**：若检索结果直接把数据库 ID 给模型，可先做最小版：请求级 Map + 系统 prompt 声明 + 未识别 ID 拒绝。

### 5.5 沙箱：五后端 + 会话绑定 + 空闲回收 + checkpoint
- 后端谱系：docker / cube(MicroVM) / e2b / host（macOS Seatbelt、Windows 受限令牌）/ disabled（`sandbox/sandbox.go:12-35`）；SessionID→沙箱绑定存 Redis（Lua 条件删除 + 30 分钟 turn lease 防泄漏）；Docker 空闲回收器**防伪造**（活动标记文件 mtime 在未来则拒绝采信，`docker_idle_sweeper.go:177-207`）；每轮完成后对 /workspace 做 git commit 作 checkpoint，支持会话 fork 回滚；技能产物自动从沙箱抽取绑定到消息。
- **对 FastRAG**：沙箱落地后的最小闭环 = 能力探测注册 + 会话绑定 + 空闲回收 + 产物抽取。

### 5.6 IM 渠道与长期记忆（WeKnora 独有，按需引入）
- IM：统一消息模型 + 4 个必选适配器方法 + **可选能力接口**（StreamSender/FileDownloader 按平台渐进启用，服务层三档降级）；9 平台（企业微信/飞书/Slack/Telegram/钉钉等）；长连接每 6 小时主动重建防 SDK 僵尸连接；多副本 Redis leader 选举（`im/adapter.go:126-182`、`supervisor.go:42-97`）。
- 长期记忆：召回注入（≤600 rune 预算 + 常驻块）+ 按需 `search_memory` 工具深查 + 后台异步抽取（预算上限/水位线/分段）+ **用户确认/拒绝闭环**（拒绝理由被记住防重复提议）+ 文档亲和影响检索排序；注入文本显式声明"是用户背景不是指令"防记忆投毒（`types/interfaces/memory.go:278-357`、`memory/extract.go:19-120`）。
- 原则可先于功能引入：**记忆读路径零模型调用 + 失败静默降级 + 只 advisory（影响改写）绝不过滤知识库**。

### 5.7 工作流的答案（重要认知）
- WeKnora **没有 DAG/流程引擎**——复杂编排全部收敛进 ReAct 循环（todo_write 计划 + steer 修正 + checkpoint 续传）。FastRAG 工作流"管理面真实落库、执行引擎占位"的对标启示有两条路：要么把工作流问题交给更强的 Agent 循环治理，要么自建执行器——**抄不到现成实现**。

---

## 六、维度五：数据模型 / API / 多租户 / 工程化（WeKnora 领先，多项为宪法级差异）

### 6.1 多租户与 RBAC（tenant 是一等公民）
- `tenants` 表 + 几乎所有业务表带 `tenant_id`；四级角色数值阶梯 `Owner(40)/Admin(30)/Contributor(20)/Viewer(10)`；**资源归属守卫**：`RequireOwnershipOrRole`——"Contributor 在自己建的 KB 里如 Owner，在别人的 KB 里如 Viewer"（`middleware/rbac.go:214-249`）；KB 跨租户共享显式带 `source_tenant_id`，通过守卫后**重写 request context 的租户**使下游检索自动落到正确的向量库（`middleware/kb_access.go:235-241`）。
- 错误路径三分纪律：查无资源放行给 handler 回 404（不把"URL 错"伪装成 403）、判定依赖故障回 503、无权限 403（`rbac.go:16-24,239-245`）。
- **API Key 模型**（成熟度最高的部分之一）：9 种 Principal 与 User 解耦（`types/principal.go:9-19`）；key 分 tenant/platform 两种 scope；**~24 种 capability** 粒度（retrieve/chat/ingest/manage_kbs…，每个常量注释写清"给什么、不给什么"）；KB 白名单叠加；**路由级默认拒绝 + 启动 panic 自检**（策略表指向不存在的路由则启动失败，`router/rbac.go:407-431`）；key 明文 AES-256-GCM 加密落库，加密失败直接中止写入。
- **对 FastRAG**：@KbAuth（owner/editor/viewer）可逐项对齐：四级数值阶梯、creator 归属守卫、404/403/503 三分、API Token 加 capability 粒度与默认拒绝。路由层值得抄一段"守卫选择指南"注释（WeKnora 在 `router/rbac.go:7-36` 写明"选错守卫是本仓库 RBAC bug 的最大单一来源"）。

### 6.2 安全治理（每包防一种攻击，均有专项测试）
- `ipclass`：12 类 IP 分类学，处理 NAT64/Teredo/6to4 内嵌 IPv4 绕过，Invalid 默认 fail-closed（`ipclass.go:29-211`）；SSRF：进制混淆 IP 正则 + **DNS 解析后逐 IP 复检防 rebinding** + 受限端口表在 URL 校验与拨号各查一次 + 重定向剥离敏感头（`utils/security.go:263-830`）；`storageurl`：默认 `resource://` 句柄走鉴权代理，KB 受限 API key 请求 public 模式直接 403（`mode.go:130-135`）；限流 Redis Lua 原子滑动窗口 + Redis 不可用时本地兜底；审计日志 append-only（无 UpdatedAt/软删列，单调 ID 兼作游标）+ 六种 outcome 区分（`accepted ≠ success`，`types/audit_log.go:196-237`）。
- **对 FastRAG**：OnlyOffice 下载 URL 无 SSRF 白名单、raw 不校验 url key 等已登记安全债务，可对照 `security.go` 的 SSRF 全家桶补齐。

### 6.3 响应约定与错误码（宪法级冲突，需决议）
- WeKnora：`{success:true,data}` + **真实 HTTP 状态码**（201/200/400/401/403/404/409/429/503）+ 分段数值错误码，中间件统一渲染；分页显式解析且**上限硬顶 100**。
- FastRAG 宪法：统一 ApiResponse/PageResult、业务错误 = HTTP 200 + body.code。
- **评估**：两边的"统一 envelope"精神一致；差异在状态码语义。真实状态码让网关/监控/重试/熔断可直接工作（429 限流、503 熔断对 HTTP 客户端才有意义）；FastRAG 的优势是 envelope 归一更彻底（WeKnora 中间件层 401/403 仍返回裸 `gin.H`，未 100% 归一）。**建议**：作为宪法修订议题走 IDD 决议——最小改法是"4xx/5xx 类错误改用真实状态码，body 仍保留 ApiResponse 结构"，并同步修订调用方契约（前端必须判断 body.code 的约定可保留兼容期）。

### 6.4 工程化
- **测试分布倒置学习**：WeKnora 1090 个测试文件中 middleware 16 + router 20 + container wiring 测试——**权限执行点与装配层不是测试洼地**；安全专项测试文件名即清单（`system_storage_ssrf_test.go`、`rbac_selfcheck_test.go` 等）。FastRAG 测试集中于 tools 模块、中间件层薄弱，正好相反。
- **CI 增量 lint**：`--new-from-merge-base` 只 lint 新增代码——"新代码零容忍，旧债务不阻塞"。对 FastRAG 的 40+ 存量债务，这是比清零更现实的治理策略（WeKnora 自身也背着历史 lint 积压，同策略）。
- **可观测性最小路径**：OpenTelemetry SDK → OTLP/HTTP → Langfuse v3，**默认关闭时所有入口都是廉价 no-op**（可无条件接线）；span 层级 agent.execute → agent.round.N → agent.tool.<name>，检索/嵌入/重排均挂 child span；trace 上下文经 asynq 中间件跨进程传播（`tracing/langfuse/manager.go:33-111`、`asynq.go:19-83`）。FastRAG 无链路追踪的最小起步：chat turn = root trace，检索/生成 = child span，env 开关。
- **供应链**：12 个 CI workflow 分模块独立（permissions: contents: read + concurrency 取消），许可证捆绑检查进 CI（`check-license-bundle.sh`）。

---

## 七、能力对照总表（FastRAG 18 特性 × WeKnora 对标物）

| FastRAG 特性 | WeKnora 对标物 | 谁更强 | 关键差异 |
|---|---|---|---|
| 1 IAM | tenants + 四级角色 + 9 Principal + capability API Key | **WeKnora** | 多租户一等公民、资源归属守卫、机器凭证治理深；FastRAG 有微信扫码/组织树 |
| 2 kb-core | knowledge_bases 等 | 相当 | — |
| 3 parse-strategy | ParserEngineRules（内嵌）+ 引擎注册表 | 各有所长 | FastRAG 有模板/自动匹配/冲突检测；WeKnora 有引擎健康探测+不可用原因展示 |
| 4 file-ingestion | asynq 流水线 + 8 引擎 + 数据源同步 | 各有所长 | FastRAG：视频双路/ASR/OCR 噪声清洗；WeKnora：引擎可插拔、任务分池、fan-in 计数器、外部源同步（FastRAG 无） |
| 5 chunk-management | Go chunker + 父子 + 修订回滚 | **WeKnora** | 自适应分块闭环、面包屑分离、TokenLimit、编辑修订/回滚/索引状态机；FastRAG 有手工编辑但无修订 |
| 6 onlyoffice-editing | 无 | **FastRAG** | 独有 |
| 7 ai-chunking | 无对应 | **FastRAG** | 区域框选/跨页拖拽/PDF layout 分析独有 |
| 8 qa-pairs | FAQ 索引 + 答案回填 + 迭代 TopK | 相当 | WeKnora FAQ 只走向量通道（弱）；有答案回填（强） |
| 9 retrieval | 完整混合检索管线 | **WeKnora** | RRF/防截断/rerank 四级降级/MMR/引用句柄/意图分类/邻块扩展全面领先；FastRAG 改写 stub |
| 10 knowledge-graph | 轻量 GraphRAG + Wiki | **FastRAG**（图谱） | FastRAG 单元化抽取/合并治理/PPR 更深；WeKnora 图谱请求路径 LLM 调用 + CONTAINS 粗糙；Wiki 合成层 FastRAG 无 |
| 11 benchmark-evaluation | 指标库（内存态任务） | **FastRAG** | FastRAG 基准集管理+落库评测更完整；WeKnora 指标种类多 |
| 12 publish-review | 无（仅 chunk revision） | **FastRAG** | 版本流转/审核工作流独有 |
| 13 tools-skills-mcp | 工具治理 + Progressive Disclosure 技能 + MCP 双向 | **WeKnora** | first-wins 防劫持/输出预算/读写屏障/MCP 目录代理+server 化；FastRAG 有外部 DB 挂载（WeKnora 有 database_query 工具） |
| 14 platform-config | 模型 catalog（27 厂商）+ 租户级检索配置 | 各有所长 | FastRAG 敏感词/术语/查询规则治理面更宽（部分 stub）；WeKnora 租户级 RetrievalConfig 更细 |
| 15 app-center | 会话/渠道/嵌入 Widget/IM | 各有所长 | FastRAG 60 端点配置面+触发器+敏感词过滤；WeKnora 渠道生态（IM 9 平台/Widget/MCP server）独有 |
| 16 workflow | 无工作流引擎 | 平手 | 双方执行引擎都缺：FastRAG 占位、WeKnora 收敛进 ReAct |
| 17 agent | ReAct 全治理运行时 | **WeKnora** | 守卫族/压缩 checkpoint/审批门/句柄/沙箱五后端+回收 vs FastRAG 沙箱桩+内存事件 |
| 18 operation-analytics | 审计日志（append-only 设计佳） | **FastRAG** | 运营分析/反馈/数据挖掘面宽；WeKnora 审计 schema 设计更好可抄 |
| （FastRAG 无） | 多租户/外部数据源/IM/Widget/长期记忆/Wiki/Lite/SDK/CLI/桌面版 | WeKnora 独有 | 按产品路线按需引入 |

---

## 八、借鉴路线图（建议优先级）

### P0——直接治已登记缺陷，1-2 周级
1. **检索防截断组合拳**（→ 特性 9）：RRF 融合（k=60，向量/关键词/QA 三路）、过检索池 `max(5×TopK, 50)` 上限 500、强制 `EmbeddingTopK ≥ RerankTopK`、rerank 阈值四级降级 + passage 标题拼装。
2. **DDL 单一事实源**（→ 全局）：schema.sql 定为唯一事实，SchemaInitializer 降级为只读对账校验 + "DDL 可解析"冒烟测试；中期引入 Flyway 版本化迁移（先新表后存量）。
3. **Agent 事件可重放**（→ 特性 17）：StreamManager 接口 + Redis List 实现（RPush/LRange offset/TTL）+ SSE 改 offset 轮询；替换 RunEventPublisher 内存 Map。
4. **工具治理最小集**（→ 特性 13/17）：工具输出预算截断（rune 计数）+ JSON 截断拒绝执行 + 沙箱缺失时能力探测不注册工具（替换降级桩语义）。

### P1——质量与可观测，1-2 月级
5. 分块升级：auto 画像分层 + 输出校验器 + 分块预览调试端点；heading_path/文档标题仅入 embedding 不烧正文；TokenLimit 按模型上限 clamp。
6. 多模态块一等公民：`image_ocr`/`image_caption` 独立 chunk 类型 + 父指回宿主 + OCR 空回复字典。
7. 查询理解最小品：规则版查询扩展（jieba，低召回触发，零 LLM 成本）→ 再上 LLM 改写+意图分类合并为一次调用（治 query_rule/suggest 双 stub）。
8. ReAct 守卫族：空响应 nudge / 复读检测 / length 截断拒执行 / 溢出一次压缩重试。
9. Langfuse/OTel opt-in 追踪：chat turn = trace，检索/嵌入/重排/生成 = span，默认关闭零开销。
10. 引擎健康探测：解析引擎不可用原因直通 UI。
11. 增量 lint CI：新代码零容忍、存量 40+ 债务隔离不阻塞。

### P2——能力扩展，按产品路线
12. 引用私有句柄协议（省 token + 防伪造引用 + 原文坐标回跳）。
13. 安全补强：SSRF 全家桶（DNS 复检/进制混淆/端口表）、下载 URL 白名单、审计 append-only schema。
14. RBAC 深化：creator 归属守卫、404/403/503 三分、API Token capability 粒度 + 默认拒绝 + 启动自检。
15. 任务队列分池（RabbitMQ 按阶段队列 + 独立并发 + 弹性消费者）。
16. MCP server 化 + 目录代理；IM 渠道适配器（若产品需要）；Lite 降级部署形态（条件装配）；长期记忆四件套（先召回注入 + search 工具）。

### 宪法级议题（走 IDD 决议）
- 业务错误是否从"HTTP 200 + body.code"改为真实状态码（论据：限流/重试/熔断/网关监控依赖状态码语义；WeKnora 证明统一错误模型与真实状态码可兼得）。涉及全前端契约与宪法修订，需独立评审，勿顺手改。

---

## 九、WeKnora 的弱点（避免照搬）

1. **解析面**：无视频支持；图片-块关联用 `strings.Contains(URL)` 近似挂接（`knowledge_process.go:4160-4171`）；PDF 全局锁使单实例解析串行；无跨页表格合并；无解析策略模板实体；内置图片解析零本地 OCR（无 VLM 部署图片能力为零）。
2. **检索面**：Postgres 关键词通道绑定 ParadeDB 扩展语法（非默认发行版不可用）；图通道请求路径多一次 LLM 调用且 `CONTAINS` 子串匹配粗糙；查询扩展纯规则无同义词；FAQ 只走向量索引；评测任务存进程内存重启即丢；store 组内 fan-out 全有全无。
3. **Agent 面**：SSE 为 100ms 轮询非推送（无 pub/sub 即时唤醒）；同步 EventBus 在 Agent 关键路径（Redis 抖动拖慢轮次）；MCP stdio 一刀切拒绝（本地 filesystem/git 类 MCP 生态不可用）；discover→describe→call 三段式增加 LLM 往返；Lite 模式下记忆自动抽取静默失效。
4. **工程面**：`container.go` 2018 行巨型装配；配置双轨制（viper 与散落 `os.Getenv` 并存，优先级不一致）；成功响应无统一结构体（gin.H 手写——**这点 FastRAG 的 ApiResponse 宪法反而更规范**）；RBAC 默认 fail-open；PG/SQLite 双方言迁移双维护且 SQLite 侧滞后；无 HTTP 层 e2e；`im/service.go` 3523 行巨石文件。

---

## 十、信息差异与存疑点

| # | 差异/存疑 | 说明 |
|---|---|---|
| 1 | MCP stdio 两处证据并存 | agent 连接侧完全拒绝 stdio（`mcp/manager.go:97-100`）；但 `utils/security.go:563-688` 存在 `ValidateStdioCommand` 白名单校验函数。网络检索证实 v0.2.5 曾修复 MCP stdio 命令注入（CVE-2026-22688）。合理解释是校验函数服务于其他调用面（如独立 Python mcp-server），本地未逐一确认调用方——**存疑，引用时注明** |
| 2 | 数据源连接器宣称 > 实现 | README 宣称 9+ 数据源；实现目录仅 8 个连接器，元数据注册表另声明 16 种（`datasource/connector.go:176-313`）——UI 已注册、实现未落地的差距 |
| 3 | "支持 Milvus" 的语义 | WeKnora 支持但默认拓扑是 Postgres(pgvector)+ParadeDB；且对 Milvus COSINE 分数做 (s+1)/2 变换——与 FastRAG 直用 cosine 的量纲不同，对标行为时注意 |
| 4 | 版本背景 | 本地检出 v0.8.2（`VERSION:1`）；网络检索显示历史版本修过 2 个安全漏洞（v0.2.5 / v0.3.2），本地未复测，仅作背景 |

---

## 十一、验证状态说明

- ✅ **多源交叉证实**（≥2 路独立调研 + 代码行号）：golang-migrate 112 对迁移、asynq 六池、StreamManager memory/redis 双实现、RRF 融合、父子分块、MCP 双向、Langfuse/OTel、四级角色 RBAC、审计 append-only。
- ✅ **代码直证**（单路但带 file:line，可信）：本报告所有机制条目均落到具体实现文件；WeKnora README 宣称的"四级角色/多空间 RBAC/权限范围 API Key/空间审计"经逐项代码核实**无夸大**。
- ⚠️ **部分证实**：MCP stdio 校验函数的调用方未确认（存疑点 1）；数据源 16 种元数据无实现目录（宣称>实现，存疑点 2）。
- ❓ **未验证**：运行时行为（未部署实测）；性能声明（渲染并行、asynq 吞吐）均来自代码注释自述。
- FastRAG 侧基线取自 `docs/feature-map.md`（IDD 链产物，2026-09-21~24 实测登记）与 `AGENTS.md` 硬约定。

---

## 📚 参考来源

**主来源（本地源码，全部结论的证据）**
- `D:\Workspace\python\github\WeKnora`（v0.8.2）——正文各条目已标注 `文件:行号`，关键文件：`internal/container/container.go`、`internal/types/task.go`、`internal/stream/redis_manager.go`、`internal/database/migration.go`、`internal/infrastructure/chunker/{profiler,strategy,validator}.go`、`docreader/parser/pdf_parser.py`、`internal/application/service/knowledgebase_search_fusion.go`、`internal/reranking/{rerank,passage,mmr}.go`、`internal/agent/{engine,const,act}.go`、`internal/mcpserver/server.go`、`internal/middleware/{auth,rbac,kb_access,error_handler}.go`、`internal/types/{tenant_api_key,audit_log,principal}.go`、`internal/utils/security.go`
- FastRAG 基线：[docs/feature-map.md](../feature-map.md)、[AGENTS.md](../AGENTS.md)

**网络佐证（仅用于仓库身份/版本/安全背景，未作为机制结论来源）**
- [Tencent/WeKnora — GitHub](https://github.com/Tencent/WeKnora)（官方仓库）
- [pkg.go.dev: github.com/Tencent/WeKnora/internal/im](https://pkg.go.dev)（模块发布佐证）
- [腾讯开源 WeKnora 企业级实践 — CSDN](https://blog.csdn.net)（第三方评测，低权威度，仅背景）
