# AI 分片（ai-chunking）— 需求规格

## 1. 特性与范围

AI分片 是知识库文件的一种交互式加工工作流：用户对已完成上传的文件打开分片工作台后，后端将原件解析（含扫描页 OCR 兜底）为**段落对齐模型**，执行**结构级跨页合并**消除页边界割裂，再由 LLM（Embedding/规则回退）给出自动分片建议；用户在前端逐片审阅、编辑、手动框选增删，最后以**用户确认的分片边界为唯一事实源**应用落库替换既有分片。全程不重新解析原文件、不重新切分（区别于「重新分片 Re-chunk」，后者按策略绑定重跑解析→分片→向量化流水线，会冲掉跨页合并与手工边界——CONTEXT.md 两词条的区分即本特性的存在理由）。显式排除：不修改解析策略绑定、不触发摄取流水线、不提供批量文件操作。

## 2. Out of Scope

- **重新分片（Re-chunk）**：按策略绑定重跑「解析→切分→向量化」的物理重切归 chunk-management.RechunkOrchestrator（contract `## chunk-management` 节）；本特性的应用落库**不复用**该流水线，也不读取解析策略的切分算法配置（仅读取长度/阈值参数）。
- **解析策略绑定变更**：AI分片不改变 kb_file.parse_strategy_id，不做换绑校验（归 parse-strategy.ParseStrategyBindingGuard）。
- **OnlyOffice 文档编辑与保存回调**：在线编辑、forcesave、callback 编排归 onlyoffice-editing 链；本特性只消费「选区桥插件框选的坐标」这一输入手段。
- **QA 模式文件**：processing_mode=qa 的文件不参与 AI分片。
- **批量文件 AI分片 / 定时任务**：单文件交互式工作流，无异步队列消费面。
- **kb_compliance_rule 合规校验**：死配置（见 data.md `## parse-strategy` 节），不进任何功能契约。
- **原图 OCR 之后的图谱抽取联动**：应用落库仅做图谱数据清理与 graphIndexed 重置，增量重建由 knowledge-graph 链消费。

## 3. 领域术语

引用 CONTEXT.md：**AI分片**、**段落对齐模型**（「Markdown 是其序列化，Chunk 是其切分结果」在本特性中具体化为：段落对齐模型是预览/分片/应用三阶段的单一事实源）、**结构级跨页合并**、**知识分片（Chunk）**、**解析策略（Parse Strategy）**（仅消费其 chunk 参数层）、**重新分片（Re-chunk）**（对照概念）、SSE（技术术语）。

## 4. 功能需求

### 4.1 OCR 解析与结构级跨页合并

把 MinIO 原件翻译为结构化段落序列，并修复页边界对逻辑块的割裂——这是「AI分片结果与原件视觉结构一致」的前提。

- **REQ-A01**：预览/流式分析须从 MinIO 下载原件并经 DocumentParser 解析（策略参数传 null 以跳过 LLM Markdown 增强），解析失败以业务错误终止（提示「文档解析失败」）。〔证据：AiChunkServiceImpl.java:889-898 ‹CERTAIN›〕
- **REQ-A02**：PDF 扫描页（文字 <50 字符）须渲染走 OCR 兜底并入正文，OCR 文本与几何文本两条链路各自完成页眉脚/页码清理。〔证据：DocumentParserImpl.java:148-202 ‹CERTAIN›；OcrNoiseFilter 仅清洗嵌入图片 OCR，扫描整页 OCR 豁免，见 contract `## file-ingestion` 节 ‹CERTAIN›〕
- **REQ-A03**：段落装配须把行序列聚合为段落粒度节点：正文换行行合并为一段；标题行判定须带几何/编号证据（显式编号或行高 ≥ 页内中位数×1.12），防止「一行一框」退化。〔证据：DocumentParserImpl.java:377-499、638-643；PdfParagraphAssemblyTest.java:38-87 ‹CERTAIN›〕
- **REQ-A04**：结构级跨页合并须就地修复三类割裂——段落（前段页尾句未终止且后段非标题/列表形态）、表格（相邻页、列数一致，表头重复时丢弃续表表头；续表仅剩表头不并）、代码块（相邻页直接行拼接）；合并后重排 order/id 并置 crossPage 标记与多页 pages。〔证据：AiChunkServiceImpl.java:1028-1110 ‹CERTAIN›〕
- **REQ-A05**：标题与小节起点不得被跨页回并（保守策略）。〔证据：AiChunkServiceImpl.java:1026,1068 tryMergeText 对 `#`/`-`/`•` 开头拒绝 ‹CERTAIN›〕
- **REQ-A06**：解析产物为空（无任何段落）时以业务错误终止（「文档解析结果为空，无法 AI分片」），不得产出空分片集。〔证据：AiChunkServiceImpl.java:974-976 ‹CERTAIN›〕

### 4.2 段落对齐模型

段落对齐模型是预览左侧原件片段、右侧分片卡、应用落库三方的单一事实源（CONTEXT.md 词条），其完整性与坐标精度直接决定「所见即所得」。

- **REQ-B01**：模型每段须含稳定唯一 id（p_序号，跨页合并后重排）、文档顺序、类型（paragraph/heading/table/code/list/image）、文本、页码、页码集合、页码范围串、章节路径 headingPath。〔证据：AiChunkParagraph.java:16-73、AiChunkServiceImpl.java:980-1000 ‹CERTAIN›〕
- **REQ-B02**：PDF 文件须为每段回填归一化（0~1 顶左原点）文字块坐标 rects：行盒经内容锚定（两指针模糊行匹配）分配给段落，锚定失败的连续段落按「上/下锚点区间 × 预估行数比例」兜底分带；页眉脚行须先于锚定清理。〔证据：AiChunkServiceImpl.java:1623-1764、1775-1857 ‹CERTAIN›〕
- **REQ-B03**：锚定成功的段落须同时产出锚定行文本 anchorText（所见即所得的内容源，手动分片优先用它替代解析全文）。〔证据：AiChunkParagraph.java:59-64、AiChunkServiceImpl.java:1690-1699 ‹CERTAIN›〕
- **REQ-B04**：PDF 内容图片须以旁路通道（imageBoxes）返回真实渲染位置盒，不参与段落/分片对齐模型。〔证据：AiChunkServiceImpl.java:1618-1621,1747-1758 ‹CERTAIN›〕
- **REQ-B05**：跨页合并块在每个出现页各保留一个 rects 锚点，前端按页渲染时共享同一段落 id。〔证据：AiChunkParagraph.java:11-12、AiChunkServiceImpl.java:1640-1648 ‹CERTAIN›〕

### 4.3 自动与手动分片

自动分片给出建议边界（LLM 主路径、Embedding 相似度回退、长度累积兜底），手动分片由用户在预览上框选/编辑产生；两者在应用前都只是「建议」，唯一事实源是用户确认的边界。

- **REQ-C01**：自动分片须按三级降级执行：知识库图谱 LLM 模型分组 → 知识库 Embedding 模型相邻段落余弦相似度分组 → 长度累积兜底；每级失败降下一级并记录实际采用路径（llm/embedding/rule）供前端提示降级。〔证据：AiChunkServiceImpl.java:1124-1151、AiChunkResult.java:39-40 ‹CERTAIN›〕
- **REQ-C02**：LLM 分组须分批执行（双约束装箱：段数 ≤60 且截断后总字符 ≤9000），附下一批开头段落作语义衔接参考；输出必须完整覆盖批内全部段落编号、升序、无重复，否则判定无效走回退。〔证据：AiChunkServiceImpl.java:116-118,1190-1198,1240-1273 ‹CERTAIN›〕
- **REQ-C03**：分片长度参数（chunkLength）与语义阈值须取自解析策略配置（经 StrategyConfigResolver 三层合并），算法自身不读策略表。〔证据：AiChunkServiceImpl.java:1125-1127 ‹CERTAIN›〕
- **REQ-C04**：chunkLength 为硬上限：超限组按段界二次切分（正文/列表按句界、代码按行界，片段 id 加 _sN 后缀可追溯）；**表格为原子不可切**（超长保持完整允许超限）；标题/图片原子。〔证据：AiChunkServiceImpl.java:1407-1460,1444-1453 注释覆盖设计文档 §6.3 ‹CERTAIN›〕
- **REQ-C05**：回退路径（embedding/rule）长度断片须携带上一片尾部 overlap（默认 100 字符、回退至句界、有效重叠 <20 字符放弃）；语义断点处不重叠。〔证据：AiChunkServiceImpl.java:121-122,1324-1330,1367-1396 ‹CERTAIN›〕
- **REQ-C06**：低于迷你组下限（300 字符与 chunkLength 取小）的纯文本组须优先向后合并、装不下并入前一组；表格/代码/图片组保持独立。〔证据：AiChunkServiceImpl.java:119-120,1512-1545 ‹CERTAIN›〕
- **REQ-C07**：自动分片完成后须把段落与分片的双向引用回填完整：分片持 paragraphIds，段落持 chunkId（_sN/_oN 衍生片回填基段）。〔证据：AiChunkServiceImpl.java:1547-1576 ‹CERTAIN›〕
- **REQ-C08**：手动分片的分片内容源优先取锚定行文本（无 rects 的段落回退解析文本）；用户对分片文本的编辑在应用前不落库。〔证据：AiChunkParagraph.java:59-64 注释、AiChunkApplyRequest.Item.text「可能经用户编辑」AiChunkApplyRequest.java:22-23 ‹CERTAIN›〕

### 4.4 区域框选输入

用户直接在原件渲染图上框选区域提取内容（跨页拖拽、图片直达分片的输入手段），以及原件版面分块画框的可视化数据源。

- **REQ-D01**：区域内容提取须三级尽力：①文字层行命中（行中心在区域内、水平覆盖 ≥40% 行宽、页眉脚边缘带排除）→ 结构化文本块（零 OCR）；②区域内内容图片（相交 ≥30% 图片面积）→ 按完整图框裁剪原图直达分片并附一次 OCR 图注，扫描底图（≥85% 页面）跳过；③两者皆无（纯图形/扫描页）→ 整区域渲染多级重试 OCR（220 原图 → 300 原图 → 300 反相 → 300 红章增强）。〔证据：AiChunkServiceImpl.java:283-352,356-403,411-456 ‹CERTAIN›〕
- **REQ-D02**：框选坐标与裁剪须感知页面旋转（/Rotate 90/270 视觉宽高交换）与 cropBox 基准，避免裁剪错位。〔证据：AiChunkServiceImpl.java:260-277,504-525 ‹CERTAIN›〕
- **REQ-D03**：路径②裁剪原图须上传 MinIO `{kbId}/{fileId}/images/` 供分片卡渲染，上传失败仅缺图不阻断文本块；路径③ OCR 全空时须返回「区域截图 + 占位说明」而非空手而归。〔证据：AiChunkServiceImpl.java:382-388,448-455,490-502 ‹CERTAIN›〕
- **REQ-D04**：版面分析（原件画框数据源）仅支持 PDF：有文字层的页走 PDFBox 几何聚类（零 VLM 成本），文字 <200 字符的扫描/图片页渲染 150 DPI PNG 走 VLM 版面分析（并发 5，单页失败跳过计数）。〔证据：AiChunkServiceImpl.java:110,601-661,664-709、PdfLayoutAnalyzer.java:14-27 ‹CERTAIN›〕
- **REQ-D05**：版面分析结果缓存于 MinIO `{kbId}/{fileId}/layout.json`（带引擎版本号），文件不变即有效；版本不符/部分页失败不落盘，下次重算；非 PDF 立即返回 unsupported 终态。〔证据：AiChunkServiceImpl.java:113,583-599,711-719,749-764 ‹CERTAIN›〕
- **REQ-D06**：几何版面分类须输出与 VLM 统一坐标系的类型化块（title/table/code/image/caption/text，目录段归 text 整框），表格边缘漏行吸收进相邻表格块。〔证据：PdfLayoutAnalyzer.java:139-201,443-500,504-521 ‹CERTAIN›〕
- **REQ-D07**：区域框选支持范围 = **仅 PDF**（双端实证）：前端 overlay 为 pdf.js 专用组件（AiChunkPdfOverlay.vue:3「pdf.js 渲染 + 结构块点选 + 原生文字选区 + 图片框点选 + 区域框选」、:519-533 动态加载 pdfjs-dist）；后端 ocrImageRegion 直接按 PDF 加载（AiChunkServiceImpl.java:256 Loader.loadPDF），DOCX 等 Office 格式无框选路径（其在线编辑场景的选区桥归 onlyoffice-editing）。‹CERTAIN 补证（2026-09-24 深证）：AiChunkServiceImpl.java:256、AiChunkPdfOverlay.vue:3,519-533›

### 4.5 应用落库替换

用户点击「应用」后，以请求体里的分片列表为唯一事实源替换落库——这是 AI分片 与 Re-chunk 的分水岭：不重新解析、不重新切分。

- **REQ-E01**：应用须先做状态准入：文件存在、不在回收站、不在 processing、非 QA 模式、分片列表非空且无空白内容分片，违反即业务错误拒绝。〔证据：AiChunkServiceImpl.java:783-800 ‹CERTAIN›〕
- **REQ-E02**：应用须做文件级互斥：同一文件并发 apply 第二次请求以业务错误拒绝（提示勿重复提交），防交错执行撞主键/污染向量与图谱。〔证据：AiChunkServiceImpl.java:766-780 ‹CERTAIN›〕
- **REQ-E03**：替换口径与 Re-chunk 同款「删 auto 留 manual」：删除 origin≠manual 的分片行并按 embeddingId 定向删 Milvus 向量（Milvus/图谱清理失败降级记账继续），保留 manual 手工分片并重置其 graphIndexed=0/extraction_result=null 随增量构建重建。〔证据：AiChunkServiceImpl.java:802-841；对照 FileServiceImpl.java:421-449 ‹CERTAIN›〕
- **REQ-E04**：请求分片即最终分片：按请求顺序生成 id `{fileId}_chunk_{i}`、chunkType=text，直接向量化落库（storeChunks 单层模式），不调用任何分片器、不重新解析原文件。〔证据：AiChunkServiceImpl.java:843-855、AiChunkApplyRequest.java:10-12 ‹CERTAIN›〕
- **REQ-E05**：应用成功后须写 MinIO 派生产物 parsed.md（分片文本按序拼接）与 parsed.json（分片清单 + paragraphIds 映射审计），写失败降级记账不阻断；并把文件置 completed/progress=100、chunk_count=本次落库数 + 保留 manual 数（排除父分片）。〔证据：AiChunkServiceImpl.java:857-874,1986-2016 ‹CERTAIN›〕
- **REQ-E06**：应用前文件图谱须整体清空（deleteFileGraph），与分片替换保持一致，不残留旧分片 mention。〔证据：AiChunkServiceImpl.java:826-830 ‹CERTAIN›〕

### 4.6 交互通道（SSE 流式与同步兜底）

- **REQ-F01**：须提供 SSE 流式分析（progress/fragment/chunk/done/error 事件）：解析与 LLM 在独立线程池执行不占请求线程，跨页合并块在每页各推一个 fragment（共享 paragraphId），分片卡逐个推送；异常以 error 事件收尾。〔证据：AiChunkController.java:41-46、AiChunkServiceImpl.java:103-104,180-240 ‹CERTAIN›〕
- **REQ-F02**：须提供同步预览兜底（SSE 不可用时一次返回全量）；chunk=false 时仅返回段落对齐模型不触发 LLM/Embedding。〔证据：AiChunkController.java:58-64、AiChunkServiceImpl.java:153-160 ‹CERTAIN›〕
- **REQ-F03**：SSE 端点由前端以原生 fetch + ReadableStream 直连消费（绕过 axios 拦截层）；viewer 权限即可发起分析/预览/版面/框选 OCR，仅「应用落库」要求 editor 权限并记审计日志。〔证据：AiChunkController.java:41,42,52,59,67-68,80 javadoc 与注解 ‹CERTAIN›；「上游文档有记载」为任务书给定口径〕

## 5. 非功能约束

- **NFR-1（响应时限，可证）**：SSE 连接超时 600 秒；LLM 分组单批调用超时 60 秒。〔证据：AiChunkServiceImpl.java:182,1226 ‹CERTAIN›〕
- **NFR-2（并发，可证）**：扫描页 VLM 版面分析并发上限 5（信号量 + 固定线程池）；SSE 分析/版面走独立 cachedThreadPool。〔证据：AiChunkServiceImpl.java:104,665-666 ‹CERTAIN›〕
- **NFR-3（降级完整性，可证）**：Milvus 清理、图谱清理、manual graphIndexed 重置、parsed 产物写入、区域图片上传、单页版面失败——全部降级记账（warn）继续主流程，不回滚不中断。〔证据：AiChunkServiceImpl.java:820-841,498-501,2013-2015,658-660,694-697 ‹CERTAIN›〕
- **NFR-4（幂等/互斥，可证）**：apply 文件级互斥（进程内 ConcurrentHashMap）；无跨实例互斥与持久化锁。〔证据 ‹CERTAIN（2026-09-24 深证：AiChunkServiceImpl.java:768 static 进程内 ConcurrentHashMap、:772 putIfAbsent/:778 finally remove，无任何跨实例协调手段——多实例下互斥失效为结构直读事实；单实例互斥为有意设计，:766-767 注释载明并发 apply 撞主键/污染向量图谱的实证事故）〕
- **NFR-5（性能阈值）**：〔决议 2026-09-24：取——有文字层 PDF 预览 P95 ≤ 3s（秒级文本提取，前端既有口径「秒级文本提取，照旧立即加载」AiChunkPdfOverlay.vue:72）；扫描件/千页级版面分析不设整本硬时限，以 SSE 逐页 progress/fragment 流式反馈为首责（页级并发 5、单页失败跳过），SSE 连接上限 600s（NFR-1）即事实天花板。理由：业界惯例（首反馈秒级 + 长任务流式进度）+ 与现有 SSE 机制一致；现状差距：无埋点与阈值断言，登记债务——随测试补齐建立（代裁，用户可推翻）〕
- **NFR-6（成本控制，可证）**：版面分析每文件至多生成一次（缓存）；有文字层页零 VLM 调用。〔证据：AiChunkServiceImpl.java:572-574 注释,601-603 ‹CERTAIN›〕

## 6. 验收清单

- [ ] 扫描件 PDF（无文字层）预览能产出非空段落对齐模型（OCR 兜底生效）？（REQ-A02）
- [ ] 一段正文被页边界切断（页尾无句末标点）时，合并为一个 crossPage 段落且 pages 含两页？（REQ-A04）
- [ ] 跨页表格（列数一致）合并后表头唯一、续表表头行不重复出现？（REQ-A04）
- [ ] 中文正文的无标点换行行不会被逐行判为标题（段落粒度不退化为行）？（REQ-A03）
- [ ] LLM 分组输出缺失任一编号/重复/乱序时，自动回退 embedding 或 rule 且 chunkSource 如实标注？（REQ-C01、REQ-C02）
- [ ] chunkLength 硬上限下超长正文组被按句界二次切分且片段 id 带 _sN 后缀？（REQ-C04）
- [ ] 表格组在超长时保持完整（不被二次切分）？（REQ-C04）
- [ ] 段落 rects 锚定失败时按兜底横带返回坐标而非缺失（PDF 预览框不丢）？（REQ-B02）
- [ ] /Rotate 90 的 PDF 页上框选区域 OCR 返回正确区域文字（不裁错位）？（REQ-D02）
- [ ] 应用后：旧 auto 分片与向量被删、manual 分片行与向量原样保留、chunk_count=新 auto+manual？（REQ-E03、REQ-E05）
- [ ] 应用请求不含任何分片器调用证据（parsed.json 的分片 id 与请求顺序一一对应）？（REQ-E04）
- [ ] 同一文件并发二次 apply 第二次被拒绝且首次执行不受影响？（REQ-E02）
- [ ] processing 中 / 回收站 / QA 模式文件的应用请求被业务错误拒绝？（REQ-E01）
- [ ] 应用后 parsed.md 内容等于分片文本按序 "\n\n" 拼接？（REQ-E05）
- [ ] 非 PDF 文件请求版面分析立即收到 unsupported 的 done 终态（不报错）？（REQ-D05）
- [ ] 版面缓存版本号变更后旧缓存失效重算？（REQ-D05）
- [ ] 解析失败时以业务错误终止并提示「文档解析失败」（REQ-A01）
- [ ] 标题与小节起点不被跨页回并（保守策略）（REQ-A05）
- [ ] 解析产物为空时以业务错误终止、不产出空分片集（REQ-A06）
- [ ] 段落模型每段含稳定唯一 id/类型/文本/页码/页码集合/headingPath 等完整字段（REQ-B01）
- [ ] 锚定成功的段落同时产出 anchorText（所见即所得内容源）（REQ-B03）
- [ ] PDF 内容图片经 imageBoxes 旁路返回真实渲染位置盒、不进入段落/分片对齐模型（REQ-B04）
- [ ] 跨页合并块在每个出现页各保留一个 rects 锚点且共享同一段落 id（REQ-B05）
- [ ] 分片长度与语义阈值参数取自解析策略配置，算法自身不读策略表（REQ-C03）
- [ ] 回退路径断片携带上一片尾部 overlap（默认 100 字符、句界回退），语义断点处不重叠（REQ-C05）
- [ ] 低于迷你组下限的纯文本组优先向后合并、装不下并入前一组；表格/代码/图片组保持独立（REQ-C06）
- [ ] 自动分片完成后段落↔分片双向引用回填完整（分片持 paragraphIds、段落持 chunkId）（REQ-C07）
- [ ] 手动分片内容源优先取锚定行文本；应用前用户对分片文本的编辑不落库（REQ-C08）
- [ ] 区域框选三级尽力生效：文字层行命中直取文本块、内容图片裁剪原图+OCR 图注、整区域多级重试 OCR（REQ-D01）
- [ ] 裁剪原图上传 MinIO 失败仅缺图不阻断；OCR 全空时返回「区域截图+占位说明」（REQ-D03）
- [ ] 版面分析有文字层页零 VLM 成本，扫描页渲染 150 DPI 走 VLM（并发 5、单页失败跳过计数）（REQ-D04）
- [ ] 几何版面分类输出与 VLM 统一坐标系的类型化块，表格边缘漏行吸收进相邻表格块（REQ-D06）
- [ ] 非 PDF 格式的区域框选支持范围（后端仅 PDFBox 路径，GAP 登记）（REQ-D07；豁免：占位/债务登记，见 implement 文档）
- [ ] 应用成功后文件图谱整体清空（deleteFileGraph），不残留旧分片 mention（REQ-E06）
- [ ] SSE 流式分析推送 progress/fragment/chunk/done/error 事件，异常以 error 事件收尾（REQ-F01）
- [ ] 同步预览兜底可用；chunk=false 时仅返回段落对齐模型、不触发 LLM/Embedding（REQ-F02）
- [ ] viewer 可发起分析/预览/版面/框选 OCR，仅「应用落库」要求 editor 权限并记审计日志（REQ-F03）

## 7. 测试策略

经由公共接口（analyze/stream/apply/ocrImageRegion/streamLayout）验证最重要：

1. **跨页合并与替换落库幂等**是本特性两族核心用例（结构正确性 × 状态一致性）：纯函数级直测 mergeCrossPage 三类型合并与拒绝分支、doApply 删留口径与计数对账（mock Milvus/GraphStore/MinIO 系统边界）。
2. **段落装配几何回归**：以 PageLine 构造样本直测 assemblePageNodes/isPdfTitleRow/assignLineRanges（现有 PdfParagraphAssemblyTest、PdfRectAssignmentTest 即此模式），不依赖真实 PDF。
3. **降级链**：LLM 无效输出→embedding 失败→rule 兜底的逐级降级与 chunkSource 断言；各系统边界失败的降级记账（不抛错）。
4. **坐标感知**：旋转页/双栏/表格边缘吸收的几何聚类（现有 PdfLayoutAnalyzerTest 真实 PDFBox 端到端模式）。
5. HITL（需人决策）：分片边界的语义质量、框选交互体验、扫描件 OCR 识别率——自动化只断言结构不变式，语义质量留人工审阅。
