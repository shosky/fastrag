# FastRAG 解析策略页面：属性合理性 + 必填性调查

> 调查时间：2026-08-13
> 范围：`fastrag-frontend`（Vue3 + Element Plus）、`fastrag-backend`（Spring Boot，knowledge 模块）
> 关联文档：本文件是 `research-parse-strategy.md`（2026-08-11，主题为"不同文档类型策略属性是否应不同"）的后续调查，聚焦**当前页面字段是否合理、哪些必填**；旧文件中的"实施记录"是否落地已在本文件第五节逐项核对。
> 说明：线上 URL `http://localhost:3000/knowledge/{kbId}/parse-strategy` 在沙箱内被 SSRF 策略拦截无法直接访问，以下结论全部以源码（primary source）为准。

## 结论速览

1. 页面属性整体设计**合理**：文档类型（parseMethod）作为一等输入、扩展名随类型过滤、按类型渲染专属高级参数；2026-08-11 旧调查的"实施记录"所列改动（9 类解析方法、meta API、前后端校验、移除死配置）已全部落地。
2. 必填性在**前端 / 后端 / 数据库三层不一致**，是主要问题：
   - 前端必填 4 项：`name`、`description`、`extensions`、`parseMethod`（带红星）；
   - 后端只强制 `parseMethod`；`extensions` 仅在**非 null** 时校验（传 null 可绕过）；`name`/`description` 完全无校验；
   - 数据库仅 `kb_id`、`name` 为 NOT NULL，其余全部可空。
3. 两个不合理点：
   - `description` 前端必填，但后端与 DB 均可空（过约束）；
   - 关键帧两个参数在 UI 对 **video + audio** 同时展示，但后端只在 `parseVideo` 消费，对 `audio` 是死配置。

---

## 一、页面字段清单（当前实现）

创建/编辑共用同一个 Dialog（`fastrag-frontend/src/views/knowledge/detail/parse-strategy.vue`），表单初始结构见 `parse-strategy.vue:50-57`：

| 字段 | 控件 / 位置 | 默认值 | 后端消费点 |
|---|---|---|---|
| `name` | el-input，maxlength 50（`parse-strategy.vue:499-505`） | `''` | 仅存储/展示；解析器不消费 |
| `description` | textarea，maxlength 200（`parse-strategy.vue:508-516`） | `''` | 仅存储/展示 |
| `parseMethod`（文档类型） | el-select，选项来自 `GET /api/parse-strategies/meta`（`parse-strategy.vue:519-533`；`api/index.ts:284-287`） | `'default'` | `DocumentParserImpl.parse()` 的 switch 决定解析器（`DocumentParserImpl.java:93-103`） |
| `extensions` | el-select multiple，随 parseMethod 过滤（`parse-strategy.vue:536-550`；过滤逻辑 `90-105`） | `[]`（切换类型时自动填充该类型扩展名集，`97-100`） | 上传/解析时按扩展名匹配策略（`ParseStrategyServiceImpl.resolveByExtension`，`service/impl/ParseStrategyServiceImpl.java:83-90`） |
| `llmModel` | el-select，可清空（`parse-strategy.vue:731-742`；模型列表来自 `getModels({purpose:'LLM'})`，`62-69`） | `''` | `DocumentParserImpl`：doc/docx/txt/md 增强（`171-173, 227-229, 281-283`）、Excel 复杂表头（`1055, 1172-1177`）、音视频 ASR 文本（`2036-2038`）、图片 OCR（`2068-2070`）；模型配置解析 `resolveLlmConfig`（`2118-2134`） |
| `advanced.parse.tableMode` | 仅 pdf/doc/docx/xlsx 显示（`parse-strategy.vue:745-759`，`TABLE_METHODS` 见 `108`） | `'structured'`（`types/knowledge.ts:379`） | `ChunkingServiceImpl`：`tableMode=ignore` 跳过表格节点、纯表格文档自动豁免（`chunking/ChunkingServiceImpl.java:503-545, 669`） |
| `advanced.parse.keyframeIntervalSeconds` | video/audio 显示（`parse-strategy.vue:761-767`，`KEYFRAME_METHODS` 见 `110`） | `null`（后端兜底 10s） | **仅** `parseVideo`（`DocumentParserImpl.java:1790-1794`）；`parseAudio` 不消费 |
| `advanced.parse.keyframeHashThreshold` | 同上 | `null`（后端兜底 10） | **仅** `parseVideo`（`DocumentParserImpl.java:1795-1796`） |
| `advanced.chunk.strategy` + 参数（chunkLength/overlap/delimiters/titlePrefix/headingPath/parentMaxChunkLength/parentAggLevel/semanticThreshold/embeddingModel） | 卡片选择器（`parse-strategy.vue:561-585`），按所选策略渲染参数（`591-726`） | `DEFAULT_ADVANCED`（`types/knowledge.ts:377-398`：chunkLength=1000、overlap=100、delimiters=["\n\n"] 等） | `ChunkingServiceImpl`：规则分块（`70-74`）、递归分块（`83-92`）、语义分块（`225-282`）、结构分块与 titlePrefix/headingPath（`987-1079`）；父分片聚合（`storage/StorageServiceImpl.java:65-108`，`chunking/ParentChunkAssembler.java:39-54`） |
| `advanced.index.embedFields` | **不展示**（`parse-strategy.vue:773-774` 注释"索引配置二期开放"） | `['content','headingPath','fileName']`（`types/knowledge.ts:396`；`model/ParseStrategyConfig.java:70`） | `StorageServiceImpl.java:314, 349-358`（一期固定策略，不随策略配置变化） |

说明：`isDefault`、`kbId`、`createdAt`、`updatedAt` 不是表单字段（`isDefault` 通过列表"设默认"操作维护，`ParseStrategyServiceImpl.java:74-80`）。

---

## 二、必填性三层对照

前端规则：`parse-strategy.vue:124-129`（el-form rules，`required: true` → 页面红星）。
后端校验：`ParseStrategyServiceImpl.validate`（`ParseStrategyServiceImpl.java:118-140`）；请求 DTO 无任何校验注解（`model/ParseStrategyRequest.java:16-25`）。
数据库：`fastrag-backend/init-scripts/schema.sql:169-182`（`kb_parse_strategy` 建表）。

| 字段 | 前端必填？ | 后端必填？ | DB 必填？ | 是否一致 |
|---|---|---|---|---|
| `name` | 是（`parse-strategy.vue:125`；maxlength 50） | **否**（validate 不检查；空串也能入库） | 是（NOT NULL VARCHAR(128)，`schema.sql:172`） | 不一致：后端缺校验；前端上限 50 vs DB 128 |
| `description` | **是**（`parse-strategy.vue:126`；maxlength 200） | 否 | 否（VARCHAR(256) 可空，`schema.sql:173`） | 不一致：前端过约束 |
| `parseMethod` | 是（`parse-strategy.vue:128`） | 是（非空且必须是注册表内方法，`ParseStrategyServiceImpl.java:119-124`） | 否（DEFAULT `'default'`，`schema.sql:175`） | 基本一致（DB 用默认值兜底） |
| `extensions` | 是（`parse-strategy.vue:127`） | **半强制**：非 null 时校验非空/受支持/与 parseMethod 兼容（`ParseStrategyServiceImpl.java:125-139`）；传 **null 直接跳过** | 否（JSON 可空，`schema.sql:174`） | 不一致：null 可绕过后端校验，与前端"至少选一个"矛盾 |
| `llmModel` | 否 | 否（create 透传 null，`ParseStrategyServiceImpl.java:45`） | 否（`schema.sql:178`） | 一致 |
| `advanced`（含全部子字段） | 否（有默认值） | 否 | 否（`schema.sql:177`） | 一致 |
| `isDefault` | 非表单字段 | 服务端控制；默认策略禁删（`ParseStrategyServiceImpl.java:67-80`） | 否（DEFAULT 0，`schema.sql:176`） | 一致 |

> 注：`update` 为部分更新语义（`ParseStrategyServiceImpl.java:51-64`），仅更新非 null 字段；前端始终提交完整表单，因此页面路径不受影响。

---

## 三、字段合理性逐项分析

### 合理的设计

1. **`parseMethod` + `extensions` 联动，选项来自后端 meta API**：9 种文档类型（default/pdf/doc/docx/pptx/xlsx/video/audio/image，`parser/ParseMethodRegistry.java`）由后端单一事实源下发（`controller/ParseStrategyMetaController.java`），前端不再各自维护清单（`types/knowledge.ts:245, 443-450`），消除了旧调查指出的前后端口径漂移（含 `.ppt → pptx` 修复，注册表 PPTX 扩展名含 `.ppt`）。
2. **按文档类型渲染专属参数**：tableMode（pdf/doc/docx/xlsx）、关键帧（video/audio）、图片提示（`parse-strategy.vue:745-771`），且均有后端消费点或明确占位说明。
3. **`llmModel` 可选且真实消费**：在文档、表格、音视频、图片四条解析路径都有 `enhanceWithLlm` 调用（见第一节引用），不是死字段。
4. **分片策略卡片 + 按策略动态渲染参数**（`parse-strategy.vue:561-726`）：`rule_fixed / rule_recursive / structure_aware / semantic / parent_child` 各有专属参数面板，后端分块器对应实现齐全，默认值与后端系统默认值对齐。
5. **已移除死配置**：`vlmModel`、`enablePptWholePage`、`enableDocSummary` 在后端 Java/SQL 中已无引用（仅 `model/FileProcessRequest.java:29` 注释残留 `vlmModel` 字样，属上传引擎配置注释，与策略无关）；前端 `mock/parse-strategy.ts` 已删除。

### 不合理 / 待改进

1. **`description` 前端必填 vs 后端/DB 可空**（见第二节）：通过 API 可以创建无描述的策略，页面却强制填写。建议：要么前端放开必填，要么后端补校验并给 DB 加 NOT NULL。
2. **`extensions` 后端 null 绕过**（`ParseStrategyServiceImpl.java:125-126`）：请求体不带 `extensions` 时校验整体跳过，与前端"至少选一个"的约束矛盾，也与注释"extensions 非空"不符。建议改为必填（@NotNull/非空）。
3. **`name` 后端无校验**：空串可入库（仅 null 被 DB 拒绝），长度上限只靠 DB 128 兜底；前端 50 与 DB 128 不一致。建议后端补 @NotBlank + 统一长度上限。
4. **关键帧参数对 `audio` 是死配置**：`KEYFRAME_METHODS = ['video', 'audio']`（`parse-strategy.vue:110`）导致音频策略也显示"关键帧采样间隔/哈希阈值"，但 `parseAudio → doAsrParse`（`DocumentParserImpl.java:2007-2057`）只做 ASR，不读取这两个字段；字段仅在 `parseVideo` 生效（`1790-1796`）。建议 UI 仅对 video 显示，或为 audio 实现/明确去掉。
5. **`advanced.index.embedFields` 只存不用**：页面不展示、后端一期固定拼接 `content + headingPath + fileName`（`StorageServiceImpl.java:314, 349-358`）。属于"二期开放"占位，不算 bug，但字段存在于 payload 中容易让用户误以为可配置。
6. **前端冲突检测与后端 API 重复**：页面用本地 computed 基于已加载列表计算扩展名冲突（`parse-strategy.vue:116-122`），而后端 `detectConflicts`（`ParseStrategyServiceImpl.java:93-107`）与 `POST /kb/{kbId}/parse-strategies/conflicts`（`api/index.ts:321`）已实现但**页面未调用**。当前逻辑一致，但存在双实现漂移风险；列表不分页的前提下本地计算可接受。
7. 小细节：创建时初始 `extensions=[]`，`handleMethodChange` 仅在切换类型时自动填充扩展名（`parse-strategy.vue:97-100, 237`），用户选"自动识别（default）"后仍需手动确认扩展名——可接受，但若希望"选中类型即默认全选"，可在打开 Dialog 时初始化一次。

---

## 四、必填字段总表（答案）

**页面上带红星、真正必填的字段：`策略名称`、`策略描述`、`文档类型`、`文件扩展名`**（`parse-strategy.vue:124-129`）。

- **`name`**：必填（前端 + DB），后端缺校验 —— 三个层面中"前端=DB=必填，后端=非必填"。
- **`description`**：只有前端必填，后端/DB 都允许空 —— **建议放开**。
- **`parseMethod`**：前端/后端都必填且必须合法，DB 有默认值兜底 —— 三层一致可用。
- **`extensions`**：前端必填；后端"传了就校验、不传就放过"；DB 可空 —— **后端应补强为必填**。
- **`llmModel`、`advanced.*`（含 tableMode、关键帧、分片参数）**：全部非必填，均有默认值。

---

## 五、与旧调查（research-parse-strategy.md）实施记录的一致性核对

旧文件 §5"实施记录（2026-08-11 已落地）"逐项核对结果：**全部已落地**。

| 旧记录 | 当前证据 |
|---|---|
| 新增 `ParseMethodRegistry`，9 种方法，含 `.ppt → pptx` 修复 | `parser/ParseMethodRegistry.java` 枚举 9 项，PPTX 含 `.ppt`；`DocumentParserImpl.java:114-116` 委托注册表 |
| 新增 `GET /api/parse-strategies/meta` | `controller/ParseStrategyMetaController.java`；前端 `api/index.ts:284-287` |
| Service 校验 parseMethod / extensions / 兼容性，实现 `detectConflicts` | `ParseStrategyServiceImpl.java:118-140`（校验）、`93-107`（冲突检测，已非空实现） |
| media 模板 parseMethod 由 audio 改为 default | `controller/ParseStrategyTemplateController.java:63-67` |
| 移除 `vlmModel` / `enablePptWholePage` / `enableDocSummary`（含 schema 的 vlm_model 列） | 后端 Java/SQL 全仓无引用（`rg` 无结果，仅 `FileProcessRequest.java:29` 注释残留字样）；`KbParseStrategy.java` / `ParseStrategyRequest.java` / `ParseStrategyDto.java` / `ParseStrategyConfig.java` 均无这些字段 |
| 前端 parseMethod 扩为 9 种、删除旧常量、新增 meta 类型 | `types/knowledge.ts:245`（`ParseMethodType` 9 项）、`443-457`（`ParseMethodMeta`/`ParseStrategyMeta`）；`PARSE_METHOD_OPTIONS`/`EXTENSION_OPTIONS`/`vlmModel` 等已无引用 |
| 表单改为"文档类型选择器 → 自动带出扩展名 → 按类型渲染专属参数" | `parse-strategy.vue:90-105, 519-550, 745-771` |
| 删除 `src/mock/parse-strategy.ts` | 文件不存在（`Test-Path` = False） |

旧调查标注的二期遗留（上传向导 engineConfig/mediaConfig 打通、OCR/ASR/VLM 能力开放、索引配置开放等）仍在，但不影响本页字段的合理性与必填性结论。

---

## 六、建议（按优先级）

1. 后端把 `name`（@NotBlank + 长度）、`extensions`（非空必填）补进校验，统一长度上限（50 或 128 取一）。
2. `description` 三层统一：建议放开前端必填（或后端 + DB 同步加 NOT NULL）。
3. 关键帧参数仅对 `video` 展示，或给 `audio` 明确去语义化。
4. 前端冲突检测改为调用后端 `/conflicts` 接口，消除双实现。
5. `advanced.index.embedFields` 在二期开放前可从表单 payload 中省略（后端有默认值兜底）。

---

## 附录：关键文件索引

**前端**
- `fastrag-frontend/src/views/knowledge/detail/parse-strategy.vue` — 页面与表单（rules `124-129`、字段 `499-556`、分片参数 `561-726`、高级参数 `728-780`）
- `fastrag-frontend/src/types/knowledge.ts` — `ParseMethodType:245`、`ParseStrategyForm:431-440`、`ParseMethodMeta:443-450`、`DEFAULT_ADVANCED:377-398`
- `fastrag-frontend/src/api/index.ts:284-321` — meta / CRUD / resolve / conflicts
- `fastrag-frontend/src/composables/useParseStrategy.ts` — 数据层（含未使用的 detectConflicts 封装）

**后端**
- `.../knowledge/entity/KbParseStrategy.java` — 实体（无 vlm 等字段）
- `.../knowledge/model/ParseStrategyRequest.java:16-25` — 请求 DTO（无校验注解）
- `.../knowledge/service/impl/ParseStrategyServiceImpl.java` — 校验 `118-140`、create/update `35-64`、冲突 `93-107`
- `.../knowledge/parser/ParseMethodRegistry.java` — 9 类方法 + 扩展名映射 + 兼容性校验
- `.../knowledge/controller/ParseStrategyMetaController.java` — meta API
- `.../knowledge/parser/DocumentParserImpl.java` — 解析 switch `93-103`、关键帧消费 `1790-1796`、parseAudio `2007-2057`、llmModel 消费多处
- `.../knowledge/chunking/ChunkingServiceImpl.java` — tableMode `503-545`、语义分块 `225-282`、ChunkBuilder `987-1079`
- `.../knowledge/storage/StorageServiceImpl.java:65-108, 314, 349-358` — 父分片聚合、embedFields 一期固定
- `.../knowledge/model/ParseStrategyConfig.java` — 类型化配置（tableMode 默认 structured、关键帧、embedFields）
- `fastrag-backend/init-scripts/schema.sql:169-182` — `kb_parse_strategy` 建表 DDL
