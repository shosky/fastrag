# AI 分片（ai-chunking）— 实现文档

## 1. Code File Map

```
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── controller/
│   └── AiChunkController.java          HTTP/SSE 暴露面（5 端点转发 + @KbAuth/@Loggable）
├── service/
│   ├── AiChunkService.java             服务接口（analyze/stream/streamLayout/apply/ocrImageRegion）
│   └── impl/
│       ├── AiChunkServiceImpl.java     全部编排实现（~2000 行：分析/合并/分组/框选/版面/应用）
│       └── PdfLayoutAnalyzer.java      PDF 几何版面分类器（纯函数、零 IO）
├── model/
│   ├── AiChunkParagraph.java           段落对齐模型单元（含 RectItem）
│   ├── AiChunkGroup.java               分片卡（自动分片产出）
│   ├── AiChunkResult.java              预览/SSE done 载荷（含 DocumentInfo）
│   ├── AiChunkLayoutBlock.java         版面分析块
│   └── AiChunkApplyRequest.java        应用请求体（含 Item）
└── parser/
    └── DocumentParserImpl.java         段落装配改进落点（extractGeometryLines/stripRowHeaderFooters/
                                        assemblePageNodes/isPdfTitleRow——共用行结构供 rects 锚定）
fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/
├── parser/PdfParagraphAssemblyTest.java            行→节点段落粒度回归（4 用例）
└── service/impl/
    ├── PdfRectAssignmentTest.java                  rects 内容锚定算法回归（6 用例）
    └── PdfLayoutAnalyzerTest.java                  几何聚类 + 真实 PDFBox 端到端（7 用例）
```

镜像说明：design 的 AiChunkWorkflowModule/ParagraphModelAssembler/AutoChunkStrategy/RegionExtractor/LayoutAnalysisOrchestrator/ApplyOrchestrator 六模块物理上全部内聚于 AiChunkServiceImpl（以私有方法簇分区，注释分节 ：132-134,877-879,1015-1018,1117-1120,1578-1581,1610-1613 ‹CERTAIN›）；PdfLayoutAnalyzer 独立成类为唯一例外。

## 2. Interface Implementations

### AiChunkWorkflowModule（from design: AiChunkWorkflowModule；HTTP/SSE 暴露面，端点排除不立契约，实现在此记录）

**Logic Flow（analyze(kbId,fileId,withChunks)）:** 1. requireFile（kbId+fileId 联查，null→notFound 业务错误）:881-887 2. parseFile：MinIO 下载全字节→documentParser.parse(bytes, extension, **null**)——null 策略使解析跳过 enhanceWithLlm :889-898 3. buildParagraphs→mergeCrossPage→fillPdfRects（PDF rects 副作用回填）4. readLayoutCache：命中则 layoutBlocks 随预览返回（秒开），未命中留给前端另走 SSE :149-151 5. IF withChunks THEN autoChunk→backfillChunkIds 6. RETURN AiChunkResult（pages=max(解析页数，段落最大页)、mergedBlocks=跨页段计数）:162-177。
**Logic Flow（stream）:** SseEmitter(600s) 提交 streamExecutor 线程 :182-183 → progress(8,parse)→解析→progress(35,merge)→装配+合并+rects → 逐段逐页 emit fragment（text 截断 400、跨页块每页一个共享 paragraphId）:197-208 → progress(55,llm-chunk)→autoChunk→backfill → 逐组 emit chunk → done{pages,mergedBlocks,chunks,chunkSource}→complete :210-226；任一步异常→error{message}（缺省「AI分片 解析失败」）→completeWithError :227-236。
**Module Interactions:** Calls ParagraphModelAssembler/AutoChunkStrategy（内部方法簇）/DocumentParser/MinioService；Called by AiChunkController（stream/preview 两端点）；Shared state streamExecutor（cachedThreadPool，无界线程，随请求累积‹CERTAIN（2026-09-24 深证）：:104 创建，仅 :183/:578 两处 execute，全文件无 shutdown/@PreDestroy——:707 的 pool.shutdown() 是逐次请求的局部版面分析池，非本字段；随应用生命周期共存为结构直读事实›:104）。
**Decisions & Deviations:** 与契约无偏离——同步与流式两出口共享同一流水线，事件协议仅此一处定义。
**Parent Trace:** AiChunkWorkflowModule（SSE 流+预览）→（HTTP 面）→ Spec REQ-F01~F03、A01、A06。

### ParagraphModelAssembler（from contract: ai-chunking.ParagraphModelAssembler）

**Logic Flow（buildParagraphs）:** 1. IF ParseResult.nodes 非空：遍历 DocNode（跳过 PAGE_BREAK），维护标题栈（弹出 level≥当前）——HEADING 入栈并产 heading 段、TABLE 产 tableText（表头+行 \t 拼接）、CODE/LIST/IMAGE/MATH 各映射类型、PARAGRAPH 默认分支跳空白 :903-945 2. ELSE 纯文本回退：PAGE_BREAK 正则切页→页内按空行分段→`#` 前缀判 heading :947-972 3. 空产物→badRequest :974-976。
**Logic Flow（mergeCrossPage）:** 遍历段落，cur.page>lastPage(prev) 时按 prev.type 分派 tryMergeText（尾字符∈终止符集→拒绝；cur 以 #/-/• 开头→拒绝；否则直拼）/tryMergeTable（列数不等→拒绝；同表头去续表表头、仅剩表头→拒绝；否则拼体）/tryMergeCode（无条件拼），合并失败或无前段则入列 :1029-1042 → 全序列重排 order/id、pages 升序去重、派生 page/pageRange/crossPage :1044-1055。
**Logic Flow（fillPdfRects）:** 非 PDF→空集 :1624-1626 → 逐页 extractGeometryLines（与解析端共用 PageLine 结构）+ stripRowHeaderFooters :1632-1639 → 按页聚合段落（跨页段每页各注册一次）:1641-1648 → assignLineRanges 两指针锚定（行归一化→找锚行→推进→孤儿行向前逐行匹配吸收→尾行按下一段首行防穿越）:1775-1834 → 命中段构建归一化 rect（单行高 10% 外扩）+ anchorText 回填 :1662-1699 → 连续未锚定段按上/下锚点区间×预估行数比例分兜底横带 :1701-1739 → 返回 extractContentImageBoxes 归一化盒 :1747-1758；任一异常→warn+空 imageBoxes :1760-1763。
**Module Interactions:** Calls DocumentParserImpl.extractGeometryLines/stripRowHeaderFooters/isPdfTitleLikeLine（跨类静态复用）、MediaExtractor.extractContentImageBoxes、MinioService；Called by AiChunkWorkflowModule；Shared state 无（paragraphs 就地变异是唯一状态通道）。
**Decisions & Deviations:** ① 锚定从「按行数顺序猜测」改为内容锚定（注释自述漂移根因，:1614-1618）；② 锚定失败兜底从逐段独立取全区间改为连续段比例分带（防堆叠同框，:1701-1702）；③ anchorText 让手动分片所见即所得（:1689-1691）；均与契约 Invariants 一致，无偏离。
**Parent Trace:** ParagraphModelAssembler → ai-chunking.ParagraphModelAssembler → Design.ParagraphModelAssembler → REQ-A02~A05、B01~B05。

### AutoChunkStrategy（from contract: ai-chunking.AutoChunkStrategy）

**Logic Flow（autoChunk）:** 1. configResolver.resolve(file.parseStrategyId).getChunk() 取 chunkLength/semanticThreshold（阈值 0~100 归一到 0~1）:1125-1127 2. IF kb.graphLlmModel 非空 THEN llmGroup，异常→warn 降级 3. IF kb.embeddingModel 非空 THEN embeddingGroup，异常→warn 降级 4. ELSE ruleGroup；每路经 finishAuto 收尾 :1130-1150。
**Logic Flow（llmGroup）:** WHILE 未消费完：双约束装箱（段数 ≤60、截断 200 字符计总 ≤9000）:1191-1198 → 构造编号清单 + 下一批两段衔接参考 → prompt（规则：1~8 段/300~chunkLength、表码不拆、标题随首段、JSON 输出）:1200-1224 → llmService.chatWithTimeout(model,prompt,apiUrl,apiKey,false,60) :1226 → parseGroups 三重校验（每编号∈[from,to] 且不重复、组内升序、全覆盖 seen==to-from+1、组间首元素严格递增）任一失败抛 IllegalStateException→触发降级 :1227-1228,1240-1273。
**Logic Flow（embeddingGroup/ruleGroup）:** 顺序扫段：embedding 路径余弦 <threshold 或累计超长→断点（长度断片附加 tailOverlapPiece：末段正文类、取尾 ≤100 字符回退句界、<20 字符放弃，id 加 _o1）:1287-1336,1367-1396；rule 路径仅长度断点 :1340-1361。
**Logic Flow（finishAuto）:** 超限组 splitList（不可切类型原样；可切按 splitByUnit——table/code 按行、其余按句界，衍生 id 加 _sN；再按段界装箱）:1161-1167,1407-1442 → mergeMiniLists（<min(300,chunkLength) 纯文本组先后并、装不下前并；表/码/图豁免）:1168,1512-1545 → 统一编号出组（text 按段 "\n\n" 拼接、pageRange 由组内 pages 集合派生）:1169-1174,1547-1560 → backfillChunkIds 双向回填（_sN/_oN 归并基段取最后映射）:1562-1576。
**Module Interactions:** Calls StrategyConfigResolver、LlmService.chatWithTimeout、EmbeddingService.embed（32/批）、ModelRecordMapper（code+status=online 查 apiUrl/apiKeyRef，异常 debug 返 null）；Called by AiChunkWorkflowModule；Shared state 无。
**Decisions & Deviations:** ① 表格原子不可切**有意覆盖**设计文档 §6.3「表格按行切分」（代码注释明示产品要求：单页表格必须完整呈现，:1444-1449）——契约已按实现收编（Invariants「表格原子不可切」）；② LLM 模型复用 kb.graphLlmModel（图谱 LLM 字段，无独立配置项——〔决议 2026-09-24：按现状为准——AI 分组 LLM 主路径直读 kb.graphLlmModel（AiChunkServiceImpl.java:1130），代码无注释阐明借用意图、意图不可考，现状即契约口径；与 GraphBuildConsumer 的「策略 LLM 优先、KB 级 graphLlmModel 兜底」相比，本处无策略级 LLM 概念，如需独立 aiChunk 模型配置另行立项（代裁，用户可推翻）〕）；③ resolveRecord 失败以 null 下传 apiUrl/apiKey 而非中断（:1582-1592）。
**Parent Trace:** AutoChunkStrategy → ai-chunking.AutoChunkStrategy → Design.AutoChunkStrategy → REQ-C01~C07。

### RegionExtractor（from contract: ai-chunking.RegionExtractor）

**Logic Flow:** 1. requireFile→MinIO 下载（失败→「文档下载失败」）:249-255 2. PDFBox 加载，页码越界/页面尺寸无效→badRequest :256-264 3. 旋转感知：/Rotate 90/270 视觉宽高交换，区域归一化→视觉 pt :266-277 4. 路径①：extractGeometryLines 行命中（中心入区+水平覆盖 ≥40%+页眉脚带 7%/93% 排除）→区域内首标题行截断（框选起点偏上不带上一节尾行）:283-304 →组块（连续 \t 行 ≥2→table；单行标题样→heading；行距 ≤1.6×行高聚段）:306-351 5. 路径②（rot=0 才走）：extractContentImageBoxes 中与本页相交 ≥30% 图片面积、非扫描底图（≥85% 页宽高）者→渲染 220 DPI 按完整图框裁剪→uploadRegionImage（失败返 null 跳过）→一次 OCR 填图注（空/失败保持纯图）:356-403 6. 块按 _y 纵坐标穿插排序（Number 取值防 ClassCastException）:405-409 7. 路径③（blocks 空）：渲染裁剪（5% 边距）→四连试 OCR（220 原图→300 原图→300 反相→300 红章增强 min(G,B) 灰度）→首个非空文本成 paragraph 块；全空且截图已传→「截图+占位说明」image 块 :411-455 8. RETURN {blocks}；BusinessException 穿透、其余包装「区域内容提取失败」:458-464。
**Module Interactions:** Calls MinioService（下载/上传）、OcrService.recognize、MediaExtractor.extractContentImageBoxes、DocumentParserImpl.extractGeometryLines/isPdfTitleLikeLine；Called by AiChunkController.image-ocr；Shared state 无。
**Decisions & Deviations:** ① 路径②命中即整框裁原图不做增强重试（内容图以视觉为主，:382-384 注释）；② 路径③ OCR 文字转正文 paragraph 块而非图片块（扫描件转文字）；③ 增强图仅送 OCR、展示/上传恒原图（:431-433）；④ `[ocr-dbg]` 诊断日志以 info 级常驻（:359,377,413,423——登记债务：应降 debug）。与契约 Invariants 一致。
**Parent Trace:** RegionExtractor → ai-chunking.RegionExtractor → Design.RegionExtractor → REQ-D01~D03、D07。

### LayoutAnalysisOrchestrator（from contract: ai-chunking.LayoutAnalysisOrchestrator）

**Logic Flow:** 1. requireFile→readLayoutCache：命中（v==3 且 blocks 数组）→一次 layout 全量+done{cached:true}→complete :580-592 2. 非 PDF→done{unsupported:true}→complete :593-599 3. 下载字节；第一遍逐页 extractPageLines 统计字符数：<200（TEXT_LAYER_CHARS）归 vlmPages，否则入 geoLines :621-635 → stripHeaderFooterLines 跨页清理 :637 → 第二遍几何页逐页 PdfLayoutAnalyzer.analyze（cropBox 归一化、图片盒按页过滤复用）→emitSync layout+progress :639-661（单页失败 warn 跳过）4. vlmPages：5 线程池+信号量并发 5，每线程独立 load PDF 副本渲染 150 DPI PNG→layoutAnalysisService.analyzePage→映射 AiChunkLayoutBlock→emitSync :664-708（单页失败 warn 跳过）5. 全部页成功且非空→写缓存 {v:3, blocks}（upload layout.json）:710-715 → done{pages,blocks}→complete；整体异常→error+completeWithError :716-731。
**extractPageLines（行盒版提取）:** PDFTextStripper 按基线差 >2pt 换行、同行 x 间隙 >max(6pt,1.5×字号) 拆行 :1901-1906；行盒=基线范围+字体 ascent/descent 展开（fontDescriptor 缺失回退 0.75em/0.2em）:1875-1893,1921-1937——与 DocumentParserImpl.extractGeometryLines（行盒按 0.8/0.25 字号张开 :307-312）为**同族双实现**（契约 Deepening 已登记内聚债务）。
**Module Interactions:** Calls MinioService、MediaExtractor.extractContentImageBoxes、LayoutAnalysisService.analyzePage（fastrag-ai VLM 网关）、PdfLayoutAnalyzer（纯函数）；Called by AiChunkController.layout；Shared state streamExecutor（与 stream 共享）、emitSync 的 emitter 级锁 :736-741。
**Decisions & Deviations:** ① 缓存只在全部页成功时落盘（部分失败留重试机会，:710-711）；② 几何/VLM 双链页眉脚清理各自执行（几何走 PdfLayoutAnalyzer.stripHeaderFooterLines，与解析侧 DocumentParserImpl.stripRowHeaderFooters 同规则双实现 :320 注释重复——登记债务）；③ 缓存键不含 updatedAt（原件替换后旧缓存命中——契约已登记偏离债务）。
**Parent Trace:** LayoutAnalysisOrchestrator → ai-chunking.LayoutAnalysisOrchestrator → Design.LayoutAnalysisOrchestrator → REQ-D04~D06。

### ApplyOrchestrator（from contract: ai-chunking.ApplyOrchestrator）

**Logic Flow（apply）:** 1. APPLYING_FILES.putIfAbsent(fileId) 命中→badRequest「正在应用…勿重复提交」:771-774 2. finally remove 互斥键 :777-779。
**Logic Flow（doApply）:** 1. requireFile；回收站（deletedAt≠null）/processing/qa→逐一 badRequest 拒绝 :783-792 2. 请求校验：chunks 空→「分片列表为空…」；任一 text 空白→「存在内容为空的分片…」:793-800 3. **删 auto 留 manual**：查 origin≠manual 行→收集 embeddingId→delete 同条件行 :804-816 → Milvus deleteByIds（collection=`kb_`+kbId 连字符转下划线；失败 warn）:818-825 → graphService.deleteFileGraph（失败 warn）:826-830 → 保留 manual 批量重置 graphIndexed=0/extraction_result=null（失败 warn）:831-841 4. **尊重边界**：请求 Item 顺序映射 ChunkData{id=fileId_chunk_i, index=i, content=text, pageRange, chunkType=text}→storageService.storeChunks 单层 :843-855 5. writeParsedArtifacts：parsed.md=Item.text 按 "\n\n" 拼接上传；parsed.json={fileId,kbId,fileName,appliedAt,chunks[{id,index,source(缺省 auto),pageRange?,paragraphIds[]}]}上传；任一失败 warn :857-858,1986-2015 6. 文件对账：status=completed/progress=100/stage=""/chunk_count=新 auto 数+manual 数（selectCount 排除 parent）/updatedAt=now→updateById :860-871 7. RETURN chunks.size() :874。
**Module Interactions:** Calls KbFileMapper/KbChunkMapper/MinioService/MilvusService/GraphService.deleteFileGraph/StorageService.storeChunks（chunk-management 写面契约）；Called by AiChunkController.apply（@Loggable 审计）；Shared state APPLYING_FILES（进程内 ConcurrentHashMap<String,Boolean>，无逐出需求——putIfAbsent/remove 成对 :768-779）。
**Decisions & Deviations:** ① **有意不走 reChunkFile**：注释明示「与 reChunkFile 相同模式，但之后不走 process 重解析」:802-803——删留口径复用、流水线不复用，这是特性分水岭（CONTEXT.md AI分片 vs Re-chunk 词条）；② 互斥为进程内存（多实例失效——契约已按 CERTAIN 深证登记债务，AiChunkServiceImpl.java:768 static Map 无跨实例协调；单实例实证必要性见 :766-767 事故注释）；③ 应用后不重置 parse 绑定、不发摄取消息、无父子装配（chunkType 恒 text）；④ manual 计数显式排除 parent 型（:868）与 ChunkLifecycleService 口径一致。
**Parent Trace:** ApplyOrchestrator → ai-chunking.ApplyOrchestrator → Design.ApplyOrchestrator → REQ-E01~E06。

## 3. Testing

对照 tdd/ai-chunking.md 切片（红绿循环未执行，本节记录**现有测试**覆盖面与缺口）：

- **已覆盖——S2 段落装配与锚定（先行切片）**：`PdfParagraphAssemblyTest`（4 用例：中文正文无标点换行行合并为段 / 大字号判标题 / 编号判标题 / 小数开头正文不误判——经反射直测 assemblePageNodes/isPdfTitleRow，PdfParagraphAssemblyTest.java:38-87）；`PdfRectAssignmentTest`（6 用例：精确对齐 / 跨页段后缀+前缀匹配 / 继承标题不占行 / 漂移自愈 / 空白模糊容错 / 短行不误锚——直测 AiChunkServiceImpl.assignLineRanges 静态算法）；`PdfLayoutAnalyzerTest`（7 用例：确定性几何聚类 + 真实 PDFBox 生成 PDF 的端到端分类——覆盖 S5 几何分类子集）。
- **缺口**：S1 跨页合并族（mergeCrossPage 三类型/拒绝分支零直测，仅经 PdfParagraphAssemblyTest 间接保证装配粒度）；S3 降级链（LLM 无效输出→embedding→rule 无用例）；S4 区域三级路径（旋转感知/相交阈值/重试序列无用例）；S5 缓存与分层路由（判层阈值/版本失效/并发 5 无用例）；S6 替换落库幂等（删留口径/互斥/对账无用例——**最大风险缺口**，历史事故仅以注释留痕 :766-767）；S7 事件序列（stream 协议无用例）。
- 补测纪律见 tdd §4：从 S6「替换删 auto 留 manual」与 S1「跨页拼接」两用例起步。
