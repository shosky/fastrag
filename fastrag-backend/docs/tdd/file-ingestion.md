# 文件上传与摄取流水线（file-ingestion）— 测试设计

> 现有测试基线（src/test/java/com/fastrag/module/knowledge/，实读 2026-09-22）：parser/ 12 个（DocxImageParseTest、ExcelParserSmokeTest、ImageDedupTest、ImageFilterTest、LegacyFormatParseTest、LlmHeaderFullFlowTest、ParseMethodRegistryTest、PdfImageExtractionTest、PdfImagePositionTest、PdfPageTitleTest、PdfParagraphAssemblyTest、PptxExtractionProbeTest）+ util/OcrNoiseFilterTest（5 例）覆盖解析与过滤纯函数；**IngestionConsumer、FileServiceImpl、StorageServiceImpl、FileController、FileMetadataService 均零直接测试**（feature-map「测试 19 个」为 knowledge 模块全量口径，其中 chunking 3 + config 1 + consumer/graph 2 + service/impl 3 归相邻特性链）。本设计不写测试代码，仅规划；执行时从 Tracer Bullet 起步。

## 1. 切片规划

按 contract/interfaces.md `## file-ingestion` 两个契约接口 + design 模块分组；HITL 在前。AFK=可自动化 / HITL=需人决策。

| 切片 | 契约/模块 | 内容 | AFK/HITL | 现状 |
|---|---|---|---|---|
| S1 | IngestionPipeline | **Tracer Bullet**：真实环境端到端（上传→触发→消费→completed）：状态里程碑序列、三分存储、parsed.md 落盘、通知——需真实 MinIO/Milvus/RabbitMQ/FFmpeg/AI 网关，结果人工核对 | **HITL** | 未实施 |
| S2 | FileLifecycleModule | **删除清理完整性**：永久删除/清空回收站后五路（分片/向量/图谱/MinIO 前缀/QA+文件级策略）零残留——同真实环境人工核对 | **HITL** | 未实施 |
| S3 | IngestionPipeline（触发面） | process 快照固化与消息字段、图谱开关继承两分支、retry 快照复用、priority 不入消息消费 | AFK | 未实施（FileServiceImpl 无测试） |
| S4 | IngestionPipeline（消费编排） | 幂等三分支、状态里程碑与 stage 截断、失败重试与终态、音频切片数量对账、manual 分片对账、MQ 同步降级 | AFK | 未实施（IngestionConsumer 无测试） |
| S5 | DocumentParser（格式解析路由与降级） | 8 格式代表性样本路由 + 降级注入（LLM/FFmpeg/OCR/单路） + 编码链 + 加密 Excel + 扫描页 OCR 兜底 | AFK | **部分覆盖**：parser/ 12 个（PDF 标题/段落装配/图片提取与定位、DOCX 图片、PPT 探测、Legacy doc/ppt、Excel 冒烟、LLM 表头全流程、ImageFilter/ImageDedup、ParseMethodRegistry）；OcrNoiseFilterTest 5 例。缺口：DocumentParser 路由分发、enhanceWithLlm 降级、FFmpeg 降级、视频双路合并、字符集链 |
| S6 | IngestionPipeline（Markdown 面） | persistParsedMarkdown 资格集/空文本/永不抛出；GET 未生成 markdown_not_ready；PUT+rechunk 组合错误 | AFK | 未实施 |
| S7 | FileLifecycleModule（操作面） | 软删/恢复/清空、copy 元数据随拷与共享 objectKey、moveToKb 校验与事务、update 改绑拦截复用断言、preview 20/300 裁剪 | AFK | 未实施 |
| S8 | FileMetadataModule | 元数据更新回填 chunk 冗余列与 revised 置位、补抽仅覆盖 none/partial、批量打标汇总 | AFK | 未实施 |

mock 边界纪律：仅系统边界可 mock——MinIO(MinioService)、Milvus(MilvusService)、GraphStore(GraphService)、RabbitMQ(MessagePublisher/RabbitTemplate)、FFmpeg(进程)、fastrag-ai(OcrService/AsrService/LlmService)、mapper 持久层；**禁 mock** DocumentParser、ChunkingService、StorageService、OcrNoiseFilter/ImageFilter 等内部逻辑模块。

## 2. 测试用例（逐接口）

### IngestionPipeline（contract: file-ingestion.IngestionPipeline）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [E2E] 文档上传触发后 [全链完成且产物齐备]（Tracer Bullet） | 真实环境：上传 sample.pdf 并 process | 等待消费完成 | status=completed、progress=100；kb_chunk 行+Milvus 向量一致；parsed.md 可读；通知存在；stage 序列含 downloading→parsing→chunking→storing→done | REQ-A01、B01、C02、C07、E01 | **真实 MinIO/Milvus/MQ**（HITL 核对） |
| [触发] 处理请求 [固化三份状态并投递完整消息] | 文件 pending；req 含 strategyId/engineConfig/retryCount | process | kb_file 三列（mode/strategy/config）落库；消息 8 字段与请求一致 | REQ-B01、B02 | MessagePublisher、mapper |
| [触发] 用户显式改过图谱开关 [不再按 KB 继承] | 文件 updatedAt>createdAt 且 enableGraphBuild=0；KB graphAutoBuild=1 | process | 消息 enableGraphBuild=0（尊重用户） | REQ-B04 | mapper、MessagePublisher |
| [触发] 新文件 KB 开关开启 [继承为 1] | updatedAt==createdAt；KB graphAutoBuild=1 | process | enable_graph_build=1 且入消息 | REQ-B04 | mapper、MessagePublisher |
| [重试] 快照存在 [复用原引擎配置重跑] | processing_config 含 asrEngine/videoStrategy | retryFile | 先五路清理，重建请求 parseStrategyId=文件绑定值，消息配置与快照一致 | REQ-B05、C09 | MinIO/Milvus/GraphService/MessagePublisher |
| [幂等] 文件已物理删除 [ack 跳过] | 消费时 selectById=null | handleIngestion | 直接 return，无状态写入无异常 | REQ-C01 | mapper |
| [幂等] 文件已软删 [ack 跳过] | deleted_at 非空 | handleIngestion | 直接 return | REQ-C01 | mapper |
| [幂等] 重复消息且已 completed [ack 跳过] | status=completed | handleIngestion | 不重复产生分片（chunkCount 不变） | REQ-C01 | mapper |
| [状态] stage 超 60 字符 [截断落库] | 构造失败路径含超长异常消息 | handleIngestion 失败 | kb_file.stage 长度 ≤60 且以 error: 开头 | REQ-C02、C09 | mapper |
| [失败] 必需步骤异常且 retryCount=2 [两次重试后置 failed] | 解析器注入持续失败；processingConfig.retryCount=2 | handleIngestion | 状态序列含 2 次 pending/retrying，终态 failed + 失败日志；消息 ack 不回队 | REQ-C09 | DocumentParser 不可 mock→用损坏文件字节；mapper/MessagePublisher |
| [音频] 分段数与分片数一致 [切片并上传 segments] | 真实小 mp3，ASR mock 返回 n 段 | handleIngestion | MinIO 出现 n 个 segments/{i}.ext；不一致时零切片且 completed | REQ-C04 | MinIO、AsrService、MessagePublisher |
| [对账] 重分片保留 manual 分片 [索引后置且计数修正] | 库中存在 origin=manual 分片 3 条；新 auto 5 条 | storeChunks 后 reconcile | manual chunkIndex=5..7；file.chunkCount=8 | REQ-C07 | mapper |
| [图谱] enableGraphBuild=true [完成时投递 incremental 消息] | 消息 enableGraphBuild=1 | handleIngestion 完成 | publishGraphBuild 收到 {kbId,fileId,mode:incremental}；投递异常仅告警仍 completed | REQ-C08 | MessagePublisher |
| [降级] MQ 探活失败 [同步直调完成处理] | MessagePublisher 探活返回 false | process | ingestionHandler 被同步调用一次，文件最终 completed | REQ-C10 | RabbitTemplate/ConnectionFactory |
| [E2E-删除] 永久删除后 [五路衍生零残留]（Tracer Bullet） | 真实环境：含分片/向量/图谱/图片/QA/文件级策略的文件 | permanentDelete | 五路查询全部为空；MinIO 前缀整删 | REQ-F02 | **真实 MinIO/Milvus/Neo4j**（HITL 核对） |
| [软删] 删除文件 [图谱即清、分片向量保留] | 文件 completed 含向量 | delete | deleted_at 置位；GraphService.deleteFileGraph 已调；kb_chunk/Milvus 不动 | REQ-F02 | GraphService、mapper |
| [移动] 目标库同名 [拒绝且无副作用] | 目标库存在同名未删文件 | moveToKb | 抛错；源库数据未清理、归属未变 | REQ-F05 | mapper、MinIO |
| [移动] 正常移动 [事务内迁移并异步重处理] | 目标库存在且无同名 | moveToKb | MinIO copy+delete；kb_id=目标；消息 strategyId=目标库自动匹配 | REQ-F05 | MinIO、MessagePublisher |
| [复制] 元数据随拷 [共享原 objectKey] | 源文件含 region/customAttrs 等 | copy | 新记录元数据字段一致、objectKey 相同、status=pending | REQ-F04、A01 | mapper |
| [markdown] 保存+rechunk 且重切失败 [返回组合错误] | svc.reChunkFile 注入失败 | PUT markdown?rechunk=true | body.code=500 且消息含「已保存，但触发重新分片失败」；MinIO 已更新 | REQ-E03 | MinIO、MessagePublisher |
| [元数据] 更新 [事务回填 chunk 冗余列并置 revised] | 文件含分片；req 更新 region/docLevel | updateMetadata | kb_chunk 对应行 region/publish_date/doc_level 同步；metadata_status=revised、source=manual | REQ-G01 | mapper |
| [元数据] 补抽遇 revised [跳过不覆盖] | 文件 metadata_status=revised；force=false | extractMetadata | 该文件未回填 auto 值；汇总中计入跳过 | REQ-G03 | mapper |

### DocumentParser（contract: file-ingestion.DocumentParser）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [路由] 策略 parseMethod 优先于扩展名 | strategy.parseMethod=audio，文件 ext=.txt | parse | 走 ASR 路（mock AsrService 被调） | REQ-D01 | AsrService、mapper |
| [路由] 未识别扩展名 [回退 default 纯文本] | ext=.xyz，UTF-8 文本字节 | parse | 返回 text=原文；不抛错 | REQ-D01 | OcrService（default 不该被调） |
| [PDF] 扫描页 [渲染 OCR 兜底产出文本] | 纯图片 PDF（1 页无文字层） | parse | OcrService 被调（200dpi PNG）；nodes 非空 | REQ-D02 | OcrService |
| [PDF] 正常页 [几何行装配+页眉脚清除] | 含重复页眉的 3 页 PDF | parse | 页眉文本不出现在 nodes；标题跨页继承进 pageTitles | REQ-D02 | 无（纯本地）〔现有 PdfPageTitleTest/PdfParagraphAssemblyTest 已覆盖部分〕 |
| [Excel] 加密工作簿 [专用错误上抛] | 加密 xlsx 字节 | parse | 异常消息含「加密」语义；非笼统 E_PARSE_FAILED | REQ-D04、E_XLSX_ENCRYPTED | 无 |
| [文本] GBK 字节显式 encoding=gbk [正确解码] | GBK 编码中文字节 | parse(options.encoding=gbk) | text 中文无损 | REQ-D05 | 无 |
| [文本] UTF-8 校验失败 [字节探测回退] | GBK 字节 + encoding=auto | parse | 探测链回退，中文可读（或至少无替换字符雪崩） | REQ-D05 | 无 |
| [视频] ASR 路失败 [关键帧路单独成片] | AsrService 抛错；关键帧 OCR 有结果 | parse(video) | segments 仅含【画面内容】块；不抛错 | REQ-D06、D_SINGLE_PATH_DEGRADED | AsrService、OcrService、FFmpeg 进程 |
| [视频] FFmpeg 缺失 [退化为纯 ASR] | ffmpegPath 指向不存在命令 | parse(video) | extractKeyframes 空列表；ASR 分段原样返回 | REQ-D07、D_FFMPEG_UNAVAILABLE | FFmpeg 进程、AsrService |
| [视频] 双路齐备 [ASR 对齐合并关键帧 OCR] | mock ASR 2 段 + 2 关键帧 OCR | parse(video) | 合并段含【画面内容】+【语音内容】；时间取 ASR 段 | REQ-D06 | AsrService、OcrService、FFmpeg 进程 |
| [音频] timeRanges 命中文件名 [仅裁剪区间入 ASR] | mediaConfig.timeRanges 匹配 fileName | parse(audio) | cropMedia 收到 [start,duration] 参数；crop 失败时全长继续 | REQ-D06、D07 | FFmpeg 进程、AsrService |
| [LLM增强] 文本 >5000 字符 [跳过不调模型] | strategy.llmModel 配置；长文本 | parse | LlmService 零调用；text 原样 | REQ-D08 | LlmService、mapper(ModelRecord) |
| [LLM增强] 调用异常/空响应 [回退原文] | LlmService 抛错 / 返回空白 | parse | text=原文；不抛错 | REQ-D08 | LlmService、mapper |
| [LLM增强] ModelRecord 非 online [跳过增强] | model 表无 online 记录 | parse | apiUrl=null 分支，零模型调用 | REQ-D08 | mapper(ModelRecord) |
| [图片] 嵌入截图 OCR 全噪声 [该图不入分片] | OcrService 返回按钮词/占位符串 | 消费端 sanitize+归并 | ocrResults 无该图，chunks 无对应 image 分片 | REQ-C05、C06 | OcrService、MinIO（消费侧用例，归 S4） |
| [过滤] 装饰图双端拦截 [不入 MinIO/分片] | DOCX 含 <100px 图标 | parse + 消费端兜底 | ParseResult.images 无该图；消费端防御复查命中 | REQ-D09 | 无/MinIO〔现有 ImageFilterTest/ImageDedupTest/DocxImageParseTest 已覆盖规则层〕 |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [E2E] 全链完成 | IngestionPipeline | IngestionPipelineModule | A01、B01、C02、C07、E01 |
| [幂等] 重复消息跳过 | IngestionPipeline | IngestionPipelineModule | C01 |
| [降级] MQ 同步直调 | IngestionPipeline | 缝 4 | C10 |
| [视频] 双路合并/单路降级 | DocumentParser | FormatParserModule（缝 2） | D06 |
| [LLM增强] 三降级分支 | DocumentParser | FormatParserModule | D08 |
| [E2E-删除] 五路零残留 | （FileLifecycleModule 公共接口） | FileLifecycleModule（缝 6） | F02 |
| [元数据] 冗余列回填 | （FileMetadataModule 公共接口） | FileMetadataModule | G01 |

完整逐条追溯见各用例行「追溯 REQ」列 → design/file-ingestion.md §5 Parent Trace。

## 4. 待实施说明

- 红绿循环未执行（本任务不写测试代码）；执行时按本文件 S1 Tracer Bullet 起步，先锁定端到端骨架再下沉切片。
- S1/S2 依赖真实基础设施（MinIO/Milvus/Neo4j/RabbitMQ/FFmpeg/AI 网关），建议复用 docker-compose 环境；S4/S5 可用 Testcontainers + mock 系统边界先行。
- 解析器测试样本建议入库 `src/test/resources/samples/`（当前测试以编程方式构造文档为主，无固定样本目录——grep 实测）〔决议 2026-09-24：按现状为准——抽样策略沿用现有测试的编程构造风格，样本目录维持不建，待实施时再定（代裁，用户可推翻）〕。
- 损坏文件注入（S4 失败路径）优先用截断字节而非 mock DocumentParser（mock 纪律）。
