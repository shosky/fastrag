# AI 分片（ai-chunking）— 架构设计

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **AiChunkWorkflowModule** | 分析编排器：串联「解析→段落模型→自动分片」并翻译成 SSE 事件流/同步载荷两种出口 | AiChunkService.java、AiChunkServiceImpl（analyze/stream 部分）:141-240 | 深模块：吞掉「同步与流式两套出口共享同一流水线」的重复复杂度，事件协议（progress/fragment/chunk/done/error）只在内部出现一次 |
| **ParagraphModelAssembler** | 段落对齐模型构建：DocNode→段落序列装配、结构级跨页合并、PDF rects 内容锚定与兜底分带 | AiChunkServiceImpl（buildParagraphs/mergeCrossPage/fillPdfRects/assignLineRanges）:901-1110,1623-1857；AiChunkParagraph.java | **深模块（本链候选 1）**：吞掉行盒几何、模糊匹配、孤儿行吸收、锚定失败兜底全部复杂度，对外只暴露「段序列 + 坐标 + 锚定文本」三件套；跨页合并的三类型判定与拒绝规则收拢于此 |
| **AutoChunkStrategy** | 自动分片三级降级：LLM 分组→Embedding 相似度→长度兜底，统一收尾（硬上限二次切分 + 迷你组回收 + 编号） | AiChunkServiceImpl（autoChunk/llmGroup/embeddingGroup/ruleGroup/finishAuto/splitList/mergeMiniLists 等）:1121-1576 | 深模块：三级路径差异被 finishAuto 收口为同一产出形态；LLM 输出校验（覆盖/升序/无重复）与回退决策不出模块 |
| **RegionExtractor** | 区域框选内容提取：文字行命中→内容图直达→整区域多级重试 OCR 的三级尽力路径 | AiChunkServiceImpl（ocrImageRegion/cropPtRegion/invertImage/redSealEnhance/uploadRegionImage）:246-563 | 深模块：旋转/坐标基准换算、扫描底图判别、OCR 失败兜底（截图占位）全部内部消化 |
| **LayoutAnalysisOrchestrator** | 版面分析：几何/VLM 分层路由、逐页推送、MinIO 缓存与版本失效 | AiChunkServiceImpl（streamLayout/readLayoutCache/extractPageLines）:575-764,1860-1962；PdfLayoutAnalyzer.java | 深模块：「哪页走几何、哪页走 VLM」的分层决策 + 缓存生命周期（版本号、全成功才落盘）收拢；PdfLayoutAnalyzer 为纯函数几何分类器（无 IO） |
| **ApplyOrchestrator** | 落库替换编排：状态准入→文件级互斥→删 auto 留 manual→请求分片直落库→派生产物→文件状态对账 | AiChunkServiceImpl（apply/doApply/writeParsedArtifacts）:766-875,1986-2016 | **深模块（本链候选 2）**：ADR-0001 的姊妹不变式「用户确认边界=唯一事实源」的执行面；三处状态（MySQL/Milvus/图谱）的一致性语义与降级边界在此定义 |
| AiChunkController **(S)** | HTTP/SSE 暴露面：5 端点参数解析与 @KbAuth/@Loggable 标注 | AiChunkController.java:34-91 | 浅模块：纯转发 + 权限注解；建议保持薄，勿加业务判断 |

## 2. Interface Contracts

> 深模块契约正式版见 contract/interfaces.md `## ai-chunking` 节；此处为设计视角的公共接口面。SSE 是**系统边界**（前端以原生 fetch + ReadableStream 直连，绕过 axios 拦截层，AiChunkController.java:41 javadoc ‹CERTAIN›）——SseEmitter 事件协议属边界协议而非内部接口。

**AiChunkWorkflowModule**（公共接口）
- `analyze(kbId, fileId, withChunks)` → AiChunkResult：同步全量（SSE 兜底；withChunks=false 不触发 LLM/Embedding）
- `stream(kbId, fileId)` → SseEmitter：流式事件流（progress 8/35/55 → fragment×N → chunk×N → done；error 收尾）
- Hidden：线程池调度、阶段进度映射、fragment 截断（400 字符）
- HTTP 暴露面：GET `/api/kb/{kbId}/files/{id}/ai-chunk/stream`、POST `/{id}/ai-chunk-preview`（AiChunkController.java:43,60 ‹CERTAIN›）

**ParagraphModelAssembler**（公共接口）
- `buildParagraphs(parseResult, extension)` → 段落序列（DocNode 优先/纯文本 PAGE_BREAK 回退；空产物报错）
- `mergeCrossPage(paragraphs)` → 就地合并（段落/表格/代码三类型；重排 id/order/pages/crossPage）
- `fillPdfRects(file, paragraphs)` → imageBoxes（旁路返回；副作用回填 rects 与 anchorText）
- Hidden：标题栈维护、headingPath 拼接、行盒→段落锚定两指针算法、孤儿行吸收、兜底横带分带

**AutoChunkStrategy**（公共接口）
- `autoChunk(kb, file, paragraphs)` → AutoChunkOutcome{groups, source}（source ∈ llm/embedding/rule）
- Hidden：双约束装箱、衔接上下文注入、LLM 输出三重校验、余弦相似度边界判定、句界/行界二次切分、overlap 尾片构造、迷你组合并

**RegionExtractor**（公共接口）
- `ocrImageRegion(kbId, fileId, page, x, y, width, height)` → {blocks:[{type, text, imageKey?}]}
- Hidden：三级路径路由、视觉空间换算（/Rotate）、行命中过滤（40% 覆盖/页眉脚带/标题起点截断）、图片相交判定（30%/85%）、OCR 增强重试序列

**LayoutAnalysisOrchestrator**（公共接口）
- `streamLayout(kbId, fileId)` → SseEmitter（layout 逐页/done）
- Hidden：文字层判层（200 字符阈值）、几何/VLM 路由、并发 5 信号量、缓存读写与版本失效、页眉脚行清理

**ApplyOrchestrator**（公共接口）
- `apply(kbId, fileId, request)` → 落库分片数
- Hidden：五重准入、进程内互斥、定向向量删除、图谱清空与 manual 重置、parsed.md/json 写入、chunkCount 对账

## 3. Seam Diagram

**缝 1：AI 能力网关（真缝）**
- What varies：LLM/Embedding/OCR/VLM 的供应商与引擎（模型表动态路由 + engine 参数）
- Adapters：fastrag-ai 的 LlmService.chatWithTimeout、EmbeddingService.embed、OcrService.recognize、LayoutAnalysisService.analyzePage（Ollama 兼容网关，多引擎配置驱动 ai.layout.engines.*）
- Stable side：本特性的分组算法与三级降级决策（只认「文本进→文本/向量出」）
- Placement：fastrag-ai 层（模型地址/密钥查 model 表的惯例在 caller 侧，AiChunkServiceImpl.resolveRecord:1582-1592 即消费例）——符合 conventions.md §3

**缝 2：解析产物形态（真缝）**
- What varies：DocumentParser 产出 DocNode 结构树（PDF/DOCX/PPTX/XLSX）或纯文本 + PAGE_BREAK 标记（文本类回退）
- Adapters：DocNodeAssembler（buildParagraphs 主分支）↔ PlainTextAssembler（:947-972 回退分支）
- Stable side：段落对齐模型（两分支产出同一 AiChunkParagraph 形态）
- Placement：ParagraphModelAssembler 内部——上游 file-ingestion.DocumentParser 契约已保证「恒为合法 ParseResult 或统一错误」（contract `## file-ingestion` 节），形态分叉在本侧消化

**缝 3：自动分组策略族（真缝）**
- What varies：分组信号源（LLM 语义 / Embedding 余弦 / 纯长度）
- Adapters：llmGroup / embeddingGroup / ruleGroup 三个可互换实现
- Stable side：finishAuto 收尾（chunkLength 硬上限、表格原子、迷你组回收、统一编号）——无论哪级胜出，产出不变式一致
- Placement：AutoChunkStrategy 内部；降级序由模型配置存在性决定（kb.graphLlmModel → kb.embeddingModel → rule）

**系统边界（非缝）：SSE 事件流** — 前端 fetch 直连消费（绕过 axios），事件协议是前后端契约面而非后端内部缝；事件协议演进需前后端同步（上游 onlyoffice-editing 链的接口面已注明同类直连方式）。

**与上游契约的衔接（不重复立缝）**：解析归 file-ingestion.DocumentParser；落库写面归 chunk-management.ChunkStoreService（本特性为单层模式消费方）；图谱清理经 GraphService.deleteFileGraph（knowledge-graph 域服务）。

## 4. Deepening Opportunities

- **ParagraphModelAssembler**：可继续吞掉 RegionExtractor 路径①的「行命中→组块」逻辑（:283-352 与 assemblePageNodes/collectText 三处各自实现行距聚类启发式，阈值略异 1.6×/2.0×——同族算法三写是当前最大的内聚债务）；以及 LayoutAnalysisOrchestrator.extractPageLines 与 DocumentParserImpl.extractGeometryLines 的行盒提取双实现（前者含 ascent/descent 精确行盒，后者已统一 PageLine 结构）。
- **ApplyOrchestrator**：parsed.json 目前仅审计（分片清单 + paragraphIds），可升格为段落对齐模型的持久化快照（重复打开工作台免重解析）；进程内互斥（APPLYING_FILES）可与 chunk-management 的文件状态机（processing 态准入）合并为统一文件级操作守卫。
- **AutoChunkStrategy**：None——三级降级 + 统一收尾已闭合。
- **LayoutAnalysisOrchestrator**：缓存失效目前仅按引擎版本号，可吸收「文件 updatedAt 参与缓存键」（当前注释口径「文件不变即有效」靠人工保证‹CERTAIN（2026-09-24 深证）：layoutCacheKey 仅拼接 kbId/fileId（AiChunkServiceImpl.java:744-746），而 replaceOriginalFile 原地覆盖同 objectKey 仅更新 updatedAt（FileServiceImpl.java:294,303,309）——原件被 OnlyOffice 替换后旧缓存必然仍命中，属已登记债务（contract ai-chunking.LayoutAnalysisOrchestrator 已知偏离）›）。

## 5. Parent Trace

| Design 模块/缝 | Spec REQ |
|---|---|
| AiChunkWorkflowModule | REQ-A01、A06、F01、F02、F03 |
| ParagraphModelAssembler（缝 2） | REQ-A02、A03、A04、A05、B01~B05 |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C01~C08 |
| RegionExtractor（缝 1） | REQ-D01、D02、D03、D07 |
| LayoutAnalysisOrchestrator（缝 1） | REQ-D04、D05、D06 |
| ApplyOrchestrator | REQ-E01~E06 |
| NFR 映射 | NFR-1/2→Workflow/Layout（SSE 超时与并发）、NFR-3→Apply/Region/Layout 降级面、NFR-4→Apply 互斥、NFR-6→Layout 缓存 |
| AiChunkWorkflowModule | REQ-A06（2026-09-24 review 补锚：解析产物为空时以业务错误终止（「文档解析结果为空」），不产出空分片集） |
| AiChunkWorkflowModule | REQ-F03（2026-09-24 review 补锚：SSE 由前端原生 fetch 直连；viewer 可发起分析/预览/版面/框选 OCR，仅「应用落库」要求 editor 权限并记审计） |
| ParagraphModelAssembler（缝 2） | REQ-A03（2026-09-24 review 补锚：行序列聚合为段落粒度节点——正文换行行合并为段，标题行判定须带几何/编号证据防「一行一框」退化） |
| ParagraphModelAssembler（缝 2） | REQ-A05（2026-09-24 review 补锚：标题与小节起点不得被跨页回并（保守策略）） |
| ParagraphModelAssembler（缝 2） | REQ-B02（2026-09-24 review 补锚：PDF 每段回填归一化 rects——行盒内容锚定，失败按「上/下锚点区间×预估行数比例」兜底分带，页眉脚先清理） |
| ParagraphModelAssembler（缝 2） | REQ-B03（2026-09-24 review 补锚：锚定成功段落同时产出锚定行文本 anchorText，作为手动分片的内容源） |
| ParagraphModelAssembler（缝 2） | REQ-B04（2026-09-24 review 补锚：PDF 内容图片以 imageBoxes 旁路返回真实渲染位置盒，不参与段落/分片对齐模型） |
| ParagraphModelAssembler（缝 2） | REQ-B05（2026-09-24 review 补锚：跨页合并块在每个出现页各保留一个 rects 锚点且共享同一段落 id） |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C02（2026-09-24 review 补锚：LLM 分组分批执行（段数≤60 且字符≤9000 双约束装箱+衔接参考），输出须完整覆盖、升序、无重复否则判定无效走回退） |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C03（2026-09-24 review 补锚：chunkLength 与语义阈值取自解析策略配置（StrategyConfigResolver 三层合并），算法自身不读策略表） |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C04（2026-09-24 review 补锚：chunkLength 为硬上限——超限组按段界二次切分（片段 id 加 _sN 后缀可追溯），表格原子不可切，标题/图片原子） |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C05（2026-09-24 review 补锚：回退路径长度断片携带上一片尾部 overlap（默认 100 字符、回退至句界），语义断点处不重叠） |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C06（2026-09-24 review 补锚：低于迷你组下限（300 字符与 chunkLength 取小）的纯文本组优先向后合并、装不下并入前一组；表格/代码/图片组保持独立） |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C07（2026-09-24 review 补锚：分片完成后段落↔分片双向引用回填完整——分片持 paragraphIds、段落持 chunkId（衍生片回填基段）） |
| AutoChunkStrategy（缝 1、缝 3） | REQ-C08（2026-09-24 review 补锚：手动分片内容源优先取锚定行文本（无 rects 段落回退解析文本）；应用前用户对分片文本的编辑不落库） |
| RegionExtractor（缝 1） | REQ-D02（2026-09-24 review 补锚：框选坐标与裁剪感知页面旋转（/Rotate 90/270）与 cropBox 基准，避免裁剪错位） |
| RegionExtractor（缝 1） | REQ-D03（2026-09-24 review 补锚：裁剪原图上传 MinIO 供分片卡渲染（失败仅缺图不阻断）；OCR 全空时返回「区域截图+占位说明」） |
| LayoutAnalysisOrchestrator（缝 1） | REQ-D06（2026-09-24 review 补锚：几何版面分类输出与 VLM 统一坐标系的类型化块（目录段归 text 整框），表格边缘漏行吸收进相邻表格块） |
| ApplyOrchestrator | REQ-E02（2026-09-24 review 补锚：应用做文件级互斥——同一文件并发二次 apply 以业务错误拒绝，防交错执行） |
| ApplyOrchestrator | REQ-E05（2026-09-24 review 补锚：应用成功写 MinIO 派生产物 parsed.md/parsed.json（写失败降级记账不阻断），并置 completed/progress=100、chunk_count=新 auto+保留 manual） |
| ApplyOrchestrator | REQ-E06（2026-09-24 review 补锚：应用前文件图谱整体清空（deleteFileGraph），不残留旧分片 mention） |

**ADR 要点核对**：ADR-0001（解析策略绑定变更必须触发重新分片）——本特性不触碰 parse_strategy_id，应用落库复用「删 auto 留 manual」替换口径但**不走**重切流水线，两者在「绑定-结果一致」不变式上不冲突：AI分片的「结果」由用户确认边界定义，策略绑定仅在下次 Re-chunk 时重新生效（CONTEXT.md AI分片 词条即此裁决，spec §2 排除项）。ADR-0002（图存储收敛 Neo4j）——本特性对图谱只做清理（deleteFileGraph）与 graphIndexed 重置，无图写入面，符合。

## 6. ADR

无新增——本链没有出现「难逆转 + 费解 + 真权衡」三者齐备的新决策；「以用户确认边界为唯一事实源替换落库」已由 CONTEXT.md 术语词条承载，不另立 ADR。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
