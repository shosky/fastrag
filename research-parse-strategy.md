# FastRAG「创建解析策略」页面调查：不同文档类型的策略属性是否应不同

> 调查时间：2026-08-11
> 范围：`fastrag-frontend`（Vue3 + Element Plus）+ `fastrag-backend`（Spring Boot 多模块，knowledge 模块）
> 结论速览：**当前实现只做到了「部分按文档类型差异化」，且存在多处前后端脱节与死配置；「文档类型」没有成为策略表单的一等输入，属性差异化既不完整也不闭环。**

---

## 一、前端页面调查

### 1.1 路由与页面结构

- 路由：`knowledge/:id/parse-strategy` → `views/knowledge/detail/parse-strategy.vue`（`fastrag-frontend/src/router/routes.ts:79-82`）。
- 页面本质是 **「策略列表 + 内联创建/编辑对话框」**，不是独立创建页。列表支持搜索、编辑、删除、设默认（`parse-strategy.vue:272-612`）。
- 知识库详情页通过「解析策略」入口进入（`views/knowledge/detail/index.vue:127-129`）。
- 备注：`src/mock/parse-strategy.ts` 是历史遗留的 mock 数据层（含种子策略），**全仓库无任何文件引用它**（grep 无结果），属于死代码，实际数据全部走 HTTP API。

### 1.2 表单字段清单（创建/编辑共用同一对话框）

表单初始结构（`parse-strategy.vue:47-56`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` / `description` | string | 必填 |
| `extensions` | string[]（多选+可输入） | 文件扩展名列表，选项来自 `EXTENSION_OPTIONS`（21 个：pdf/docx/doc/xlsx/xls/pptx/ppt/md/txt/csv/图片6/音频4/视频5，`types/knowledge.ts:394-421`） |
| `parseMethod` | enum | 选项仅 5 项：default/pptx/pdf/video/audio（`types/knowledge.ts:369-375`） |
| `advanced.parse` | 对象 | `tableMode`（structured/markdown/ignore）、`enablePptWholePage`、`keyframeIntervalSeconds`、`keyframeHashThreshold`、`enableDocSummary`（`types/knowledge.ts:251-262`） |
| `advanced.chunk` | 对象 | `chunkLength`（子分片长度，预设 2000/1000/500/自定义）、`overlap`、`delimiters`、`titlePrefix`、`headingPath`（`types/knowledge.ts:265-279`） |
| `llmModel` / `vlmModel` | string | 从模型 API 动态加载（`parse-strategy.vue:62-73`） |
| `enableGraphBuild` | boolean | 构建知识图谱开关 |

高级参数默认值 `DEFAULT_ADVANCED`（`types/knowledge.ts:298-316`）：chunkLength=1000、overlap=100、delimiters=["\n\n"]、tableMode=structured 等，与后端系统默认对齐。

### 1.3 页面如何感知「文档类型」？

**页面内没有「文档类型」选择器。** 文档类型只通过两个间接方式体现：

1. **`extensions` 字段（文件扩展名）**：策略通过扩展名列表与文件绑定；前端有 `EXTENSION_TO_CATEGORY` 扩展名→分类映射（document/image/audio/video，`types/knowledge.ts:432-439`），但**该映射未被策略表单使用**，仅用于文件列表展示。
2. **`parseMethod` 字段条件渲染高级参数**（唯一的「按类型给不同属性」逻辑）：
   - `parseMethod === 'pptx'` 时显示「PPT 整页解析」开关（`parse-strategy.vue:572-575`）；
   - `parseMethod === 'video' || 'audio'` 时显示「关键帧采样间隔/哈希阈值」（`parse-strategy.vue:577-583`）。

除此之外，所有类型的策略表单字段完全一致，无 OCR 开关、无 ASR 引擎选择、无 VLM 使用开关等类型化字段。

### 1.4 提交 payload 结构

- 创建：`POST /kb/{kbId}/parse-strategies`；更新：`PUT /kb/{kbId}/parse-strategies/{id}`（`api/index.ts:291-297`）。
- body 为 `ParseStrategyForm`：`{ name, description, extensions, parseMethod, advanced:{parse,chunk,index}, llmModel, vlmModel, enableGraphBuild }`（`types/knowledge.ts:353-366`）。
- 提交前校验：仅 name/description/extensions/parseMethod 必填（`parse-strategy.vue:89-94`）；扩展名冲突用**前端本地 computed** 检测并弹警告（`parse-strategy.vue:81-87, 233-245`），允许强制保存。
- `useParseStrategy` composable 封装全部 CRUD（`composables/useParseStrategy.ts:5-87`）。

---

## 二、后端调查

### 2.1 实体与表结构

- 实体 `KbParseStrategy`，表 `kb_parse_strategy`（`entity/KbParseStrategy.java:32-49`；建表 DDL `fastrag-backend/init-scripts/schema.sql:166-181`）：
  `id / kb_id / name / description / extensions(JSON) / parse_method / is_default / advanced(JSON) / llm_model / vlm_model / enable_graph_build`。
- 请求 DTO `ParseStrategyRequest`：与实体字段一一对应，**无任何校验注解**（`model/ParseStrategyRequest.java:16-25`）。
- Controller：`/api/kb/{kbId}/parse-strategies` 全套 CRUD + set-default + resolve + conflicts（`controller/ParseStrategyController.java:34-58`）。

### 2.2 后端支持的文档类型（解析方法）

**后端没有统一的「文档类型枚举」，解析方法散落多处定义：**

1. **解析方法全集（9 种）**：`DocumentParserImpl.parse()` 的 switch —— `pdf / doc / docx / pptx / xlsx / video / audio / image / default`（`parser/DocumentParserImpl.java:88-98`）。
2. **扩展名→解析方法映射** `resolveMethodByExtension`（`DocumentParserImpl.java:108-123`）：pdf→pdf、doc→doc、docx→docx、pptx→pptx、xlsx/xls→xlsx、mp4/avi/mov/mkv/flv/wmv/webm→video、mp3/wav/m4a/aac/ogg/flac/wma→audio、jpg/jpeg/png/gif/bmp/webp/tiff→image、其余（txt/md/csv）→default。
3. **文件类别 4 类**：`FileServiceImpl.detectCategory` → document / image / audio / video（`service/impl/FileServiceImpl.java:605-612`），落库到 `kb_file.category`（`entity/KbFile.java:45`）。
4. **策略模板 4 种**：`ParseStrategyTemplateController` → auto / pdf / pptx / media，每个模板仅含 `extensions + parseMethod`，**不含 advanced**（`controller/ParseStrategyTemplateController.java:40-69`）。

> 前端 `PARSE_METHOD_OPTIONS` 只有 5 项（default/pptx/pdf/video/audio，`types/knowledge.ts:369-375`），**比后端少了 doc/docx/xlsx/image 4 种**——前端无法为 Word、Excel、图片显式选择专用解析方法。

### 2.3 策略与文档类型的关联方式（后端）

- 策略与文件的关系 = **扩展名匹配**，与 category 无关：
  - 上传时 `FileServiceImpl.resolveStrategy(kbId, extension)` 按「默认+扩展名匹配 → 扩展名匹配 → 默认策略 → 第一条」选定策略（`service/impl/FileServiceImpl.java:614-648`），并写入 `kb_file.parse_strategy_id`（`FileServiceImpl.java:144-149`）。
  - 解析时 `DocumentParserImpl.parse()` 若策略 parseMethod 为空/default，则**按扩展名自动推断解析方法**（`DocumentParserImpl.java:76-83`）。
  - `ParseStrategyServiceImpl.resolveByExtension` 同样按扩展名匹配（`service/impl/ParseStrategyServiceImpl.java:82-89`）。

### 2.4 高级参数（advanced）的实际消费点

`StrategyConfigResolver` 把系统默认值 + 策略 advanced JSON 合并成类型化 `ParseStrategyConfig`（`config/StrategyConfigResolver.java:54-70, 78-146`；模型 `model/ParseStrategyConfig.java:27-64`）。各字段消费情况：

| advanced 字段 | 后端消费点 | 结论 |
|---|---|---|
| `tableMode` | `ChunkingServiceImpl.structuralChunk`：ignore 时跳过 TABLE 节点，纯表格文档（Excel 类）自动豁免（`chunking/ChunkingServiceImpl.java:246-260, 296-299`） | ✅ 有消费 |
| `keyframeIntervalSeconds` / `keyframeHashThreshold` | `DocumentParserImpl.parseVideo`：关键帧采样间隔、pHash 去重阈值（`DocumentParserImpl.java:1781-1784, 1809-1814`） | ✅ 有消费 |
| `chunk.chunkLength / overlap / titlePrefix / headingPath / delimiters` | `ChunkingServiceImpl` 结构分片与兜底分片 | ✅ 有消费 |
| `enablePptWholePage` | **无任何消费点**（仅被 Resolver 读入 `ParseStrategyConfig.java:31`） | ❌ 死配置 |
| `enableDocSummary` | **无任何消费点**（仅被 Resolver 读入 `ParseStrategyConfig.java:37`） | ❌ 死配置 |
| `vlmModel`（策略字段） | **无任何消费点**；解析器只用 `llmModel`（`DocumentParserImpl.java:177-180, 233-236, 2004-2007`），图谱构建也用 `llmModel`（`consumer/GraphBuildConsumer.java:433-436`） | ❌ 死字段 |
| `enableGraphBuild` | `FileServiceImpl.resolveDefaultGraphBuild`（`FileServiceImpl.java:190-223`） | ✅ 有消费 |
| `llmModel` | 各解析器 LLM 增强（`DocumentParserImpl.java:2056-2072`） | ✅ 有消费 |

### 2.5 后端是否按文档类型校验/约束策略字段？

**否。** `ParseStrategyRequest` 无校验注解；`ParseStrategyServiceImpl.create/update` 逐字段透传存库，不检查 parseMethod 合法性、不检查 extensions 与 parseMethod 的匹配关系、不检查扩展名是否被系统支持（`service/impl/ParseStrategyServiceImpl.java:34-63`）。后果：

- 可以创建 `extensions=[".pdf"], parseMethod="pptx"` 的合法策略——解析时按 pptx 方法去解析 PDF，直接抛异常或产生错误结果（`DocumentParserImpl.java:88-98` 的 switch 只看 parseMethod）。
- `detectConflicts` 接口**空实现**，永远返回空列表（`ParseStrategyServiceImpl.java:91-94`），与前端本地冲突检测脱节（前端 `parse-strategy.vue:81-87` 自己算）。

---

## 三、其他相关页面 / 功能点

### 3.1 上传向导（FileUploader.vue）——「按文档类型给不同参数」做得最细的地方，但全是死配置

- 上传向导按文件类型条件显示引擎配置（`views/knowledge/detail/components/FileUploader.vue:1046-1088`）：
  - 图片 → OCR 引擎（DeepSeek OCR/PaddleOCR）+ 图片视觉描述模型（VLM）；
  - 音视频 → ASR 引擎（FunASR/Whisper）；
  - 视频 → 视频策略（关键帧+ASR/仅ASR/均匀采样）+ 关键帧间隔（`FileUploader.vue:455-459, 494`）。
- 这些配置（`engineConfig`）与 `parseStrategyId`、`mediaConfig`（说话人分离、时间范围）、`language`、`encoding`、`priority`、`retryCount` 一起在 `handleStartUpload` 中 emit 给父组件（`FileUploader.vue:687-704`）。
- **但链路上传不到后端**：
  - 实际上传请求只带 `file + folderId`（`FileUploader.vue:262-270`；`api/index.ts:124-128` `uploadFile` 只传 FormData）；
  - 后端 `FileController.upload` 只接收 `file` 和 `folderId` 两个参数（`controller/FileController.java:84-86`）；
  - 父组件 `FileManager.handleUpload` 收到配置后**只转发 `processingMode` 和 `qaConfig`** 到 `processFile`，engineConfig/mediaConfig/parseStrategyId 被丢弃（`views/knowledge/detail/components/FileManager.vue:94-120`）。
- 结论：上传向导中的 OCR/ASR/视频引擎配置是**前端摆设**，用户配置了不生效；实际 OCR/ASR 引擎由后端 `OcrService`/`AsrService` 固定实现（`DocumentParserImpl.java:152, 1796, 2002`），不可配置。

### 3.2 策略模板

- `GET /api/parse-strategy-templates` 返回 4 个模板（auto/pdf/pptx/media，`ParseStrategyTemplateController.java:40-69`），仅含 extensions + parseMethod。
- 模板只在**创建知识库**时使用：`create.vue:39-47` 建库成功后自动创建一条模板策略（**不带 advanced，含 llmModel 等也不带**）；`form.vue:917-927` 创建模式用下拉选模板、编辑模式只给「管理解析策略」入口。
- `form.vue:930-946` 另有 KB 级「文件类型」勾选（文档/音频/视频/图片 4 类）——这是 KB 元数据，与解析策略无关（后端 `KnowledgeBase.parseMode` 字段仅存储，`entity/KnowledgeBase.java:52`）。

---

## 四、对比分析结论

### 4.1 当前实现是否做到了「不同文档类型 → 不同属性」？

**只做到了 30%，且不闭环：**

✅ **做到的**：
1. 前端按 `parseMethod` 条件渲染 3 个类型化参数（PPT 整页 / 关键帧间隔 / 关键帧哈希阈值，`parse-strategy.vue:572-583`），其中关键帧参数有后端消费（`DocumentParserImpl.java:1781-1784`）。
2. 后端按扩展名自动推断解析器（`DocumentParserImpl.java:108-123`），策略绑定 extensions 按扩展名匹配（`FileServiceImpl.java:614-648`）。
3. `tableMode` 对不同文档类型（纯文本/表格/PPT 等）分片行为不同（`ChunkingServiceImpl.java:296-299`）。

❌ **没做到的（差距）**：
1. **「文档类型」不是策略表单的一等输入**：表单只有自由组合的「扩展名多选 + parseMethod 下拉」，二者**无联动、无校验**，可构造出 `.pdf`+pptx 方法这类必然解析失败的策略；后端同样不校验（`ParseStrategyServiceImpl.java:34-63`；`ParseStrategyRequest.java:16-25`）。
2. **属性差异化覆盖度极低**：解析行为真正被类型影响的只有 3 个字段，且其中 2 个（`enablePptWholePage`、`enableDocSummary`）是后端死配置；`vlmModel` 也是死字段。OCR 开关、ASR 引擎、VLM 使用、说话人分离等类型化能力**完全缺失**（只在无法生效的上传向导里出现）。
3. **前端解析方法选项不全**：5 项 vs 后端 9 种（缺 doc/docx/xlsx/image），Word/Excel/图片策略只能选 default 靠后端自动推断，前端表单无法体现「专用解析方法」；`.ppt` 扩展名在前端可选，但后端 `resolveMethodByExtension` 无 `.ppt` 分支（`DocumentParserImpl.java:108-123`），会落到 default 纯文本解析——扩展名口径前后端不一致。
4. **「文档类型」概念在前后端共 5+ 处独立定义、口径漂移**：后端 switch 方法（9）、`resolveMethodByExtension` 扩展名表、`detectCategory` 4 类、前端 `EXTENSION_OPTIONS`/`EXTENSION_TO_CATEGORY`、KB 表单 fileTypeConfig、策略模板——没有统一的文档类型枚举与共享映射。
5. **上传向导的按类型引擎配置与策略体系完全割裂**：一个配置在「策略」里（传得到后端），一个在「上传向导」里（传不到后端），用户无法在同一处完成「某类型文档怎么解析」的配置。

### 4.2 该页面设计是否合理？不同类型文档创建策略时属性应该不同吗？

**结论：方向合理，落地半成品。**

- 合理之处：采用「知识库下多策略、策略按扩展名绑定解析方法与参数、上传按扩展名自动匹配」（覆盖链：上传参数 > 选中策略 > KB 默认策略 > 系统默认值，见 `parse-strategy.vue:317` tooltip 与 `StrategyConfigResolver.java:22`）——这是主流 RAG 产品的建模方式，可扩展。
- 不合理之处：
  1. **属性应当因文档类型而异，且当前差异化太少**。PDF（扫描件 OCR 兜底、版面分析）、Word/Excel（表格处理）、PPT（整页/逐页）、图片（OCR/VLM）、音视频（ASR/关键帧/说话人分离）的解析配置项本质不同，应「选类型 → 动态呈现该类型的属性」，而不是一个包含全部字段的通用表单。当前 9 种解析方法共用同一组字段，类型无关字段（分片长度/重叠等）倒是全量展示，类型相关字段却只有 3 个且 2 个不生效。
  2. **「扩展名自由组合」给了用户制造错误配置的空间**：没有文档类型/扩展名/解析方法的一致性校验，策略会静默失效或解析报错。
  3. **表单展示与后端能力脱节**：表单让你配置 PPT 整页、文档摘要、VLM，后端不消费；后端实际按扩展名推断的类型差异，前端表单又不展示（如 PDF 扫描件 OCR 兜底是硬编码行为，`DocumentParserImpl.java:146-160`，策略里没有任何开关）。

### 4.3 当前实现差距清单（可按优先级修复）

| # | 差距 | 证据 |
|---|---|---|
| 1 | 缺统一「文档类型」枚举与前后端共享的扩展名→类型/解析方法映射（含 `.ppt` 漏映射） | `DocumentParserImpl.java:108-123` vs `types/knowledge.ts:369-421` |
| 2 | 前端 parseMethod 选项缺 doc/docx/xlsx/image 4 种 | `types/knowledge.ts:369-375` vs `DocumentParserImpl.java:88-98` |
| 3 | extensions 与 parseMethod 无一致性校验（前后端都无） | `parse-strategy.vue:89-94`；`ParseStrategyRequest.java:16-25`；`ParseStrategyServiceImpl.java:34-63` |
| 4 | `enablePptWholePage`、`enableDocSummary`、`vlmModel` 为死配置/死字段 | `StrategyConfigResolver.java:100,103`；`ParseStrategyConfig.java:31,37`；`DocumentParserImpl.java`（vlmModel 无引用） |
| 5 | `detectConflicts` 后端空实现 | `ParseStrategyServiceImpl.java:91-94` |
| 6 | 上传向导 engineConfig/mediaConfig/parseStrategyId 传不到后端 | `FileUploader.vue:262-270, 687-704`；`FileManager.vue:94-120`；`FileController.java:84-86` |
| 7 | 模板与自动创建策略不带 advanced | `ParseStrategyTemplateController.java:40-69`；`create.vue:39-47` |
| 8 | mock/parse-strategy.ts 死代码 | 全仓库无引用 |

### 4.4 建议（简要）

1. **引入统一「文档类型」概念**：前后端共享一份「文档类型 ↔ 扩展名列表 ↔ 默认解析方法 ↔ 可用属性集」映射；表单改为「多选文档类型（PDF/Word/Excel/PPT/文本/图片/音频/视频）→ 自动带出扩展名与默认解析方法」，并随类型动态渲染该类型专属属性。
2. **按类型分区表单**：分片参数（通用）、表格处理（PDF/Word/Excel/PPT）、OCR/VLM（图片/扫描件）、ASR/关键帧/说话人分离（音视频），非本类型的字段不展示。
3. **接通或删除死配置**：`enablePptWholePage`、`enableDocSummary`、`vlmModel` 要么实现消费点，要么从表单移除；上传向导引擎配置要么打通到后端，要么删除，避免「配了不生效」。
4. **后端补校验与冲突检测**：校验 parseMethod 枚举、扩展名与 parseMethod 匹配、扩展名被系统支持；实现 `detectConflicts`。
5. **模板补齐 advanced**，让 KB 创建时的自动策略真正体现类型化默认参数。

---

## 五、实施记录（2026-08-11 已落地）

按 §4.4 建议完成的改动（方案：文档类型 = 解析方法；后端元数据 API 为单一事实来源；死配置移除；只改策略链路）：

**后端**
- 新增 `parser/ParseMethodRegistry.java`：「文档类型 ↔ 扩展名 ↔ 解析方法」唯一权威映射（9 种方法，含 `.ppt → pptx` 修复），`DocumentParserImpl.resolveMethodByExtension` 改为委托注册表
- 新增 `controller/ParseStrategyMetaController.java`：`GET /api/parse-strategies/meta` 返回全部方法（code/label/extensions）与 `supportedExtensions`
- `ParseStrategyServiceImpl`：创建/更新校验（parseMethod 必须为注册表方法；扩展名受支持；与生效方法兼容，default 兼容全部），实现 `detectConflicts`（返回重叠策略名）
- `ParseStrategyTemplateController`：media 模板 parseMethod 由 audio 改为 default（原模板音频+视频扩展名混用 audio 方法，与新校验冲突且解析语义错误）
- 移除死配置：`KbParseStrategy.vlmModel`、`ParseStrategyRequest.vlmModel`、`ParseStrategyDto.vlmModel`、`ParseStrategyConfig.enablePptWholePage/enableDocSummary`、`StrategyConfigResolver` 对应读取逻辑、`schema.sql`/`SchemaInitializer` 中 `vlm_model` 列

**前端**
- `types/knowledge.ts`：`ParseMethodType` 扩为 9 种；删除 `vlmModel`/`enablePptWholePage`/`enableDocSummary`/`PARSE_METHOD_OPTIONS`/`EXTENSION_OPTIONS`；新增 `ParseMethodMeta`/`ParseStrategyMeta`
- `api/index.ts`：新增 `getParseStrategyMeta()`
- `parse-strategy.vue`：表单改为「文档类型选择器 → 自动带出扩展名（按类型过滤可选集）→ 按类型渲染专属参数」（表格模式：pdf/doc/docx/xlsx；关键帧：video/audio；图片提示）；移除 VLM 模型、PPT 整页开关；表格列改显示文档类型 label
- 删除死代码 `src/mock/parse-strategy.ts`

**遗留（未做，二期）**：上传向导 engineConfig/mediaConfig 打通后端；`enablePptWholePage`/`enableDocSummary`/`vlmModel` 相关功能的实现计划；已有库中旧的「audio 方法 + 视频扩展名」策略需手动修正后保存。

## 附：关键文件索引

**前端**
- `fastrag-frontend/src/router/routes.ts:79-82` — 路由
- `fastrag-frontend/src/views/knowledge/detail/parse-strategy.vue` — 页面（表单 `:47-56`、条件渲染 `:572-583`、提交 `:228-258`）
- `fastrag-frontend/src/types/knowledge.ts` — 类型与常量（`PARSE_METHOD_OPTIONS:369-375`、`EXTENSION_OPTIONS:394-421`、`EXTENSION_TO_CATEGORY:432-439`、`DEFAULT_ADVANCED:298-316`）
- `fastrag-frontend/src/api/index.ts:283-317` — 策略 API
- `fastrag-frontend/src/composables/useParseStrategy.ts` — 数据层
- `fastrag-frontend/src/views/knowledge/detail/components/FileUploader.vue` — 上传向导（引擎配置 `:1046-1088`、实际上传 `:262-270`）
- `fastrag-frontend/src/views/knowledge/detail/components/FileManager.vue:94-120` — 上传后处理转发
- `fastrag-frontend/src/views/knowledge/create.vue:26-61` — 建库自动建策略
- `fastrag-frontend/src/mock/parse-strategy.ts` — 死代码

**后端**
- `fastrag-backend/fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/entity/KbParseStrategy.java:32-49` — 实体
- `.../model/ParseStrategyRequest.java:16-25` — 请求 DTO（无校验）
- `.../model/ParseStrategyConfig.java:27-64` — 类型化配置
- `.../config/StrategyConfigResolver.java:54-146` — 配置合并
- `.../service/impl/ParseStrategyServiceImpl.java` — 策略 CRUD（空冲突检测 `:91-94`）
- `.../parser/DocumentParserImpl.java:76-123` — 解析方法全集与扩展名推断；`:1781-1784` 关键帧消费
- `.../chunking/ChunkingServiceImpl.java:246-299` — tableMode 消费
- `.../service/impl/FileServiceImpl.java:614-648` — 上传按扩展名匹配策略
- `.../controller/FileController.java:84-86` — 上传接口仅 file/folderId
- `.../controller/ParseStrategyTemplateController.java:40-69` — 模板
- `fastrag-backend/init-scripts/schema.sql:166-181` — 建表 DDL
