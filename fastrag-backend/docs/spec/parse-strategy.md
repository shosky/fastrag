# 解析策略（parse-strategy）— 需求规格

## 1. 特性与范围

解析策略特性定义「文档如何被解析与切分」的全部配置面：知识库级命名解析策略（Parse Strategy）的 CRUD、默认策略唯一性、预定义模板与文档类型元数据、按文件扩展名的自动匹配（Auto Match）、文档级覆盖（文件级专属策略与换绑三态）、绑定-重切一致性守卫（ADR-0001：绑定变更必须触发重新分片）、扩展名冲突检测，以及策略高级配置（advanced）到运行时配置 `ParseStrategyConfig` 的三层合并解析。显式排除：摄取流水线与解析器实现（file-ingestion）、分片算法执行（chunk-management）、合规规则表的功能化（见 §2 死配置）、`kb.parse_mode`/`split_mode` 字段语义（仅存储透传，见 data.md `## kb-core`）。

## 2. Out of Scope

- **分片算法本体**：rule_fixed/rule_recursive/structure_aware/semantic/parent_child 五种分片器的执行逻辑归 chunk-management；本特性只决定「用哪个分片器与参数」（IngestionConsumer.java:189-236 消费侧证据）。
- **文档解析器实现**：PDF/DOCX/OCR/ASR 等 8 格式解析逻辑归 file-ingestion；本特性只提供 parseMethod（文档类型）与 llmModel 配置。
- **kb_compliance_rule 的合规校验功能**：该表有 DDL、实体、mapper 但全仓零业务消费者（grep 实测，见 data.md），不构成任何功能需求；任何「合规检查」需求请求应被拒绝并引导至数据治理/敏感词特性。
- **AI分片 工作流**（预览→OCR→跨页合并→LLM 分片）：独立特性，仅其落库环节消费本特性的文件级策略保存接口。
- **文件上传/回收站/移动复制**：FileController 的文件生命周期端点归 file-ingestion；本特性只覆盖其中的策略绑定语义（re-chunk/strategy/preview 三端点的绑定面）。
- **系统级配置项管理**（general_chunk_size 等 SysConfig 键的 CRUD）：归 platform-config；本特性只消费。

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：**解析策略（Parse Strategy）**（表 kb_parse_strategy）、**策略绑定（Strategy Binding）**（知识库级默认绑定 + 文档级覆盖两级；变更绑定必然触发重新分片）、**自动匹配（Auto Match）**、**重新分片（Re-chunk）**。本特性是上述术语的定义主体。

## 4. 功能需求

### 4.1 策略 CRUD 与引用计数

叙述：每个知识库维护自己的命名解析策略集合；策略管理界面是用户定义「什么文件怎么切」的主入口。文件级专属策略（file_id 非空）是文档级覆盖的载体，不进入管理列表。列表附带的引用计数让用户在删除前看到影响面。

- **REQ-A01**：策略列表仅返回所属知识库的知识库级策略（file_id 为空），每条附带引用该策略的未删除文件数 fileCount。〔证据：ParseStrategyServiceImpl.java:28-40 ‹CERTAIN›〕
- **REQ-A02**：获取单条策略详情同样限定知识库级（file_id 为空），查无时返回「解析策略不存在」的资源不存在错误。〔证据：ParseStrategyServiceImpl.java:295-303 ‹CERTAIN›〕
- **REQ-A03**：创建策略时必填名称（≤50 字符）与至少一个受支持扩展名，解析方法（parseMethod）必填且必须在文档类型注册表内；创建的策略一律非默认（is_default=0）。〔证据：ParseStrategyServiceImpl.java:63-79、255-274 ‹CERTAIN›〕
- **REQ-A04**：更新策略为部分更新语义——请求中为 null 的字段沿用现有值；解析方法与分片参数按「请求优先、否则沿用」的生效值校验。〔证据：ParseStrategyServiceImpl.java:81-102 ‹CERTAIN›〕
- **REQ-A05**：默认策略不允许删除；删除非默认策略不做引用阻断，被引用文件随后回退扩展名自动匹配（fileCount 用于删除前提示影响面）。〔证据：ParseStrategyServiceImpl.java:104-109、49-52 ‹CERTAIN›〕

### 4.2 默认策略、模板与元数据

叙述：默认策略是自动匹配的兜底终点；「设为默认」必须维持知识库内默认策略的唯一性。模板与元数据端点为前端表单提供唯一权威口径，避免前后端各自维护扩展名/解析方法清单导致漂移。

- **REQ-B01**：将某策略设为默认时，先清除该知识库全部现有默认标记，再置目标策略为默认，保证任一时刻知识库级默认策略至多一个。〔证据：ParseStrategyServiceImpl.java:111-121 ‹CERTAIN›〕
- **REQ-B02**：系统提供预定义策略模板（通用文档/PDF 专用/PPT 专用/音视频四种，含适用扩展名与 parseMethod），供创建策略时作为基线。〔证据：ParseStrategyTemplateController.java:38-69 ‹CERTAIN›〕〔决议 2026-09-24：模板四分类维持现状——通用文档/PDF/PPT/音视频对应办公文档四大主流形态，扩展名集与 ParseMethodRegistry 对齐；被否方案不可考，后续如需新增走模板列表增项（代裁，用户可推翻）〕
- **REQ-B03**：提供「文档类型 ↔ 扩展名 ↔ 解析方法」唯一权威映射的元数据端点，返回全部解析方法（code/label/兼容扩展名）与全部受支持扩展名。〔证据：ParseStrategyMetaController.java:32-45、ParseMethodRegistry.java:29-39 ‹CERTAIN›〕
- **REQ-B04**：解析方法固定为九种：default（自动识别，兼容全部受支持扩展名）/ pdf / doc / docx / pptx / xlsx / video / audio / image，每种绑定专属扩展名集；扩展名统一以带点小写形式存储与比较。〔证据：ParseMethodRegistry.java:29-39、93-97 ‹CERTAIN›〕

### 4.3 扩展名自动匹配（Auto Match）

叙述：未指定文档级覆盖时，文件按扩展名在知识库级策略中匹配解析策略。这是摄取管线的默认选路机制，也是删除策略/清除覆盖后的回退终点。〔注：仓库存在两处匹配实现，优先级语义不同，见 REQ-C03、REQ-C04 与仲裁说明。〕

- **REQ-C01**：摄取路径的自动匹配按四级回退：默认策略且扩展名匹配 → 扩展名匹配（非默认）→ 默认策略（不限扩展名）→ 首个策略；全无则不绑定（运行时仅用系统默认配置）。〔证据：FileServiceImpl.java:916-952 ‹CERTAIN›〕
- **REQ-C02**：自动匹配候选仅限知识库级策略（file_id 为空），文件级专属策略永不参与自动匹配，避免文档级覆盖污染其他文件的选路。〔证据：FileServiceImpl.java:920-924、FileServiceImpl.java:492-495 ‹CERTAIN›〕
- **REQ-C03**：策略管理侧提供按扩展名解析匹配的查询口（先扩展名命中、后默认兜底），供前端上传向导/换绑对话框预览选路结果。〔证据：ParseStrategyServiceImpl.java:123-133、ParseStrategyController.java:54-55 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：ParseStrategyServiceImpl.java:128-132 vs FileServiceImpl.java:925-947）›与 REQ-C01 的语义差异为已证事实：查询口为二级（按 DB 返回序首个扩展名命中 → 默认兜底），摄取侧为四级（默认+扩展名 → 扩展名 → 默认 → 首个）；同库多策略命中同一扩展名或全部无命中时两口径可选出不同策略。2026-09-24 复核：前端当前无活跃调用（仅 useParseStrategy.ts:59-65 封装与 FileManager.vue:89 未使用解构），「上传向导/换绑对话框预览」为设计用途而非现状。
- **REQ-C04**：文件级回退语义——文件绑定的策略被删除后，该文件下次处理时自动回退到扩展名自动匹配，不阻断。〔证据：ParseStrategyServiceImpl.java:49-52、FileServiceImpl.java:207-220（requestedId 查无时 warn + 回退）‹CERTAIN›〕

### 4.4 文档级覆盖与文件级专属策略

叙述：用户可对单个文档显式指定策略（换绑对话框），或为其构造不出现在策略管理列表的文件级专属自定义策略。文件级策略以 file_id 绑定归属文件，随文件删除级联清理。

- **REQ-D01**：换绑对话框可保存文件级专属自定义策略：同一文件至多一条（upsert 语义，重复保存即更新），不出现在策略管理列表、不参与自动匹配、随文件删除级联清理。〔证据：ParseStrategyServiceImpl.java:165-201、206-211、FileServiceImpl.java:613-619 ‹CERTAIN›〕
- **REQ-D02**：文件级策略的名称/描述校验放宽：名称可空（缺省「自定义」）、不执行 50 字符截断；描述超长静默截断至 200 字符（对比知识库级的拒绝式校验，见 §5 已登记的不一致）。〔证据：ParseStrategyServiceImpl.java:174-175、162-164 javadoc ‹CERTAIN›〕
- **REQ-D03**：文件处理请求可显式指定 parseStrategyId：仅当该策略属于本知识库（知识库级或本文件的文件级）时生效，否则告警并回退自动匹配。〔证据：FileServiceImpl.java:207-220 ‹CERTAIN›〕
- **REQ-D04**：重新分片支持策略三态：strategyId 未传=沿用当前绑定；非空 id=换绑重切（校验归属，非法时拒绝）；空串=清除覆盖恢复自动匹配后重切。〔证据：FileServiceImpl.java:350-359 javadoc、405-419 实现、FileController.java:131-149 ‹CERTAIN›〕

### 4.5 绑定-重切一致性（ADR-0001）

叙述：ADR-0001（要点：解析策略绑定变更必须触发重新分片，不允许只改绑定不重切的漂移状态）是本特性的核心架构约束。原 legacy 归档已丢失，仅引要点编号。以下四道守卫是其在代码中的全部落点。

- **REQ-E01**：文件元数据更新接口不得变更策略绑定——patch 携带 parseStrategyId 且与当前绑定不同时必须拒绝，提示改用 re-chunk 接口。〔证据：FileServiceImpl.java:642-649 ‹CERTAIN›〕
- **REQ-E02**：重新分片将「换绑 + 清理旧分片/向量/图谱 + 重跑流水线」原子完成：换绑校验通过后删除非 manual 分片与其向量（manual 手工分片保留、仅重置图谱标记），再按新绑定重跑「解析→分片→向量化」。〔证据：FileServiceImpl.java:405-419（换绑）、421-469（清理）、471-478（重跑）‹CERTAIN›〕
- **REQ-E03**：重试/重切时以 kb_file.parse_strategy_id 为「当前绑定」唯一真源，覆盖处理配置快照中内嵌的历史策略值，防止换绑后重切仍用旧策略。〔证据：FileServiceImpl.java:226-247 ‹CERTAIN›〕
- **REQ-E04**：文件级自定义策略的保存与重新分片在同一接口内原子完成（保存后立即按新策略重切），不允许出现「已保存未重切」的中间态。〔证据：FileServiceImpl.java:555-560 ‹CERTAIN›〕
- **REQ-E05**：「预设分片策略」换绑（如 structure_aware）按三步解析：当前绑定已生效为该策略则不动 → 知识库内已有（KB 级优先、本文件级其次）则复用 → 均无则创建本文件级预设策略；预设绝不创建为 KB 级，防止改变其他文件选路。〔证据：FileServiceImpl.java:489-538 ‹CERTAIN›〕

### 4.6 冲突检测

叙述：多个策略的扩展名重叠会造成自动匹配歧义；策略编辑时的实时冲突提示依赖显式检测接口。

- **REQ-F01**：检测给定扩展名列表与知识库内既有策略（可排除自身）的扩展名重叠，返回冲突策略名称列表；入参为空时返回空列表。〔证据：ParseStrategyServiceImpl.java:139-154、ParseStrategyController.java:56-58 ‹CERTAIN›〕
- **REQ-F02**：冲突检测仅是提示性软约束——存在冲突不阻断策略保存/更新（无任何保存路径调用 detectConflicts 做前置拦截）。〔证据：ParseStrategyServiceImpl.java:63-102 全路径实读 ‹CERTAIN›〕〔决议 2026-09-24：维持软约束不升级——硬拦截会令既有重叠策略在编辑任一字段（含部分更新）时被连带拒绝，破坏可用性；前端已有 detectConflicts 实时冲突提示兜底告知。理由：业界同类配置面（规则/映射类）均以「警告不阻断」为默认；现状差距：无（代裁，用户可推翻）〕

### 4.7 配置合并与参数校验

叙述：策略的 advanced JSON（parse/chunk/index 三分组）最终合并为运行时配置对象 `ParseStrategyConfig`，由解析器、分片器、索引、AI分片、图谱构建统一消费；合并遵循固定优先级，且必须兼容旧平铺格式。校验面覆盖解析方法合法性、扩展名兼容性与分片参数物理约束。

- **REQ-G01**：配置合并优先级（低→高）：代码内置默认值 → 系统配置（SysConfig 的 general_chunk_size/general_chunk_overlap）→ 策略 advanced 配置。〔证据：StrategyConfigResolver.java:61-74 ‹CERTAIN›；类 javadoc 声称的「上传请求参数 > 选中策略 > KB 默认策略 > 系统默认值」四层中，请求参数层与 KB 默认层并未在本类实现（请求参数覆盖无消费落点），实际生效为上述三层。〔证据：StrategyConfigResolver.java:22 javadoc vs 61-74 实现 ‹CERTAIN›；javadoc 与实现偏离已登记 §5〕〕
- **REQ-G02**：advanced 支持新分组结构（parse/chunk/index）与旧平铺字段（chunkLength/overlap/delimiter/delimiter 单数/tableMode 等）双格式，新分组优先、旧字段仅在同组缺失时回退映射。〔证据：StrategyConfigResolver.java:82-96、122-175 ‹CERTAIN›〕
- **REQ-G03**：合并过程逐项容错：advanced JSON 解析失败或单项值非法时告警并沿用已合并值/默认值，不使解析失败阻断流水线。〔证据：StrategyConfigResolver.java:93-95、210-218 ‹CERTAIN›〕
- **REQ-G04**：策略保存时校验扩展名与解析方法的兼容性：default 兼容全部受支持扩展名，专用方法只兼容自身扩展名集，不兼容即拒绝。〔证据：ParseStrategyServiceImpl.java:223-245、ParseMethodRegistry.java:75-80 ‹CERTAIN›〕
- **REQ-G05**：分片参数物理约束：overlap 必须小于 chunkLength（chunkLength>0 时），违反即拒绝，防止相邻分片近乎全量重复稀释召回；兼容分组与平铺两种格式取值。〔证据：ParseStrategyServiceImpl.java:281-293 ‹CERTAIN›〕
- **REQ-G06**：策略级 llmModel 配置向下游生效：解析阶段用于 LLM 增强，图谱构建阶段优先于 KB 级图谱模型。〔证据：DocumentParserImpl.java:230-232、GraphBuildConsumer.java:1082-1096 ‹CERTAIN›〕

## 5. 非功能约束

- **鉴权面**：知识库级策略端点经 @KbAuth（读 viewer、写 editor）；meta 与模板端点为非 KB 作用域，仅需登录（/api/** 认证闸复用 iam.SecurityGate 契约）。〔证据：ParseStrategyController.java:38-58、ParseStrategyMetaController.java:27-45、ParseStrategyTemplateController.java:33-70 ‹CERTAIN›〕
- **审计面**：策略创建/更新/删除/设默认/冲突检测五个写操作经 @Loggable 记录操作审计。〔证据：ParseStrategyController.java:43-57 ‹CERTAIN›〕
- **降级容忍**：SysConfig 读取失败、advanced JSON 损坏、绑定的策略已被删除三种异常均降级（代码默认值/默认配置/自动匹配回退），不阻断摄取。〔证据：StrategyConfigResolver.java:179-189、93-95、FileServiceImpl.java:217-219 ‹CERTAIN›〕
- **数据一致性**：setDefault 的「先清后置」两步更新与 reChunk 的「换绑+清理+重跑」均无显式事务包裹（方法无 @Transactional 观测），异常窗口的一致性依赖回退语义兜底。〔证据：ParseStrategyServiceImpl.java:111-121、FileServiceImpl.java:369-482 实读 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：ParseStrategyServiceImpl.java:21-23 类注解仅 @Service+@RequiredArgsConstructor，:113-120 两次 updateById 各自自动提交）›窗口期内零默认策略状态为结构性事实：清空与置位之间异常即落入零默认；下游兜底已证——零默认时 resolveStrategy 降至第 4 级「首个策略」或 null（FileServiceImpl.java:939-951），null 时摄取运行时用系统默认配置。
- **性能**：策略匹配/合并的延迟与知识库策略数量上限无任何代码证据。〔决议 2026-09-24：取「自动匹配为单次查询载入后内存遍历，KB 级策略 ≤50 条时 p95 < 50ms」为性能预算；策略数量无硬上限，以管理界面可维护性建议 ≤50。理由：两实现均为一次 selectList + 内存循环（ParseStrategyServiceImpl.java:125-132、FileServiceImpl.java:921-947），该量级遍历成本可忽略；现状差距：无（代裁，用户可推翻）〕

### 5.1 已登记缺陷信号（feature-map 引述核实结果）

- **字段必填性三层不一致（CERTAIN）**：DDL 层 name VARCHAR(128) NOT NULL（schema.sql:192）；知识库级服务层 name 必填且 ≤50（ParseStrategyServiceImpl.java:256-262）；文件级服务层 name 可空缺省「自定义」且无 50 上限（:174，仅 DDL 128 兜底）。同一 description 约束在两级行为相反：知识库级超 200 拒绝（:264-266），文件级超 200 静默截断（:175）。三层口径互不一致。〔前端表单层为第四层，R3 已实证（2026-09-24 补证：fastrag-frontend/src/views/knowledge/detail/parse-strategy.vue:142-144、531、542）〕‹CERTAIN（2026-09-24 补证）›KB 级表单 name 必填且 maxlength=50、description maxlength=200、extensions/parseMethod 必填——与知识库级后端校验对齐；三层不一致的真实差异面只在文件级服务层放宽路径，而唯一文件级调用方 StrategyChangeDialog.vue:150-157 固定传 name='自定义'、description='文件自定义分片策略'（远短于上限），当前无活跃触发面（仅 API 直调可触达）。
- **死配置（CERTAIN）**：kb_compliance_rule 表（schema.sql:857-868）+ 实体（KbComplianceRule.java:24-29）+ mapper（KbComplianceRuleMapper.java）齐备，但全仓无任何 Service/Controller 读写（grep 实测，无消费者）。合规规则功能整体未实现。
- **双自动匹配实现语义不一致（CERTAIN）**：resolveByExtension（扩展名命中不限默认优先、依赖 DB 返回序，ParseStrategyServiceImpl.java:128-131）与 resolveStrategy（默认+扩展名优先的四级回退，FileServiceImpl.java:920-947）并存，同一 (kbId, ext) 可能返回不同策略。
- **扩展名大小写归一不贯穿（CERTAIN）**：ParseMethodRegistry.normalize 统一转小写（:94-97），但自动匹配两实现均直接拼接/包含比较不转小写（FileServiceImpl.java:918、ParseStrategyServiceImpl.java:129）——大写扩展名文件会失配回退。‹CERTAIN（2026-09-24 深证：FileServiceImpl.java:130、134——Hutool FileUtil.extName 保留原文件名大小写、原样入库无归一）›影响面确定：原文件名带大写扩展名（如 Report.PDF → 库存 "PDF"）在自动匹配中 equals(".PDF") vs 注册表小写 ".pdf" 失配，回退默认/首个策略；显式绑定文件不受影响。

## 6. 验收清单

- [ ] 知识库级策略列表不含任何文件级专属策略，且每条带正确 fileCount（REQ-A01）
- [ ] 创建缺名称/空扩展名列表/未知 parseMethod/不兼容扩展名的请求全部被拒绝且错误信息可定位（REQ-A03、REQ-G04）
- [ ] 任一时刻对同一知识库查询默认策略至多返回一个；set-default 后旧默认被清除（REQ-B01）
- [ ] 默认策略删除被拒绝；非默认策略删除后其绑定文件下次处理回退自动匹配（REQ-A05、REQ-C04）
- [ ] 同一文件重复保存文件级自定义策略不产生第二条记录（REQ-D01）
- [ ] 文件级策略不出现在自动匹配结果中（REQ-C02、REQ-D01）
- [ ] 通过文件元数据接口改绑 parseStrategyId 被拒绝（REQ-E01）
- [ ] re-chunk 换绑他库策略被拒绝；空串 strategyId 清除覆盖后重切走自动匹配（REQ-D04、REQ-E02）
- [ ] 重切后旧 auto 分片与向量被清理、manual 分片保留、图谱数据随分片重建（REQ-E02）
- [ ] 处理配置快照中的历史 strategyId 不覆盖文件当前绑定（REQ-E03）
- [ ] conflicts 接口对重叠扩展名返回既有策略名，excludeId 生效（REQ-F01）
- [ ] advanced 新分组与旧平铺格式均能正确合并出 ParseStrategyConfig；损坏 JSON 降级默认值不抛错（REQ-G02、REQ-G03）
- [ ] overlap ≥ chunkLength 的策略被拒绝保存（REQ-G05）
- [ ] 获取不存在的策略详情返回「解析策略不存在」资源不存在错误（REQ-A02）
- [ ] 更新策略仅覆盖请求非 null 字段，解析方法与分片参数按生效值校验（REQ-A04）
- [ ] 预定义策略模板恰为通用文档/PDF 专用/PPT 专用/音视频四种且含适用扩展名与 parseMethod（REQ-B02）
- [ ] 元数据端点返回全部解析方法与受支持扩展名的唯一权威映射（REQ-B03）
- [ ] 解析方法固定九种，扩展名统一以带点小写形式存储与比较（REQ-B04）
- [ ] 摄取路径自动匹配按四级回退执行，全无策略时不绑定（运行时仅用系统默认配置）（REQ-C01）
- [ ] 策略管理侧按扩展名解析匹配的查询口可用（先扩展名命中、后默认兜底）（REQ-C03）
- [ ] 文件级策略名称可空缺省「自定义」、描述超长静默截断至 200 字符（REQ-D02）
- [ ] 处理请求指定他库策略时告警并回退自动匹配，本库知识库级/文件级策略正常生效（REQ-D03）
- [ ] 文件级自定义策略保存与重新分片在同一接口内原子完成，无「已保存未重切」中间态（REQ-E04）
- [ ] 「预设分片策略」换绑三步解析生效，且预设绝不创建为 KB 级（REQ-E05）
- [ ] 存在扩展名冲突的策略保存/更新不被阻断（软约束现状断言）（REQ-F02）
- [ ] 配置合并优先级为内置默认→SysConfig→策略 advanced 三层（REQ-G01）
- [ ] 策略级 llmModel 对解析阶段 LLM 增强与图谱构建模型选择生效（REQ-G06）

## 7. 测试策略

经公共接口重点测：①策略 CRUD 校验面（方法/扩展名兼容/必填/长度/overlap<chunkLength）——纯逻辑， mock mapper 层即可高密度覆盖；②默认策略唯一性与删除守卫——状态机断言；③自动匹配四级回退与两实现语义差异（回归锁定现状 + 差异用例显式标注意图）；④绑定-重切一致性守卫（ADR-0001）——元数据改绑拒绝、三态换绑、真源覆盖、文件级保存即重切，mock MinIO/Milvus/GraphStore/MQ 系统边界；⑤配置合并三层优先级与双格式兼容（已有 StrategyConfigResolverTest 基础上补齐 parse/index 组与旧平铺回退）；⑥文件级策略 upsert 与级联清理。禁忌：不 mock 内部模块（ChunkingService/DocumentParser 逻辑层），系统边界仅限 MinIO/Milvus/Neo4j/RabbitMQ/SysConfig 外部面。
