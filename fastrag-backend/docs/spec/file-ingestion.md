# 文件上传与摄取流水线（file-ingestion）— 需求规格

## 1. 特性与范围

本特性覆盖知识库文件从「字节进入系统」到「可检索分片就绪」的全过程：两阶段上传（第一阶段 MinIO 落盘 + `kb_file` 元数据 pending，第二阶段处理触发进 RabbitMQ 异步摄取）、异步摄取流水线（下载 → 解析 → 分块分派 → 嵌入图片提取与 OCR → 音频切片 → 分片存储 → 通知/图谱触发/Markdown 落盘）、8 类格式解析与逐级降级（PDF/DOCX/DOC/PPTX/PPT/XLSX/文本/图片 OCR/音频 ASR/视频双路）、Markdown 全文持久化与读写、回收站与文件操作族 CRUD（复制/跨库移动/下载族/状态查询/预览）、文件元数据管理。显式排除：解析策略的定义/匹配/绑定守卫（parse-strategy，本特性是 StrategyConfigResolver 与 ParseMethodRegistry 的消费方）、分片算法本体与分片数据模型（chunk-management，本特性只调用其分片服务并写库）、OnlyOffice 编辑回调（onlyoffice-editing，仅复用本特性的 replaceOriginalFile/re-chunk 能力）、AI分片 工作流（ai-chunking）。

## 2. Out of Scope

- **解析策略自动匹配与绑定一致性**：四级自动匹配（FileServiceImpl.resolveStrategy :916-952）、换绑三态、元数据改绑拦截属 parse-strategy 的 ParseStrategyBindingGuard 契约（contract/interfaces.md `## parse-strategy`）；本特性只在「处理触发」时调用其裁决结果。任何「改绑定规则」「改自动匹配优先级」的需求应拒绝并引导至 parse-strategy。
- **分片算法与父子分片装配**：rule_fixed/recursive/structure_aware/semantic/parent_child 五种分片器的实现、ParentChunkAssembler 聚合逻辑归 chunk-management；本特性只按策略分派（IngestionConsumer.java:196-236）并把结果交给存储层。
- **图谱构建本体**：摄取完成后仅投递 `fastrag.graph-build.queue` 消息（IngestionConsumer.java:494-511）；抽取/合并/存储归 knowledge-graph。
- **OnlyOffice 文档编辑与回调保存**：仅提供 replaceOriginalFile 原文件替换能力供其复用（FileServiceImpl.java:280-314）。
- **通用品牌图片上传之外的平台资产上传**：`/api/upload` 仅收 jpg/png ≤1MB（UploadController.java:49-56），任何扩大其受理范围的请求应引导至对应业务域端点。
- **向量检索消费**：Milvus 写入是本特性的边界，检索读侧归 retrieval。

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：**Ingestion Pipeline**（上传 → 解析 → 切分 → 向量化 → 存储，经 `fastrag.ingestion.queue` 异步）、**重新分片（Re-chunk）**、**AI分片**（区别概念，仅术语对照）、**结构级跨页合并**（解析层目标之一）、**自动匹配（Auto Match）**（消费其裁决）、**统一响应（ApiResponse）**。本特性是 Ingestion Pipeline 术语的定义主体。

## 4. 功能需求

### 4.1 两阶段上传

叙述：文件进入系统分两步——第一步上传把字节落 MinIO 并登记 `kb_file`（status=pending），第二步由用户在上传向导中触发处理才进入摄取队列。两步解耦让用户在处理前确认策略/引擎/裁剪等配置；代价是「只传不处理」的文件会停留 pending 态。平台侧另有一个与知识库无关的通用图片上传口。

- **REQ-A01**：上传把文件字节写入 MinIO（对象键 `{kbId}/{uuid}`），并落库 `kb_file`：原始文件名、扩展名、大小、自动识别类别（document/image/audio/video，未识别兜底 document）、objectKey、status=pending、progress=0、chunkCount=0，可选 folderId。〔证据：FileServiceImpl.java:119-148、906-914 ‹CERTAIN›〕
- **REQ-A02**：上传阶段不做扩展名/大小白名单校验——任意类型文件均可上传成功，格式适配推迟到摄取期（未识别扩展名走纯文本兜底解析）；上传亦不自动触发处理。〔证据：FileServiceImpl.java:119-148 无校验与发消息逻辑、DocumentParserImpl.java:117 default 分支 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——前端上传向导前置扩展名过滤（fastrag-frontend/src/views/knowledge/detail/components/FileUploader.vue:101 ACCEPTED_EXTENSIONS 硬编码白名单）、「后端不设防」是否属有意取舍代码不可证，现状即事实源；已登记债务：绕过前端直调 API 仍可传任意类型/任意文件名，如需强约束应在 upload 入口补白名单（代裁，用户可推翻）〕〔决议 2026-09-24：上传体积上限取 Spring multipart 配置值 200MB（R3 实证：fastrag-bootstrap/src/main/resources/application.yml:60-62 max-file-size/max-request-size 均 200MB，超限由容器拒收）；后端业务代码无显式大小校验，200MB 即实际上限（代裁，用户可推翻）〕
- **REQ-A03**：上传成功记录操作日志（file_added/file_uploaded 两路，日志失败不阻断上传）。〔证据：FileController.java:97-106 ‹CERTAIN›〕
- **REQ-A04**：管理员上传品牌图（仅 jpg/png、≤1MB）即时生效并返回访问 URL（存储与端点细节见契约）。〔证据：UploadController.java:45-73 ‹CERTAIN›〕

### 4.2 处理触发与处理配置

叙述：处理触发是摄取流水线的唯一入口（上传向导第二步、重试、重切、跨库移动共用）。触发时把「这份文件按什么参数处理」固化为三份持久化状态——处理模式、策略绑定、处理配置快照——再投递消息；重试/重切靠快照复用原配置，避免前端重传参数。

- **REQ-B01**：处理触发校验文件存在，解析处理模式（缺省 chunk），裁决解析策略（请求指定优先、否则自动匹配——语义归 parse-strategy 契约），持久化 processingMode/parseStrategyId/processingConfig 快照后，向 `fastrag.ingestion.queue` 投递含 fileId/kbId/objectKey/strategyId/operator/processingMode/enableGraphBuild/processingConfig 的消息。〔证据：FileServiceImpl.java:151-200 ‹CERTAIN›〕
- **REQ-B02**：上传向导的处理配置（语言/编码/优先级/重试次数/OCR·ASR 引擎/视频策略/关键帧间隔/时间裁剪）全量持久化到 `kb_file.processing_config`，重试与重新分片时从快照重建请求（解析失败降级最小默认请求）。〔证据：FileProcessRequest.java:16-33、FileServiceImpl.java:182、230-247 ‹CERTAIN›〕
- **REQ-B03**：处理优先级 priority 仅落库存储，摄取链路当前不消费（无调度语义）。〔证据：FileProcessRequest.java:26 注释「MQ 暂未消费」、IngestionConsumer 全文无 priority 读取 ‹CERTAIN›〕
- **REQ-B04**：图谱开关继承——文件从未被用户显式修改过（updatedAt 未晚于 createdAt）且当前未开启时，处理触发按 KB 级 graphAutoBuild 解析默认值；用户显式设置过则尊重用户选择。〔证据：FileServiceImpl.java:169-180、253-263 ‹CERTAIN›〕
- **REQ-B05**：重试先清理该文件全部衍生数据（分片/向量/图谱/MinIO 衍生物/QA 对/文件级策略），重置状态 pending 后复用原配置快照重新走标准处理流程。〔证据：FileServiceImpl.java:317-348、565-635 ‹CERTAIN›〕

### 4.3 异步摄取流水线

叙述：IngestionConsumer 监听 `fastrag.ingestion.queue`，按「下载 → 解析 → 分块 → 图片 OCR 归并 → 音频切片 → 存储 → 通知 → 图谱触发 → Markdown 落盘 → 完成」推进，全程回写 status/progress/stage 供前端轮询。它是全链路的编排者：对上消费 MQ 消息，对下调度解析器、分块服务、存储服务与 AI 能力。

- **REQ-C01**：消费消息先做幂等检查：文件不存在（已删）、已在回收站、status=completed 时直接 ack 跳过，不重投不报错。〔证据：IngestionConsumer.java:126-138 ‹CERTAIN›〕
- **REQ-C02**：流水线全程推进状态机 pending/processing/completed/failed 与进度里程碑（10 downloading → 30 parsing → 60 chunking → 70 slicing → 75 extracting images → 80 storing → 100 done），stage 超 60 字符截断；状态写失败仅记日志不阻断。〔证据：IngestionConsumer.java:159,171,186,244,271,449,520、730-742 ‹CERTAIN›〕
- **REQ-C03**：分块按策略配置的 chunk.strategy 分派五种分片器；有 ASR 时间分段（音视频）固定走时间戳分段（分片策略不适用）；缺省 rule_fixed 时有结构节点走结构分片、否则规则分片。〔证据：IngestionConsumer.java:184-236 ‹CERTAIN›；分片器本体归 chunk-management〕
- **REQ-C04**：音频文件在 ASR 分段与分片数量一致时按时间戳切割音频段并上传 MinIO（`{kbId}/{fileId}/segments/{i}.{ext}`）；数量不一致跳过切片、切割失败仅告警不阻断。〔证据：IngestionConsumer.java:242-263 ‹CERTAIN›〕
- **REQ-C05**：PDF 嵌入图片提取上传 MinIO（`{kbId}/{fileId}/images/{imageKey}`）后并行 OCR（信号量限流 5 并发），OCR 结果经噪声清洗后按页归并进文本分片流（每页先文本后图片，纯图片页按页号排位，归并后重排 index/id）；DOCX/PPT 图片同模式（有页码按页插入，无页码按文档序追加末尾）。〔证据：IngestionConsumer.java:269-339、344-445、628-728 ‹CERTAIN›〕
- **REQ-C06**：嵌入图片 OCR 文本并入正文前经行级噪声清洗（界面残留/表单占位符/纯按钮词/乱码行），全部为噪声时该图不入分片；该过滤仅用于嵌入图片，扫描件整页 OCR 不适用。〔证据：IngestionConsumer.java:309-314、396-401、OcrNoiseFilter.java:8-28、47-80 ‹CERTAIN›〕
- **REQ-C07**：分片存储交存储模块完成（Embedding + MySQL + Milvus，含父子分片聚合参数透传）；重分片场景保留的 manual 分片在存储后对账——chunkIndex 重排到 auto 之后、chunkCount 修正为 auto+manual。〔证据：IngestionConsumer.java:449-461、753-782 ‹CERTAIN›〕
- **REQ-C08**：完成后发送系统通知（knowledge_update）与更新日志（失败不阻断）；enableGraphBuild 为真时投递图谱构建消息（incremental 模式；投递失败仅告警）。〔证据：IngestionConsumer.java:464-481、486-515 ‹CERTAIN›〕
- **REQ-C09**：任一步骤异常按配置的 retryCount 重试（重试前置回 pending），最终失败置 status=failed、stage=error 摘要并记录失败日志；消息 ack 不回队，不靠 MQ 重投。〔证据：IngestionConsumer.java:156,526-544、605-616 ‹CERTAIN›〕
- **REQ-C10**：队列不可用或投递失败时同步降级为进程内直接执行流水线（双重保障），保证 MQ 宕机不中断文件处理。〔证据：MessagePublisher.java:81-103 ‹CERTAIN›〕

### 4.4 八格式解析与降级

叙述：DocumentParser 按文档类型把字节流解析为统一 ParseResult（全文 Markdown、结构节点 DocNode、时间分段、内嵌图片、页标题）。解析是流水线中最异质的一环：每类格式有自己的库、自己的失败模式，全部失败都以上抛异常外的降级方式消化，除非整体无法产出。

- **REQ-D01**：解析路由双键——策略 parseMethod 显式指定时用之，缺省/default 按扩展名经 ParseMethodRegistry 推断（未识别回退 default 纯文本）；整体解析失败包装为统一异常上抛由流水线置 failed。〔证据：DocumentParserImpl.java:97-123、129-131 ‹CERTAIN›〕
- **REQ-D02**：PDF 双链路——文字层页提带坐标几何行（行距分段/表格列 `\t`/标题识别），文字 <50 字符的页判定为扫描页渲染 200dpi PNG 走 OCR 兜底；两链路各自做页眉/页脚/页码清理；页标题跨页继承（供图片分片上下文）。〔证据：DocumentParserImpl.java:133-242 ‹CERTAIN›〕〔决议 2026-09-24：扫描页判定阈值取 50 字符维持现状——业界对「文字层字符密度判扫描页」常用 30~100 字符档位，50 居中且为本仓在用值；现状差距：无（代裁，用户可推翻）〕
- **REQ-D03**：DOCX 结构化解析（标题层级/表格/代码块/公式 OMML→LaTeX/图片提取）；DOC 走 legacy HWPF 链路；PPTX/PPT 逐形状提取并识别标题占位符。〔证据：DocumentParserImpl.java:681-717、1322-1367、1513 ‹CERTAIN›〕
- **REQ-D04**：Excel 解析含表头区域检测（多行表头合并、标题行识别、隐藏行列跳过），可选用 LLM 合并多级表头；加密工作簿识别为专用错误信息。〔证据：DocumentParserImpl.java:1712-1750、2019-2128、2128-2190 ‹CERTAIN›〕
- **REQ-D05**：纯文本族按编码检测链解析（显式 encoding > UTF-8 严格校验 > 字节探测），支持 gbk/shift-jis 等回退。〔证据：DocumentParserImpl.java:2344-2419、ParseOptions.java:26 ‹CERTAIN›〕
- **REQ-D06**：图片走 OCR；音频走 ASR 带时间戳分段（可选时间裁剪）；视频双路——音频路 FFmpeg 提取音轨后 ASR，视觉路按间隔采样关键帧、pHash 去重后逐帧 OCR，按 ASR 分段对齐合并（单路失败降级为另一路单独成片）。〔证据：DocumentParserImpl.java:2443-2533、2551-2635、2666-2733 ‹CERTAIN›〕
- **REQ-D07**：媒体工具全链降级——FFmpeg 不可用时提取音轨退化为原始字节直传 ASR、关键帧提取返回空列表（视频退化为纯 ASR）；音视频可选时间范围裁剪（裁剪失败按全长解析）。〔证据：MediaExtractor.java:78-93、105-116、DocumentParserImpl.java:2466-2476 ‹CERTAIN›〕
- **REQ-D08**：策略配置 llmModel 时对解析结果做 LLM 格式增强，但多处降级：文本 >5000 字符跳过、无 apiUrl 跳过、调用失败/空响应回退原文、超时 15 秒快速失败。〔证据：DocumentParserImpl.java:2744-2774 ‹CERTAIN›〕〔决议 2026-09-24：5000 字符/15 秒维持现状——风险动机已在代码注释载明（:2741-2742 长文本收益有限且易超时阻塞管线、:2761-2762 超时快速降级），15 秒覆盖 LLM 流式返回常规时延、5000 字符为单次增强调用的性价比上限；现状差距：无（代裁，用户可推翻）〕
- **REQ-D09**：装饰性图片五规则过滤（尺寸/宽高比/字节/页眉脚位置/视觉丰富度）在解析期与消费端双重生效，防止图标分隔线进入 MinIO/OCR/分片。〔证据：ImageFilter.java:11-63、IngestionConsumer.java:287-289、350-353、374-378 ‹CERTAIN›〕

### 4.5 Markdown 持久化

叙述：解析产出的 Markdown 全文是「文档内容」的可读事实源：流水线完成前落盘 MinIO 约定路径，供前端全文查看与编辑保存；保存可选触发重切使分片与 Markdown 保持一致。

- **REQ-E01**：流水线完成前把解析 Markdown 落盘 MinIO `{kbId}/{fileId}/parsed.md`；仅对文档类扩展名落盘（音视频/图片跳过），空文本跳过，失败仅告警永不阻断主流程。〔证据：IngestionConsumer.java:517-519、MarkdownService.java:39-70 ‹CERTAIN›〕
- **REQ-E02**：读取 Markdown 从约定路径取对象，不存在或尚未生成时返回 markdown_not_ready 语义错误并提示文档类范围。〔证据：FileController.java:152-168 ‹CERTAIN›〕
- **REQ-E03**：保存 Markdown 覆盖写约定路径（null 归一空串），可选 rechunk=true 在保存后触发重新分片；保存成功但重切失败时返回组合错误信息（数据已保存的事实不掩盖）。〔证据：FileController.java:170-197 ‹CERTAIN›〕
- **REQ-E04**：Markdown 对象键不落库——通过路径约定定位，实体仅设 transient 字段说明，避免 DDL 迁移。〔证据：KbFile.java:74-82 ‹CERTAIN›〕

### 4.6 回收站与文件操作族

叙述：文件删除是软删除（回收站），恢复/永久删除/清空构成完整生命周期闭环；复制、跨库移动、重命名、文件夹迁移、下载族与预览构成日常操作面。删除引发的衍生数据清理是「删除测试通过」级的能力。

- **REQ-F01**：文件列表排除软删（按创建时间倒序），回收站列表仅含软删（按删除时间倒序）；列表批量装配标签名与策略摘要（避免 N+1）。〔证据：FileServiceImpl.java:97-116、970-1039 ‹CERTAIN›〕
- **REQ-F02**：软删除置 deleted_at 并清理图谱数据（保留分片/向量以便恢复后重建图谱）；恢复清空 deleted_at；永久删除与清空回收站执行全量清理——MySQL 分片、Milvus 向量（按 kbId 连字符转下划线的 collection）、图谱、MinIO 原件与 `{kbId}/{fileId}` 前缀全部衍生对象、QA 对、文件级专属策略——再删记录；各外部清理失败 warn 记账不阻断。〔证据：FileServiceImpl.java:661-698、565-620 ‹CERTAIN›〕
- **REQ-F03**：元数据更新接口仅受理 name/folderId/enableGraphBuild 三键；文件夹迁移/重命名/图谱开关切换据此生效；操作日志按 patch 内容差异化记录。〔证据：FileServiceImpl.java:638-659、FileController.java:199-218、469-482 ‹CERTAIN›〕
- **REQ-F04**：复制文件产生新记录（名称加 " (copy)"，共享原 objectKey 即同一 MinIO 对象），业务元数据（地域/发文日期/层级/机关/文号/元数据状态与来源/自定义属性）随文件复制。〔证据：FileServiceImpl.java:700-725 ‹CERTAIN›〕
- **REQ-F05**：跨库移动校验目标库存在与同名冲突后，在一个事务内清理源库全部分片/向量/图谱/QA、迁移 MinIO 内容、更新归属与状态，随后按目标库自动匹配异步重处理（发消息失败仅告警）。〔证据：FileServiceImpl.java:727-796 ‹CERTAIN›〕
- **REQ-F06**：下载族三口——原文件下载（kbId+id 双校验，按扩展名映射 Content-Type 与 UTF-8 文件名）、音频切片下载（`segments/{index}`）、PDF 页面图片下载（`images/{imageKey}`）；对象缺失返回 404 语义。〔证据：FileController.java:379-464 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：FileController.java:451-452）›页图归属已由构造保证：objectKey 固定拼接为 `{kbId}/{id}/images/{imageKey}`（kbId 与文件 id 均来自路径变量），imageKey 天然被约束在本文件前缀内；MinIO 对象键为扁平字符串不做路径归一，".." 不会被解析，无法穿越前缀；对象不存在返回 404（:458-460）。原「imageKey 未约束在本文件前缀内」的疑虑不成立。
- **REQ-F07**：处理状态查询返回 fileId/status/progress/stage 供前端轮询；分块预览按（临时策略 > 指定策略 > 自动匹配）下载-解析-分块后返回前 20 个分块、内容截断 300 字符，不落库不产生副作用。〔证据：FileServiceImpl.java:798-808、810-886 ‹CERTAIN›〕

### 4.7 文件元数据管理

叙述：文档级业务元数据（地域/发文日期/层级/机关/文号 + 自定义属性）支持人工录入与存量补抽，写入即视为人工校订并与检索过滤列闭环（回填 kb_chunk 冗余列）。

- **REQ-G01**：元数据查看返回固定字段+标签+KB 自定义属性 schema+取值+状态；全量更新（固定字段+自定义取值）在事务内同步回填 kb_chunk 冗余列（region/publish_date/doc_level），并置 metadata_status=revised、metadata_source=manual。〔证据：FileMetadataService.java:21-25、FileController.java:222-241 ‹CERTAIN›；回填列清单以 schema.sql:176-178 为准 ‹CERTAIN›〕
- **REQ-G02**：单文件标签为替换式设置；批量打标为增量加/删一批文件的标签并返回处理汇总。〔证据：FileMetadataService.java:28-31、FileController.java:246-266 ‹CERTAIN›〕
- **REQ-G03**：存量补抽按规则自动抽取并回填，仅覆盖 none/partial 状态（不覆盖人工校订 revised），force=true 强制重抽，失败置 partial。〔证据：FileMetadataService.java:33-34、FileController.java:268-281 ‹CERTAIN›〕

## 5. 非功能约束（仅代码/配置可证者）

- **幂等**：重复消费消息不产生重复处理（REQ-C01，IngestionConsumer.java:126-138）。
- **降级纪律**：日志/通知/图谱触发/音频切片/Markdown 落盘/Milvus·图谱清理/LLM 增强/FFmpeg 均失败不阻断主管线（IngestionConsumer.java:260-262,479-481,509-511、DocumentParserImpl.java:2770-2773、FileServiceImpl.java:578-598 ‹CERTAIN›）。
- **并发上限**：嵌入图片并行 OCR 限流 5 并发（Semaphore）。〔证据：IngestionConsumer.java:281-282、369-370 ‹CERTAIN›〕〔决议 2026-09-24：并发 5 维持现状——OCR 属外部服务/CPU 瓶颈型任务，业界惯例 4~8 并发；容量口径：单消息 OCR ≤5 并发 × MQ 消费者并发（simple listener prefetch=2、默认单消费者，application.yml:36-39），单实例峰值 OCR 并发即 5；现状差距：无动态配置项，调优需改代码（代裁，用户可推翻）〕
- **Embedding 韧性**：分批 32 条、失败指数退避重试最多 3 次；仍失败的批次以纯文本落库、无向量不入 Milvus（ids/vectors 严格对齐防错位）。〔证据：StorageServiceImpl.java:41-45、127-134、144-165 ‹CERTAIN›〕
- **存储一致性取舍**：storeChunks 不使用 Spring @Transactional（MyBatis BATCH Executor 自管事务，二者不能混用）——跨 MySQL/Milvus 无分布式事务，Milvus 写失败仅 error 记账。〔证据：StorageServiceImpl.java:47-49、162-164 ‹CERTAIN›〕
- **回收站无自动过期清理**（仅手动清空/永久删除）〔grep 实测无定时任务 ‹CERTAIN›；@EnableScheduling 已开启但全仓 @Scheduled 仅 agent 沙箱一处（FastRagApplication.java:33、SandboxService.java:157）〕〔决议 2026-09-24：回收站保留期取 30 天自动清理——业界惯例（SharePoint/Google Drive/OneDrive 均为 30 天）兼顾误删找回与存储成本；现状差距：已登记债务——过期清理任务未实现，需新增 @Scheduled 回收站扫描（见 implement/file-ingestion.md 债务清单）（代裁，用户可推翻）〕

## 6. 验收清单

- [ ] 上传后 `kb_file` 出现 status=pending 记录且 MinIO 存在 `{kbId}/{uuid}` 对象（REQ-A01）
- [ ] 只上传不触发处理的文件永远停留 pending 且不产生任何分片（REQ-A02）
- [ ] 品牌图上传拒绝非 jpg/png 与 >1MB 文件（REQ-A04）
- [ ] 处理触发后 kb_file.processing_config 与 MQ 消息内容一致（REQ-B01、REQ-B02）
- [ ] KB graphAutoBuild=1 时新文件处理触发后 enableGraphBuild=1；用户显式关闭过的文件不被覆盖（REQ-B04）
- [ ] 同一摄取消息重复投递不产生重复分片（REQ-C01）
- [ ] 处理全程 status/progress/stage 按里程碑推进且最终 completed（REQ-C02）
- [ ] 音视频文件产生带时间戳的分片，音频额外产生 segments 对象（REQ-C04、REQ-D06）
- [ ] 含界面截图的 DOCX 摄取后，按钮词/表单占位符文本不出现在任何分片（REQ-C06）
- [ ] 扫描版 PDF 页经 OCR 兜底产出文本分片（REQ-D02）
- [ ] FFmpeg 缺失时视频文件仍完成处理（仅 ASR 路）（REQ-D07）
- [ ] 摄取完成后 `parsed.md` 可经 GET markdown 读回（REQ-E01、REQ-E02）
- [ ] 软删除文件从列表消失、出现在回收站、恢复后回到列表（REQ-F02）
- [ ] 永久删除后 Milvus/图谱/MinIO/QA/文件级策略无残留（REQ-F02）
- [ ] 跨库移动后文件归属目标库且自动重新处理（REQ-F05）
- [ ] embedding 服务整体不可用时文件仍 completed，分片纯文本可查、向量缺失（§5）
- [ ] 上传成功记录 file_added/file_uploaded 操作日志且日志失败不影响上传结果（REQ-A03）
- [ ] 处理优先级 priority 落库后摄取链路不消费（REQ-B03 无调度语义现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 重试清空该文件全部衍生数据并复用原配置快照重走标准流程（REQ-B05）
- [ ] 分块按策略 chunk.strategy 分发，ASR 时间分段固定走时间戳分段（REQ-C03）
- [ ] PDF 嵌入图片提取上传后 OCR 按页归并进文本分片流（每页先文本后图片）（REQ-C05）
- [ ] 分片存储后 manual 分片对账：chunkIndex 重排到 auto 之后、chunkCount 修正为 auto+manual（REQ-C07）
- [ ] 摄取完成后收到 knowledge_update 通知；enableGraphBuild 时图谱构建消息已投递（REQ-C08）
- [ ] 注入处理异常后按 retryCount 重试（前置回 pending），最终失败置 failed 且消息 ack 不回队（REQ-C09）
- [ ] MQ 不可用或投递失败时文件处理降级为进程内直接执行仍完成（REQ-C10）
- [ ] 策略 parseMethod 显式指定时优先于扩展名推断，未识别扩展名回退 default 纯文本（REQ-D01）
- [ ] DOCX 标题层级/表格/公式 LaTeX/图片提取正确，DOC 走 legacy 链路不失败（REQ-D03）
- [ ] 多行表头 Excel 合并正确，加密工作簿返回专用错误信息（REQ-D04）
- [ ] GBK/Shift-JIS 等非 UTF-8 文本按编码检测链正确解码（REQ-D05）
- [ ] LLM 格式增强在文本 >5000 字符、无 apiUrl、调用失败/超时时逐级跳过并回退原文（REQ-D08）
- [ ] 装饰性小图标/分隔线图片不进入 MinIO/OCR/分片（REQ-D09）
- [ ] Markdown 保存后重切失败时返回组合错误信息且已保存数据不掩盖（REQ-E03）
- [ ] Markdown 对象键不落库，实体无 markdown 存储列（路径约定定位）（REQ-E04）
- [ ] 文件列表排除软删按创建时间倒序、回收站仅含软删按删除时间倒序（REQ-F01）
- [ ] 元数据更新仅受理 name/folderId/enableGraphBuild 三键且日志按 patch 差异记录（REQ-F03）
- [ ] 复制文件产生新记录（名称加 " (copy)"）且业务元数据随复制（REQ-F04）
- [ ] 原文件下载按扩展名映射 Content-Type 与 UTF-8 文件名，对象缺失返回 404 语义（REQ-F06）
- [ ] 处理状态查询返回轮询四字段；分块预览返回前 20 块、截断 300 字符且不落库（REQ-F07）
- [ ] 元数据全量更新后 kb_chunk 冗余列同步回填且 metadata_status=revised、metadata_source=manual（REQ-G01）
- [ ] 单文件标签为替换式设置；批量打标为增量加/删并返回处理汇总（REQ-G02）
- [ ] 存量补抽仅覆盖 none/partial、不覆盖 revised，force=true 强制重抽（REQ-G03）

## 7. 测试策略

经由公共接口（FileController 26 端点 + MQ 消费入口）测试，优先级：

1. **流水线编排（IngestionPipeline 契约）**：幂等跳过三分支、状态里程碑序列、失败重试与终态、manual 分片对账——mock MinIO/Milvus/GraphStore/MessagePublisher 系统边界，解析器用小样本真实文件。
2. **格式解析路由与降级（DocumentParser 契约）**：8 格式代表性样本（含扫描 PDF、加密 Excel、GBK 文本、坏编码）逐格式断言 ParseResult 结构；LLM 增强/FFmpeg/OCR 降级路径逐一注入失败。
3. **删除清理完整性**：永久删除后五类衍生数据（分片/向量/图谱/MinIO 前缀/QA+文件级策略）零残留——真实三分存储环境人工核对（HITL）。
4. **噪声过滤与图片过滤**：OcrNoiseFilter/ImageFilter 纯函数全规则覆盖（已有测试，补管线集成断言）。
5. **元数据闭环**：更新回填 chunk 冗余列、revised 不被补抽覆盖。
