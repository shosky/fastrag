# FastRAG 文件上传与解析处理链路调查报告（上传向导引擎配置打通后端）

> 调查时间：2026-08-11
> 结论速览：上传与解析是分离的两步（upload 不触发解析，需再调 process）；process 已支持 processingMode/qaConfig，但 qaConfig 是死参数（无消费者）；OCR/ASR 引擎、语言、说话人分离、优先级、重试次数等前端向导配置全部未传到后端；解析器里关键帧间隔/哈希阈值是唯一已打通的高级配置（经 kb_parse_strategy.advanced）。

## 1. 上传链路
- `POST /api/kb/{kbId}/files`（FileController.java:83-99）：仅 file + folderId，不触发解析。
- FileServiceImpl.upload（:104-134）：MinIO 上传 + 写 kb_file 元数据（status=pending），无 MQ。

## 2. 处理链路
- `POST /api/kb/{kbId}/files/{id}/process`（FileController.java:101-111）：body 只认 processingMode + qaConfig 两键。
- FileServiceImpl.process（:136-184）：resolveStrategy 按扩展名匹配策略（:144）；持久化 processingMode/parseStrategyId/enableGraphBuild（:147-164）；组装 MQ 消息（:168-179）；publishIngestion（:180）。
- 异步消费 IngestionConsumer.handleIngestion（:99-462）：只读 fileId/kbId/objectKey/strategyId/operator/enableGraphBuild；不读 processingMode/qaConfig。流水线：幂等检查 → 下载 → parse → 分块 → 音频切片 → PDF/DOCX/PPT 图片 OCR → storeChunks → 图谱 → completed。
- MQ 不可用时同步降级直接调 handleIngestion（MessagePublisher.java:87-98）——改配置传递必须同步覆盖此路径。

## 3. 解析参数消费点（DocumentParserImpl）
- parseVideo（:1763-1825）：唯一从策略 advanced 读引擎类配置（keyframeIntervalSeconds 默认10 / keyframeHashThreshold 默认10，:1767-1773）；音频路 asrService.transcribe（:1782-1784）；视觉路 extractKeyframes → 去重 → ocrService.recognize（:1797-1817）；mergeSegmentsWithKeyframes 合并（:1833-1916）。
- parseAudio（:1947-1952）→ doAsrParse（:1957-1982）：直接 transcribe，不读策略配置。
- parseImage（:1987-1998）：ocrService.recognize + 可选 LLM 增强。
- 无：引擎选择、language、说话人分离、时间范围裁剪、vlmModel。

## 4. OcrService / AsrService（fastrag-ai 模块）
- 都是具体 @Service 类（非接口），无多实现。引擎来自 @Value：ai.ocr.url/key/model（默认 deepseek-ai/DeepSeek-OCR）、ai.asr.url/key/model（默认 FunAudioLLM/SenseVoiceSmall）。请求体无 language/engine 参数（AsrService multipart 只有 file+model，:112-135）。
- 要支持引擎切换需：方法加 engine/language 参数 + 配置支持多组 url/model。

## 5. MediaExtractor
- FFmpeg 封装：extractAudio（视频抽音频）、extractKeyframes（fps=1/interval 均匀采样）、deduplicateByHash（pHash）、splitAudio（按 ASR 时间戳切）、extractPdfImages。关键帧=均匀采样+哈希去重，无场景检测。

## 6. kb_file 表
- 列：id/kb_id/name/category/extension/size/object_key/status/progress/stage/duration/pages/parse_strategy_id/parse_strategy_name/chunk_count/processing_mode/enable_graph_build/folder_id/view_count/deleted_at/created_at/updated_at。
- 无 processing_config JSON 列（需加）；duration 列全链路无人写入。

## 7. qaConfig 死参数
- process 的 qaConfig 只进 MQ 消息，IngestionConsumer 从不读取 → 死参数。「问答对提取」模式无实现（QA 对只能手工创建）。

## 8. 说话人分离 / 时间戳
- kb_chunk 有 start_time/end_time（ASR segments → ChunkTimeSegment → chunkBySegments → StorageServiceImpl:240-241）。
- 说话人分离全链路无实现：AsrResult.AsrSegment 无 speaker 字段、kb_chunk 无 speaker 列、解析器不传参。

## 打通配置改动清单
1. 前端：FileManager.handleUpload（:94-116）把 parseStrategyId/language/encoding/priority/retryCount/engineConfig/mediaConfig 全量放进 process body；api/index.ts:130-132 类型扩展。
2. Controller：FileController.process 泛化接收 body；parseStrategyId 优先于服务端 resolveStrategy。
3. 持久化：kb_file 加 processing_config JSON 列；FileServiceImpl.process 持久化并放 MQ 消息。
4. MQ/消费：IngestionConsumer 读取新字段（同步降级路径同步覆盖），构造 ParseOptions 传入 documentParser.parse。
5. 解析器：parseVideo/parseAudio/parseImage 接入选项（引擎/语言/时间范围/视频策略）。
6. AI 服务：OcrService/AsrService 改造成可传 engine/language。
7. 不做（无后端能力）：说话人分离、MQ 优先级、VLM 图片描述（需 LLM 多模态）、QA 抽取分支。

## 实施记录（2026-08-11 已落地）

按「配置化多引擎 + 视频策略分支 + 文本编码 + 时间裁剪 + 失败重试」范围完成：

**配置传递链路（打通）**
- 新增 `model/FileProcessRequest.java`：processingMode/qaConfig/parseStrategyId/language/encoding/priority/retryCount/engineConfig/mediaConfig
- `FileController.process` body 改为 FileProcessRequest；`FileServiceImpl.process` 全量落库到 kb_file.processing_config（新增列，schema.sql + SchemaInitializer 幂等加列），MQ 消息带 processingConfig JSON
- parseStrategyId 优先于服务端 resolveStrategy（校验归属，非法回退自动匹配）
- retry / re-chunk 通过 rebuildProcessRequest 复用原引擎配置
- `IngestionConsumer`：读 processingConfig → 构造 ParseOptions → parse 带 options；PDF/DOCX/PPT 图片 OCR 带所选引擎；失败按 retryCount 自动重试（本地循环，MQ 同步降级路径同覆盖）

**解析器消费（ParseOptions）**
- 新增 `parser/ParseOptions.java`：ocrEngine/asrEngine/language/encoding/videoStrategy/keyframeInterval/timeRanges/fileName
- `DocumentParser.parse` 新增重载；parseVideo 按 videoStrategy 分支（keyframe_asr/asr_only/uniform_sample），keyframeInterval 覆盖策略 advanced，时间范围 FFmpeg 裁剪（MediaExtractor.cropMedia 新增）；parseAudio/parseImage/parseDefault（encoding 覆盖检测）接入

**AI 引擎（配置化多引擎）**
- OcrService/AsrService 方法重载带 engine/language：引擎代码 → ai.ocr.engines.{code}.* / ai.asr.engines.{code}.* 配置，未配置回退默认并告警；AsrService multipart 增加 language 字段（auto/mixed 不传）
- application.yml 增加 whisper/paddle 引擎占位配置

**前端**
- FileManager.handleUpload 全量转发配置；api.processFile 类型放宽
- FileUploader 引擎值改 code（deepseek/paddle/funasr/whisper）；vlmModel 非 disabled 选项禁用+提示、speakerDiarize 禁用+tooltip、priority 非 normal 禁用+提示（后端无能力，诚实标注）

**不做（后端无能力，前端已禁用/提示）**：vlmModel 视觉描述、说话人分离、priority MQ 优先级、QA 抽取分支（qaConfig 仍为死参数）。
