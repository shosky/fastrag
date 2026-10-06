# AI 分片（ai-chunking）— 测试设计

> 契约源：contract/interfaces.md `## ai-chunking` 节五接口。mock 只允许在真缝/系统边界：fastrag-ai 四能力（Llm/Embedding/Ocr/VLM Layout）、MinIO、Milvus、GraphService（图存储边界）、时间；禁 mock 内部模块。现有测试（PdfParagraphAssemblyTest / PdfRectAssignmentTest / PdfLayoutAnalyzerTest）为本设计的几何切片先行实现，见 §4。

## 1. 切片规划

| 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|
| S1 跨页合并（**重点用例族**） | ParagraphModelAssembler | AFK | 纯函数级（构造段落序列直测），零 mock |
| S2 段落装配与 rects 锚定 | ParagraphModelAssembler | AFK | 几何回归（现有 PdfParagraphAssemblyTest/PdfRectAssignmentTest 模式） |
| S3 自动分组降级链 | AutoChunkStrategy | AFK | mock LLM/Embedding 边界 |
| S4 区域框选三级路径 | RegionExtractor | AFK + HITL（识别率） | mock MinIO/OcrService；语义质量留人 |
| S5 版面分析分层与缓存 | LayoutAnalysisOrchestrator | AFK | mock VLM/MinIO（现有 PdfLayoutAnalyzerTest 覆盖几何分类） |
| S6 替换落库幂等（**重点用例族**） | ApplyOrchestrator | AFK | mock MinIO/Milvus/GraphService/ChunkStoreService 写面边界 |
| S7 端到端工作流（SSE/预览） | AiChunkWorkflowModule | AFK + HITL（交互体验） | 经控制器断言事件序列；SSE 直连消费属前端，交互体验留人 |

## 2. 测试用例

### ParagraphModelAssembler（contract: ai-chunking.ParagraphModelAssembler）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 正文段页尾无句号跨页拼接为单段 | 段落 [p1(paragraph,页1,尾无终止符), p2(paragraph,页2,非 #/-/• 开头)] | mergeCrossPage | 合并为 1 段：text 直拼、pages=[1,2]、crossPage=true、pageRange="1-2"、id/order 重排连续 | A04、B01、B05 | 无 |
| 句已终止的段不跨页回并 | p1 尾字符为「。」 | mergeCrossPage | 两段保留、crossPage=false | A04、A05 | 无 |
| 下一页标题行阻断合并 | p1(paragraph,尾无终止符) + p2(heading,页2) | mergeCrossPage | heading 不被并、两段保留 | A05 | 无 |
| 跨页表格同列数并表且续表表头去重 | p1(table,首行"甲\t乙",页1) + p2(table,同表头,页2) | mergeCrossPage | 合并后首行表头唯一、行拼接；pages=[1,2] | A04 | 无 |
| 跨页表格列数不一致或续表仅剩表头不并 | 列数不同两表 / 续表仅表头行 | mergeCrossPage | 各自保留独立段 | A04 | 无 |
| 跨页代码块直接行拼 | 相邻页两 code 段 | mergeCrossPage | 1 段多行、crossPage=true | A04 | 无 |
| 空解析产物报错 | ParseResult 无 nodes 且 text 空 | buildParagraphs | 抛业务错误「文档解析结果为空」 | A06 | 无 |
| 纯文本 PAGE_BREAK 回退分页分段 | text 含 [PAGE_BREAK:2] 与空行 | buildParagraphs | 页 1/页 2 各产出段落、type 以 # 前缀判 heading | B01 | 无 |
| DocNode 标题栈拼 headingPath | HEADING(L1)+HEADING(L2)+PARAGRAPH | buildParagraphs | 正文段 headingPath="第一章 > 1.1 背景" 形态；L1 重复时栈正确弹出 | B01 | 无 |
| 锚定失败段落按兜底横带分带 | PDF 行盒 + 不可锚定段（OCR 差异文本） | fillPdfRects | 段落获得兜底带 rect（x≈0.03~0.97、按预估行数比例分割上/下锚点区间）；不缺框 | B02 | MinIO（原件下载） |
| 锚定成功段产出 rects+anchorText | 页面行与段落文本可模糊匹配 | fillPdfRects | rects 归一化 0~1；anchorText=命中行文本按序拼接 | B02、B03 | MinIO |
| 跨页合并在每页各锚一次 | 合并段 pages=[15,16]，两页各有行盒 | fillPdfRects | 该段 rects 含两页各一个盒；每页只消费本页行（不顶错后续段） | B02、B05 | MinIO |
| 非 PDF 或装配异常降级空集 | extension=docx / 下载失败 | fillPdfRects | 返回空 imageBoxes、不抛错 | B04 | MinIO |

### AutoChunkStrategy（contract: ai-chunking.AutoChunkStrategy）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| LLM 输出覆盖不全降级 embedding | kb 配置 LLM+Embedding 模型；LLM 返回缺编号分组 | autoChunk | outcome.source="embedding"；分组合法 | C01、C02 | LlmService、EmbeddingService |
| LLM 输出编号重复/乱序/非法 JSON 降级 | LLM 返回 [[1,2],[2]] / [[3],[1]] / 非 JSON | autoChunk | source 非 "llm" | C02 | LlmService |
| 无任何模型配置走长度兜底 | kb 两模型字段均空 | autoChunk | source="rule"、按 chunkLength 累积分组 | C01 | 无 |
| 超长正文组按句界二次切分带 _sN | chunkLength 小于单段长度 | autoChunk（finishAuto） | 产出多组、衍生段 id 带 _sN、各组 ≤chunkLength | C04 | 无 |
| 表格超长保持原子不切 | 超长 table 段 | autoChunk | 表格组独立且允许超限、无 _sN 衍生 | C04 | 无 |
| 长度断片携带句界回退 overlap | rule 路径累计超限、上组末段为正文 | autoChunk | 下一组开头为上组尾部 ≤100 字符、自最早句界起、≥20 字符；语义断点（embedding 路径）无 overlap | C05 | EmbeddingService |
| 迷你纯文本组回收、表格组独立 | rule 产出 <300 字符正文组与表格组相邻 | autoChunk | 正文组并入相邻组；表格组原样独立 | C06 | 无 |
| 分组后段落-分片双向引用完整 | 任意路径产出 groups | backfillChunkIds（经 autoChunk 断言） | 每 group.paragraphIds 非空、对应段 chunkId=组 id；_sN/_oN 归并基段 | C07 | 无 |
| 策略参数经三层合并获取 | 策略 advanced 设 chunkLength=500 | autoChunk | 分组以 500 为硬上限（不读策略表直查） | C03 | 无（配置桩属 parse-strategy 契约面） |

### RegionExtractor（contract: ai-chunking.RegionExtractor）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 文字层区域命中产结构化块零 OCR | 页有文字层；区域覆盖行中心、覆盖 ≥40% 行宽 | ocrImageRegion | blocks 含 paragraph/heading/table；OcrService 零调用；页眉脚带（7%/93%）行被排除 | D01 | MinIO |
| 框选起点略高于标题时截去上一节尾行 | 区域内首个命中行为标题样、其上有一尾行 | ocrImageRegion | 从首标题行起产出块 | D01 | MinIO |
| 内容图片相交 ≥30% 裁原图直达分片 | 区域与图片盒相交超阈、rot=0 | ocrImageRegion | 产 image 块携 imageKey（MinIO 已上传）+ OCR 图注；OCR 失败保持纯图 text="" | D01、D03 | MinIO、OcrService |
| 扫描底图（≥85% 页）跳过图片路径 | 区域与整页底图相交 | ocrImageRegion | 不产 image 块、落路径③整区域 OCR | D01 | MinIO、OcrService |
| 纯图形区域多级重试 OCR 成功 | 无文字行无内容图；第 3 次（反相）识别出文本 | ocrImageRegion | 产 paragraph 块；尝试序列 220→300→300 反相→300 红章；展示/上传用原图 | D01 | MinIO、OcrService |
| OCR 全空返回截图占位块 | 四次尝试均空、截图上传成功 | ocrImageRegion | blocks 含 1 个 image 块（imageKey=截图、text 为占位说明）；非空 blocks | D03 | MinIO、OcrService |
| /Rotate 90 页框选不错位 | 页 rotation=90 | ocrImageRegion | 视觉宽高交换后换算、裁剪区域含目标文字（构造旋转页断言视觉坐标） | D02 | MinIO |
| 页码越界/下载失败/文件不存在报错 | page=0 / MinIO 抛错 / fileId 查无 | ocrImageRegion | 对应业务错误（页码超出范围/文档下载失败/文件不存在） | Error modes（实现防御，见 contract/interfaces.md ai-chunking 节） | MinIO |

### LayoutAnalysisOrchestrator（contract: ai-chunking.LayoutAnalysisOrchestrator）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 文字层页走几何聚类零 VLM | 全页文字 ≥200 字符 | streamLayout（消费 emitter 事件） | layout 事件逐页推送、VLM analyzePage 零调用、页眉脚行不入块 | D04、D06 | MinIO、VLM |
| 扫描页归 VLM 并发不超 5 | 全页文字 <200 字符、页数 >5 | streamLayout | analyzePage 并发 ≤5（信号量）；单页 VLM 失败跳过计数、其余页照常 | D04 | MinIO、VLM |
| 非 PDF 立即 unsupported 终态 | extension=docx | streamLayout | done 事件 {unsupported:true}、无 layout 事件、不报错 | D05 | MinIO |
| 缓存命中一次全量推送 | layout.json 存在且 v=3 | streamLayout | 一次 layout（全量）+ done{cached:true}；无几何/VLM 调用 | D05 | MinIO |
| 缓存版本不符重算且部分失败不落盘 | 缓存 v=2；本次 1 页 VLM 失败 | streamLayout | 全页重算、失败页跳过、layout.json 未被覆写 | D05 | MinIO、VLM |
| 几何分类优先级与表格边缘吸收 | 同 y 多列短行 + 相邻漏行 + 大字号行 | PdfLayoutAnalyzer.analyze | table 块吸收边缘漏行；title/table 优先于 text；caption 不标表格邻接短行 | D06 | 无（现有 PdfLayoutAnalyzerTest 模式） |

### ApplyOrchestrator（contract: ai-chunking.ApplyOrchestrator）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 应用按请求序替换落库不重切 | 准入通过；请求 3 分片 | apply | 新行 id=`{fileId}_chunk_0..2` 与请求一一对应、chunkType=text、storeChunks 单层调用一次；返回 3；全程无分片器/解析调用 | E04 | MinIO、Milvus、GraphService、StorageService（写面边界） |
| 替换删 auto 留 manual | 库内 auto 2 片 + manual 1 片 | doApply | 仅 auto 行被删、manual 行/向量原样；Milvus 按 auto embeddingId 定向删（非 deleteByFileId） | E03 | Milvus、GraphService |
| 向量/图谱清理失败降级不回滚 | Milvus.deleteByIds / deleteFileGraph 抛错 | doApply | 落库照常完成、返回分片数；manual graphIndexed 重置尝试 | E03、E06 | Milvus、GraphService |
| 五重准入逐项拒绝 | 回收站/processing/QA/空列表/空白内容 各构造 | apply | 对应业务错误消息、MySQL/Milvus 零副作用 | E01 | 无 |
| 同文件并发二次 apply 拒绝 | 第一次 apply 执行中（阻塞 storeChunks） | 并发第二次 apply | 第二次抛「正在应用」业务错误；第一次完成后互斥键清除、可再次 apply | E02 | MinIO、Milvus、GraphService、StorageService |
| 应用后文件状态与计数对账 | 请求 3 片 + 库存 manual 1 片（非 parent） | doApply | kb_file.status=completed/progress=100/stage=""/chunk_count=4 | E05 | 无 |
| parsed 派生产物按契约写入 | apply 成功 | doApply | parsed.md=分片文本 "\n\n" 拼接；parsed.json 携 chunks[].{id,index,source,pageRange?,paragraphIds}；写失败仅 warn | E05 | MinIO |
| 父分片不计入 manual 计数 | 库存 manual 1 片 + parent 1 片 | doApply | chunk_count=落库数+1（parent 排除） | E05 | 无 |

### AiChunkWorkflowModule（contract: design §2；SSE 为系统边界，断言事件序列）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| stream 事件序列与里程碑 | 文件可解析 | 消费 SseEmitter 事件 | 顺序 progress(8,parse)→progress(35,merge)→fragment×N→progress(55,llm-chunk)→chunk×N→done；fragment 截断 400 字符 | F01 | AI 四能力、MinIO |
| stream 异常以 error 收尾 | 解析抛错 | 消费事件 | 收到 error{message} 且流终止、无 done | F01 | MinIO |
| preview withChunks=false 不触发分组 | chunk=false | analyze | 返回段落对齐模型、chunks 空、无 LLM/Embedding 调用；layoutBlocks=缓存命中面 | F02 | AI 四能力、MinIO |
| 权限面：viewer 可分析 editor 才可应用 | viewer/editor 各角色 | 调 5 端点 | stream/preview/layout/image-ocr viewer 放行；apply 对 viewer 拒绝且 @Loggable 审计（editor） | F03 | 无（安全切面为 iam 既有事实） |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| 正文段页尾无句号跨页拼接为单段 | ai-chunking.ParagraphModelAssembler | ParagraphModelAssembler（缝 2） | REQ-A04 |
| LLM 输出覆盖不全降级 embedding | ai-chunking.AutoChunkStrategy | AutoChunkStrategy（缝 1/3） | REQ-C01、C02 |
| 应用按请求序替换落库不重切 | ai-chunking.ApplyOrchestrator | ApplyOrchestrator | REQ-E04 |
| 替换删 auto 留 manual | ai-chunking.ApplyOrchestrator | ApplyOrchestrator | REQ-E03 |
| 扫描页归 VLM 并发不超 5 | ai-chunking.LayoutAnalysisOrchestrator | LayoutAnalysisOrchestrator（缝 1） | REQ-D04 |
| 内容图片相交 ≥30% 裁原图直达分片 | ai-chunking.RegionExtractor | RegionExtractor（缝 1） | REQ-D01、D03 |
| preview withChunks=false 不触发分组 | （HTTP 面）AiChunkWorkflowModule | AiChunkWorkflowModule | REQ-F02 |

## 4. 待实施说明
- 孤儿 REQ 豁免：REQ-D07（[GAP] 区域框选对非 PDF 格式的支持范围——前端选区桥能力不在本仓，后端仅 PDFBox 路径，属占位/债务登记，无下游承接为有意；见 implement 文档）

- 红绿循环**未执行**（本任务禁止改源码/测试码）；执行时按本文件从 Tracer Bullet 起步：建议以「替换删 auto 留 manual」+「正文段跨页拼接」两用例先行（两重点用例族各一），再铺 S1→S6，S7 端到端收尾。
- 现有测试已覆盖（作为本设计的先行切片，实读核实）：PdfParagraphAssemblyTest（4 用例：正文行合并/大字号标题/编号标题/小数非标题，覆盖 S2 装配回归）；PdfRectAssignmentTest（6 用例：精确对齐/跨页后缀前缀/继承标题不占行/漂移自愈/模糊容错/短行不误锚，覆盖 S2 锚定算法）；PdfLayoutAnalyzerTest（7 用例：几何聚类 + 真实 PDFBox 端到端分类，覆盖 S5 几何分类）。缺口：S1 合并族、S3 降级链、S4 区域路径、S5 缓存/分层路由、S6 替换幂等、S7 事件序列均无用例（grep 实测无 AiChunk* 测试类）。
- mock 纪律：StorageService 在 S6 以「写面边界」身份打桩（其内部契约归 chunk-management 链验证，本链只断言调用形态：单层模式、调用次数）；AI 四能力/MinIO/Milvus/GraphService 为系统边界允许 mock；ParagraphModelAssembler/AutoChunkStrategy 内部纯函数零 mock。
