# 分片管理（chunk-management）— 需求规格

## 1. 特性与范围

分片管理特性覆盖「文档变成可检索分片」之后的全生命周期：五族分片算法（规则 / 递归 / 语义 / 时间分段 / 结构感知）把解析产物切分为知识分片，父子装配器把子分片按标题层级聚合为父分片，摄取管线把分片落库并向量化，用户通过分片接口查看、手工创建、编辑、删除（单个/批量）分片，或在策略绑定变更后对整文件重新分片（Re-chunk）。显式排除：解析产物如何产生（归 file-ingestion 的 DocumentParser）、策略绑定与换绑校验（归 parse-strategy.ParseStrategyBindingGuard，ADR-0001）、分片向量如何被检索消费（归 retrieval）、从分片抽取图谱（归 knowledge-graph，经 ExtractionUnitAssembler 重组，本链仅在共享文件关系上交叉引用）、AI 分片的选段落库交互面（归 ai-chunking，本链只约定其写 kb_chunk 的口径）。

## 2. Out of Scope

- **解析与格式**：PDF/DOCX/音视频等 8 类格式如何解析出文本/结构节点/时间分段——归 file-ingestion（DocumentParser）。本链只消费 `ParseResult{text, nodes, segments}`（ChunkingServiceImpl.java:31-33 输入类型 ‹CERTAIN›）。
- **策略配置的存储与合并**：chunkLength/overlap/delimiters/tableMode 等参数如何从策略 advanced JSON 合并——归 parse-strategy（StrategyConfigResolver）。本链只声明「分片算法经其读取参数」这一依赖（ChunkingServiceImpl.java:49 ‹CERTAIN›）。
- **换绑合法性**：re-chunk 请求携带 strategyId 时的归属校验、元数据接口拒绝改绑——归 parse-strategy.ParseStrategyBindingGuard（已登记于 contract/interfaces.md `## parse-strategy` 节）。本链只负责重切的物理执行（删旧分片/向量→重跑管线）。
- **检索消费**：子分片向量召回、父分片上下文组装（parent_document / window）的检索侧行为——归 retrieval（KbChunkMapper.java:40-45 的 selectByFileId 注释注明消费方为上下文组装 ‹CERTAIN›）。
- **图谱构建本身**：抽取单元重组、三元组抽取、实体合并——归 knowledge-graph。本链只负责编辑/删除分片时的图谱数据清理与增量构建消息触发（ChunkServiceImpl.java:249-269、531-553 ‹CERTAIN›）。
- **AI 分片交互面**：预览/选区/应用落库的 SSE 流程——归 ai-chunking。本链只约束其应用落库时对既有 auto 分片的替换口径与手工分片保留（AiChunkServiceImpl.java:804-835 与重分片同款清理模式 ‹CERTAIN›）。
- **QA 对**：kb_qa_pair 与分片无外键关系，QA 模式文件拒绝重切（FileServiceImpl.java:382-384 ‹CERTAIN›），QA 管理归 qa-pairs。
- **权限体系**：@KbAuth 判定协议归 iam（复用，不重新定义）。

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：**知识分片（Chunk）**、**父分片（Parent Chunk）**、**子分片（Child Chunk）**、**解析策略（Parse Strategy）**、**策略绑定（Strategy Binding）**、**自动匹配（Auto Match）**、**重新分片（Re-chunk）**、**AI分片（AI Chunking）**、**向量检索（Vector Retrieval）**、**抽取单元（Extraction Unit）**（交叉引用）、**Ingestion Pipeline**。本特性是「知识分片/父分片/子分片/重新分片」四个术语的定义主体：父子两级的语义（子分片向量召回、父分片按标题聚合作上下文）由本链的算法与装配行为落实。

## 4. 功能需求

### 4.1 分片查看与查询

分片管理页的读取面：按知识库（可选按文件过滤）分页浏览分片，系统自动探测该库处于「父子模式」还是「单层模式」——存在父分片行时按父分片分页并在行内嵌其子分片，无父分片时按扁平列表分页；同时返回整库叶分片字符总量与父分片数两个聚合指标，供管理页展示规模。单分片详情与总量计数是同域的轻量读取。

- **REQ-A01**：列表接口在知识库内存在 chunkType='parent' 行时进入父子模式（mode=parent），按父分片与「无父归属的孤立子分片」混合分页，父分片行内嵌按 chunkIndex 升序的全部子分片。〔证据：ChunkServiceImpl.java:49-87 ‹CERTAIN›〕
- **REQ-A02**：父子模式下，孤立子分片（chunkType≠parent 且 parent_id 为空，单分片章节产物）与父分片按 chunkIndex 升序混排，保持文档顺序。〔证据：ChunkServiceImpl.java:61-66 ‹CERTAIN›〕
- **REQ-A03**：列表响应携带 parentCount（父分片总数）与 tokenTotal（指定库/文件全部叶分片的字符总数，排除父分片防重复统计）。〔证据：ChunkServiceImpl.java:53-59、KbChunkMapper.java:57-60 ‹CERTAIN›；「tokenTotal 实为字符数非 token 数」——命名口径为字符长度求和 ‹CERTAIN›〕
- **REQ-A04**：计数接口返回知识库分片总数且排除父分片（父分片是上下文聚合非独立分片）。〔证据：ChunkServiceImpl.java:103-107 ‹CERTAIN›〕
- **REQ-A05**：详情接口按 id 返回单个分片；分片不存在或不属于该知识库时拒绝。〔证据：ChunkServiceImpl.java:110-119 ‹CERTAIN›；拒绝实现为 RuntimeException 走全局兜底 500 语义而非 404——登记债务，见 §5〕

### 4.2 分片 CRUD 与批量操作

用户（editor+）可手工创建分片补充内容、删除错误分片或批量清理。手工创建的分片以独立 ID 段命名并与管线自动分片区分来源，使重新分片时能识别保留；删除父分片必须级联其全部子分片；删除后其余分片的索引保持文档顺序连续。

- **REQ-B01**：手工创建分片必填文件 ID 与内容，可指定插入位置（insertAfterIndex：在该索引后插入并把后续分片索引 +1）或追加到末尾（当前最大索引 +1）。〔证据：ChunkCreateRequest.java:11-18、ChunkServiceImpl.java:133-142 ‹CERTAIN›〕
- **REQ-B02**：手工创建的分片 ID 为 `{fileId}_manual_{n}`（n 为文件内递增序号），origin=manual——与管线自动分片 `{fileId}_chunk_{index}` 隔离，避免重分片重新编号时撞车。〔证据：ChunkServiceImpl.java:144-147,163 ‹CERTAIN›〕
- **REQ-B03**：删除分片时，若为父分片则级联删除其全部子分片（MySQL 行 + Milvus 向量 + 图谱数据三处同步清理）。〔证据：ChunkServiceImpl.java:289-315 ‹CERTAIN›〕
- **REQ-B04**：删除分片后，同文件中索引大于被删索引的分片索引一律 -1 保持连续；父分片索引取首个子分片位置、不参与移位。〔证据：ChunkServiceImpl.java:317-318,395-401 ‹CERTAIN›〕
- **REQ-B05**：批量删除时仅对属于本知识库的分片生效（跨库 id 静默忽略），父分片同样级联子分片，涉及文件的 chunkCount 逐一重算。〔证据：ChunkServiceImpl.java:336-344,347-359,382-385 ‹CERTAIN›；批量删除**不**做索引重排——与 REQ-B04 不对称 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——batchDelete 全路径实读无 shiftChunkIndices 调用、无解释性注释、无测试断言，「有意保留」意图不可考，现状即事实源：批量删除后允许索引空洞，列表按 chunk_index 排序不受影响；该不对称已在契约登记为已知偏离（代裁，用户可推翻）〕
- **REQ-B06**：任一写操作（创建/更新/删除/批量删除）后，所属文件的 chunkCount 重算为该文件非父分片行数。〔证据：ChunkServiceImpl.java:188,320-321,382-385,490-500 ‹CERTAIN›〕

### 4.3 手工编辑与父子联动

分片内容与附属元数据（时间戳/页码/类型/图片键）可被手工修正；内容变化是向量重嵌入与图谱重建的触发器；父子模式下编辑子分片必须同步刷新其父分片内容，保证「父分片内容 = 子分片拼接」的一致性。

- **REQ-C01**：编辑接口按字段部分更新：content、startTime、endTime、pageNumber、chunkType、imageKeys 六个字段。〔证据：ChunkServiceImpl.java:209-220 ‹CERTAIN›〕
- **REQ-C02**：content 变更时重新生成向量并「删旧插新」替换 Milvus 中的向量；embedding 不可用时置 vectorStored=0 降级为纯文本分片，不阻断编辑。〔证据：ChunkServiceImpl.java:222-240、453-477 ‹CERTAIN›〕
- **REQ-C03**：子分片内容变更后，其所属父分片内容立即重拼（子分片内容按 "\n\n" 拼接，与装配规则一致）。〔证据：ChunkServiceImpl.java:244-247,506-524 ‹CERTAIN›〕
- **REQ-C04**：content 变更时清理该分片的图谱数据（deleteChunkGraph）、重置 graphIndexed=0 与 extractionResult，并在文件图谱开关开启时触发增量图谱构建。〔证据：ChunkServiceImpl.java:249-269、531-553 ‹CERTAIN›〕

### 4.4 分片算法族

解析产物按策略 chunk.strategy 分发到五族算法：结构感知（默认兜底）、时间分段（音视频）、递归字符、语义切片、规则分块。算法只决定「怎么切」，参数一律来自策略配置（经 StrategyConfigResolver 三层合并）；任何算法失败都不得中断摄取。结构感知算法承载结构感知细节：不可切分单元整体保留、标题层级栈、tableMode=ignore 及纯表格文档豁免。

- **REQ-D01**：策略 chunk.strategy 分发：parent_child / rule_recursive / semantic / structure_aware / rule_fixed（默认）五值；音视频时间分段优先于策略值。无节点可结构切分时回退纯文本规则分块。〔证据：IngestionConsumer.java:184-236 ‹CERTAIN›〕
- **REQ-D02**：结构感知分片将不可切分单元（表格/代码块/行内与独立公式/图片）整体保留不切断，可切分段落按 chunkLength 累积切分；超长表格按行拆分且每个子片复制表头，超长代码按函数/类边界拆分（无法按边界时按行均分），公式与图片超长保持完整。〔证据：ChunkingServiceImpl.java:851-861,957-962,1005-1039,1045-1099,1125-1216 ‹CERTAIN›〕
- **REQ-D03**：结构感知分片维护 H1-H6 标题栈（headingPath）：同级替换、更深压栈、遇浅级截断子级；分片携带所属最近标题 title 与层级路径 headingPath（"第一章 > 1.1 背景"），titlePrefix 开启时把 content 未包含的祖先标题作为 Markdown 前缀注入，使分片自包含上下文。〔证据：ChunkingServiceImpl.java:916-952,1278-1414、测试 ChunkingServiceImplTest.java:142-157 ‹CERTAIN›〕
- **REQ-D04**：标题永不独占成片（延迟合并）：标题行挂起等待下一个正文节点合并；文档以标题结尾时挂起标题独立成片输出，连续标题不丢失。〔证据：ChunkingServiceImpl.java:529-536,623-629,818-835,881-887 ‹CERTAIN›〕
- **REQ-D05**：tableMode=ignore 时跳过表格节点，但纯表格文档（Excel 类，仅 HEADING+TABLE 或含表格标题型短段落）自动豁免该配置保留表格，避免数据静默丢失。〔证据：ChunkingServiceImpl.java:794-802,839-841,968-997、TableModeIgnoreTest.java:41-94 ‹CERTAIN›〕
- **REQ-D06**：落盘的相邻分片携带 overlap 尾部延续（overlap 字符），且防 overlap≥chunkLength 死循环。〔证据：ChunkingServiceImpl.java:596-608,758-770,899-912 ‹CERTAIN›〕
- **REQ-D07**：递归字符切分按分隔符优先级逐级降级，分隔符用尽回退句子边界，最后按 chunkLength 硬切；短片段贪心合并。〔证据：ChunkingServiceImpl.java:89-130,138-226 ‹CERTAIN›〕
- **REQ-D08**：语义切片拆句后批量向量化，相邻句余弦相似度低于阈值（semanticThreshold/100）断开新片、chunkLength 为硬上限；策略级模型缺省回退 KB 级模型，无模型/embedding 失败/数量不符一律回退规则分片，不中断摄入。〔证据：ChunkingServiceImpl.java:250-346 ‹CERTAIN›〕
- **REQ-D09**：音视频 ASR 时间分段按文本长度比例线性分配每片 startTime/endTime，单段超长按句边界二次拆分。〔证据：ChunkingServiceImpl.java:363-445 ‹CERTAIN›〕
- **REQ-D10**：规则分块支持 PDF 页感知（PAGE_BREAK 标记解析页码）、图片占位符 [IMAGE:key] 提取入 imageKeys、代码围栏内不识别标题。〔证据：ChunkingServiceImpl.java:455-476,558-569,646-680 ‹CERTAIN›〕

### 4.5 父子分片装配

仅当策略为 parent_child 且解析产出结构节点时启用：子分片由结构感知分片生成，父分片在**存储层**（而非分片算法层）按标题层级聚合——因为摄取管线在分片后还会按页归并图片 OCR 分片，存储层聚合保证图片分片同样进入所属章节父分片。父分片不向量化、不参与召回，仅作命中后的上下文。

- **REQ-E01**：聚合按文档顺序扫描、连续且 headingPath（可截断到聚合层级 H1/H2/H3；auto=完整路径）相同的子分片归为同一组；headingPath 为空（文档前言）视为同一组。〔证据：ParentChunkAssembler.java:56-113 ‹CERTAIN›〕
- **REQ-E02**：每组至少生成一个父分片：内容 = 组内子分片内容按 "\n\n" 拼接；组总长超 maxParentLength 时按子分片边界二次切分为多个父分片，共享标题上下文。〔证据：ParentChunkAssembler.java:121-143,146-166 ‹CERTAIN›〕
- **REQ-E03**：父分片 id = `{fileId}_parent_{n}`，chunkIndex 取组内首个子分片位置（保证列表按文档顺序）；子分片回填 parentId；父分片 vector_stored=0、不写入 Milvus。〔证据：ParentChunkAssembler.java:146-166、StorageServiceImpl.java:282-298 ‹CERTAIN›〕
- **REQ-E04**：单分片文档（<2 片）不聚合。〔证据：ParentChunkAssembler.java:60-63 ‹CERTAIN›〕
- **REQ-E05**：图片 OCR 分片与所属章节子分片聚合进同一父分片（聚合发生在存储层的理由）。〔证据：ParentChunkAssembler.java:15-17、ParentChunkAssemblerTest.java:137-152 ‹CERTAIN›〕

### 4.6 重新分片（Re-chunk）编排

策略绑定变更（ADR-0001）后的物理执行：删除该文件既有 auto 分片及其向量 → 清空文件图谱 → 文件重置为 pending → 重发摄取消息走标准「解析→分片→向量化→存储」流水线。用户手工创建（manual）的分片跨重分片保留，保留分片在管线完成后统一对账。绑定裁决（换绑校验、元数据改绑拒绝）归 parse-strategy 守卫，本域只定义物理编排与保留语义。

- **REQ-F01**：重切前校验文件状态：回收站中 / 处理中 / QA 模式的文件拒绝重切。〔证据：FileServiceImpl.java:375-384 ‹CERTAIN›〕
- **REQ-F02**：重切删除该文件全部 origin≠manual 的分片行，并按其 embeddingId 选择性删除 Milvus 向量（不按 fileId 全删，避免误删保留的 manual 分片向量）。〔证据：FileServiceImpl.java:421-449 ‹CERTAIN›〕
- **REQ-F03**：重切保留 manual 分片：行、ID、向量原地保留不重建；文件图谱数据清空后保留分片的 graphIndexed 重置为 0，随增量构建全量重建。〔证据：FileServiceImpl.java:421-469 ‹CERTAIN›〕
- **REQ-F04**：重切把文件重置 pending/progress=0/chunkCount=0 后复用原处理配置重发摄取消息（含引擎配置快照重建）；chunkCount 先由流水线写 auto 数，完成后按 auto+manual 对账修正。〔证据：FileServiceImpl.java:471-482、IngestionConsumer.java:458-461,744-782 ‹CERTAIN›〕
- **REQ-F05**：对账把保留 manual 分片的 chunkIndex 重排到本次 auto 分片之后，避免与管线从 0 重新编号的 auto 索引交错。〔证据：IngestionConsumer.java:753-778 ‹CERTAIN›〕
- **REQ-F06**：AI 分片应用落库时对既有 auto 分片采用同款「删 auto 留 manual」替换口径（以用户确认边界为唯一事实源）。〔证据：AiChunkServiceImpl.java:804-835 ‹CERTAIN›；「唯一事实源」语义引自 CONTEXT.md AI分片词条〕

### 4.7 向量与图谱同步

分片是「MySQL 行 + Milvus 向量 + 图谱 mention」三位一体的聚合：任何产生、修改、删除分片的操作必须维持三处一致，且任何一处外部系统失败都不阻断 MySQL 主流程（降级记账）。Milvus collection 按知识库隔离（`kb_{kbId}`，连字符转下划线），向量主键与 MySQL 行主键同源（embedding_id）。

- **REQ-G01**：分片落库时向量写入按知识库隔离 collection `kb_{kbId}`（连字符转下划线），向量 id 与分片 id 一致；库不存在时幂等创建（维度取 KB 配置，缺省 1024）。〔证据：ChunkServiceImpl.java:172-175、StorageServiceImpl.java:75-81、MilvusService.java:120-204、ChunkServiceImpl.java:482-485 ‹CERTAIN›〕
- **REQ-G02**：Milvus/Embedding/图谱任一外部依赖失败：warn 记账后继续（向量缺失以 vector_stored=0 落库、图谱清理失败不回滚 DB 删除），不产生部分失败回滚。〔证据：ChunkServiceImpl.java:177-181,299-315、StorageServiceImpl.java:162-164 ‹CERTAIN›〕
- **REQ-G03**：分片索引位移导致 auto 分片 id 变化时，图谱中的 chunk 节点 id 同步改名（防 MENTIONS 悬空），向量按新 id 重嵌入重插。〔证据：ChunkServiceImpl.java:410-447 ‹CERTAIN›〕
- **REQ-G04**：新增分片（graphIndexed 未提取）在文件图谱开关开启时自动触发增量图谱构建补齐；编辑内容时清理旧图谱数据后同样触发。〔证据：ChunkServiceImpl.java:190-191,249-269,531-553 ‹CERTAIN›〕
- **REQ-G05**：摄取落库时向量化输入固定为 content + headingPath + fileName 拼接（结构层级与文件名进入向量语义），embedding 失败按批重试 3 次（指数退避）后以纯文本落库。〔证据：StorageServiceImpl.java:323-360,366-378 ‹CERTAIN›〕

## 5. 非功能约束（仅代码/配置可证）

- **NFR-1（鉴权门槛）**：分片读操作要求 KB 角色 viewer+，写操作（创建/更新/删除/批量删除）要求 editor+，由 @KbAuth 切面统一判定（判定协议归 iam）。〔证据：ChunkController.java:42,51,57,63,70,77,85 ‹CERTAIN›〕
- **NFR-2（操作审计）**：创建/更新/删除/批量删除四个写端点均有 @Loggable 审计埋点。〔证据：ChunkController.java:64,71,78,86 ‹CERTAIN›〕
- **NFR-3（外部依赖降级）**：Milvus 客户端连接不可用时降级为空实现（跳过写入并 warn），不阻塞应用启动与分片操作。〔证据：MilvusService.java:71-103,120-124 ‹CERTAIN›〕
- **NFR-4（批量写入性能手段）**：摄取路径分片落库使用 MyBatis BATCH Executor 批量插入（禁用 Spring @Transactional 混用），embedding 按 32 片分批。〔证据：StorageServiceImpl.java:42,48-49,232-234 ‹CERTAIN›〕
- **NFR-5（错误语义登记债务）**：分片不存在/不属该库/文件不存在均抛 RuntimeException → 全局兜底「服务异常」500 语义，而非 404/BusinessException——与 kb-core 的 E_KB_NOT_FOUND 同款已登记债务，契约以现状为准。〔证据：ChunkServiceImpl.java:113,116,129 + GlobalExceptionHandler 兜底语义（conventions.md §5）‹CERTAIN›〕
- **NFR-6（列表响应形态）**：列表返回 Map（键 list/total/page/pageSize 与 PageResult 同名兼容，另有 mode/parentCount/tokenTotal 三个扩展键），非标准 PageResult 类型——扩展聚合指标导致，登记为形态偏离。〔证据：ChunkServiceImpl.java:57-99 ‹CERTAIN›〕
- **性能阈值**：分片列表/重切的时延与吞吐预算〔决议 2026-09-24：取「分片列表 p95 ≤ 500ms（单文件 ≤1000 分片、分页返回）；重切提交受理 p95 ≤ 2s（执行异步，HTTP 只承担入队）；单文件 1000 分片全流水线 < 10min（嵌入服务限速主导）」。理由：列表为分页查询、重切为受理式异步（入队即返回），与业界管理面读接口/异步任务受理预算一致；现状差距：已登记债务——无性能监控埋点与压测数据，阈值未经实测标定（代裁，用户可推翻）〕。
- **手工内容上限**：单分片内容业务上限〔决议 2026-09-24：取「content ≤ 50,000 字符」（手工创建与编辑同限）——覆盖常用嵌入模型单条输入的上下文安全裕度（约 8k token），远低于 DDL MEDIUMTEXT 16MB，兼顾前端编辑器渲染；现状差距：已登记债务——create/update 直写入无业务校验（ChunkServiceImpl.java:156、213），需在入口补校验（代裁，用户可推翻）〕。

## 6. 验收清单

- [ ] 存在父分片的库列表返回 mode=parent 且父分片行内嵌子分片，无父分片的库返回 mode=single（REQ-A01）
- [ ] count 与 tokenTotal 均不含父分片贡献（REQ-A03、REQ-A04）
- [ ] 手工创建的分片 id 形如 `{fileId}_manual_{n}` 且 origin=manual，自动分片 id 形如 `{fileId}_chunk_{index}`（REQ-B02）
- [ ] 指定 insertAfterIndex 创建后，后续分片索引整体 +1 且 auto 分片 id/向量/图谱 chunkId 同步重建（REQ-B01）
- [ ] 删除父分片时其全部子分片的 MySQL 行、Milvus 向量、图谱 mention 一并消失（REQ-B03）
- [ ] 批量删除跨库 id 被静默忽略，不误删他库分片（REQ-B05）
- [ ] 编辑 content 后 Milvus 中该分片向量被替换（删旧插新），父分片内容等于新子分片拼接（REQ-C02、REQ-C03）
- [ ] 编辑 content 且文件图谱开关开启后，该分片图谱数据被清理、graphIndexed 归零并收到增量图谱构建消息（REQ-C04）
- [ ] tableMode=ignore 的混合文档不含表格内容，同配置的纯表格文档（Excel 类）表格数据完整保留（REQ-D05）
- [ ] 标题行永不单独成片（文档以标题结尾的场景除外，此时挂起标题独立成片）（REQ-D04）
- [ ] parent_child 策略 + 结构节点输入时，子分片 parentId 指向 `{fileId}_parent_{n}`，父分片 vector_stored=0 且不在 Milvus 中（REQ-E01、REQ-E03）
- [ ] 超长章节按 maxParentLength 在子分片边界切成多个父分片，共享同一 headingPath（REQ-E02）
- [ ] 重切后 auto 分片全部换新，manual 分片行与向量原样保留，且其 chunkIndex 重排到 auto 之后、chunkCount=auto+manual（REQ-F02、REQ-F03、REQ-F04、REQ-F05）
- [ ] 回收站/处理中/QA 模式文件请求重切被拒绝（REQ-F01）
- [ ] Milvus 不可用时创建/编辑/删除分片仍成功（MySQL 主流程不回滚），vector_stored 如实反映（REQ-G02）
- [ ] 父子模式下孤立子分片与父分片按 chunkIndex 升序混排保持文档顺序（REQ-A02）
- [ ] 分片不存在或不属于该库时详情请求被拒（REQ-A05 现状为全局兜底 500 语义）
- [ ] 删除中间索引分片后其后分片索引一律 -1 保持连续，父分片索引不参与移位（REQ-B04）
- [ ] 任一写操作（创建/更新/删除/批量删除）后所属文件 chunkCount 重算为非父分片行数（REQ-B06）
- [ ] 编辑接口仅 content/startTime/endTime/pageNumber/chunkType/imageKeys 六字段部分更新（REQ-C01）
- [ ] chunk.strategy 五值分发正确，音视频时间分段优先于策略值，无节点可结构切分时回退规则分块（REQ-D01）
- [ ] 表格/代码块/公式/图片等不可切分单元整体保留，超长表格按行拆分且每个子片复制表头（REQ-D02）
- [ ] 分片携带最近标题 title 与 headingPath，titlePrefix 开启时祖先标题注入 Markdown 前缀（REQ-D03）
- [ ] 相邻分片携带 overlap 尾部延续，overlap≥chunkLength 不产生死循环（REQ-D06）
- [ ] 递归切分按分隔符优先级逐级降级，用尽后回退句界再硬切，短片段贪心合并（REQ-D07）
- [ ] 语义切片在无模型/embedding 失败/数量不符时回退规则分片，不中断摄入（REQ-D08）
- [ ] ASR 时间分段按文本长度比例分配每片起止时间，单段超长按句边界二次拆分（REQ-D09）
- [ ] 规则分块的 PDF 页感知、[IMAGE:key] 提取入 imageKeys、代码围栏内不识别标题均生效（REQ-D10）
- [ ] 单分片文档（<2 片）不聚合产生父分片（REQ-E04）
- [ ] 图片 OCR 分片与所属章节子分片聚合进同一父分片（REQ-E05）
- [ ] AI 分片应用落库对既有 auto 分片采用同款「删 auto 留 manual」替换口径（REQ-F06）
- [ ] 分片向量写入按 `kb_{kbId}` 隔离 collection、向量 id 与分片 id 一致，库不存在时幂等创建（REQ-G01）
- [ ] 索引位移导致 auto 分片 id 变化时，图谱 chunk 节点 id 同步改名、向量按新 id 重嵌入重插（REQ-G03）
- [ ] 新增分片在文件图谱开关开启时自动触发增量图谱构建补齐（REQ-G04）
- [ ] 向量化输入固定为 content+headingPath+fileName 拼接，embedding 失败按批重试 3 次后纯文本落库（REQ-G05）

## 7. 测试策略

经由公共接口（ChunkService 七方法、ChunkingService 五算法、ParentChunkAssembler.assemble、re-chunk 编排入口）测试，优先级：

1. **父子装配语义**（ParentChunkAssembler 纯函数，无外部依赖，最高性价比）：连续同路径聚合、超长二次切分、aggLevel 截断、空路径归组、单分片不聚合——现有 7 个单测已覆盖主干（ParentChunkAssemblerTest.java:16-153），补 aggLevel=H1/H2/H3 用例。
2. **算法族边界**（ChunkingServiceImpl + mock StrategyConfigResolver/EmbeddingService，mock 仅在系统边界）：tableMode 豁免、标题延迟合并、overlap 死循环防护、语义切片三重回退、时间比例分配——现有 ChunkingServiceImplTest（21 例）+ TableModeIgnoreTest（5 例）覆盖主干。
3. **三库一致性**（ChunkService，mock Milvus/Embedding/GraphStore/MessagePublisher——均为真缝/系统边界）：删除级联三分区清理、编辑重嵌入删旧插新、索引位移的图谱改名、向量失败降级记账——**现状无测试，为最大缺口**（ChunkServiceImpl 无对应测试类）。
4. **重切编排**（FileServiceImpl.reChunkFile + reconcileManualChunks）：manual 保留、向量选择性删除、对账重排、状态守卫——现状无测试，缺口。
5. **HITL 人工确认**：父分片聚合层级的产品语义（H1/H2/H3 截断观感）、纯表格文档判定的字符阈值（100/1000，ChunkingServiceImpl.java:983-987）是否符合真实 Excel 解析样本。
