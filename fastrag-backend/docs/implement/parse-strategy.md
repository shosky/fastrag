# 解析策略（parse-strategy）— 实现文档

## 1. Code File Map

```
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── config/
│   └── StrategyConfigResolver.java      策略配置三层合并解析器（系统默认→策略 advanced，双格式容错）
├── parser/
│   └── ParseMethodRegistry.java         文档类型↔扩展名权威映射枚举（九方法+归一化+兼容判定）
├── controller/
│   ├── ParseStrategyController.java     /api/kb/{kbId}/parse-strategies 8 端点（CRUD/设默认/resolve/conflicts）
│   ├── ParseStrategyMetaController.java /api/parse-strategies/meta 元数据端点（透传 Registry）
│   └── ParseStrategyTemplateController.java /api/parse-strategy-templates 硬编码四模板
├── service/
│   ├── ParseStrategyService.java        策略服务接口（10 方法，含文件级 save/delete）
│   └── impl/ParseStrategyServiceImpl.java  CRUD/校验/设默认/两级匹配/冲突检测/文件级 upsert
├── entity/
│   ├── KbParseStrategy.java             kb_parse_strategy 实体（file_id 双态字段）
│   └── KbComplianceRule.java            kb_compliance_rule 实体（死配置，无消费者）
├── mapper/
│   ├── KbParseStrategyMapper.java       BaseMapper 空接口
│   └── KbComplianceRuleMapper.java      BaseMapper 空接口（死配置）
├── model/
│   ├── ParseStrategyConfig.java         类型化配置（parse/chunk/index 三分组 + 代码默认值）
│   ├── ParseStrategyRequest.java        创建/更新请求 DTO（无 Bean Validation，校验全在服务层）
│   └── ParseStrategyDto.java            响应 DTO（含 fileCount 引用计数）
├── service/impl/FileServiceImpl.java    绑定守卫宿主：resolveRequestedStrategy/resolveStrategy(4级)/
│                                        reChunkFile/resolvePresetStrategy/rebuildProcessRequest/
│                                        saveFileStrategy/update 守卫/cleanupFileData（仅本特性相关方法）
├── consumer/IngestionConsumer.java      消费侧：消息 strategyId → 解析 → configResolver.resolve → 分片分派
├── controller/FileController.java       /{id}/re-chunk、/{id}/preview、/{id}/strategy 三绑定面端点
└── parser/DocumentParserImpl.java       消费侧：strategy.llmModel → enhanceWithLlm（:230/:692/:746）
src/test/java/com/fastrag/module/knowledge/
├── config/StrategyConfigResolverTest.java          合并解析器 3 例
└── service/impl/ParseStrategyServiceImplTest.java  保存/校验 7 例
init-scripts/schema.sql                                kb_parse_strategy(:188-203)、kb_compliance_rule(:857-868)
```

镜像说明：StrategyCrudModule/StrategyMetaModule (S) 由 controller+service+entity+mapper 五层文件构成；StrategyConfigResolver、ParseMethodRegistry、ParseStrategyBindingGuard（宿主 FileServiceImpl）三个深模块各有主文件；kb_compliance_rule 两文件为死配置实体，无行为。

## 2. Interface Implementations

### StrategyConfigResolver（from contract: parse-strategy.StrategyConfigResolver）

**Logic Flow:**
1. `resolve(strategyId)`：strategyId 非空则 selectById 取实体（null 容忍），转调 `resolve(strategy)`；为空则新建空实体语义。
2. `resolve(strategy)`：① 系统默认层——`getIntConfig("general_chunk_size",1000)`、`("general_chunk_overlap",100)` 填充 chunk；② 策略层——strategy 非 null 且 advanced 有文本时 `applyAdvanced`。
3. `applyAdvanced`：JSON 转 Map；IF 解析异常 THEN warn「using defaults」RETURN（保留默认层）；ELSE 依次 `applyGroup` 合并 parse/chunk/index 三组，再 `applyLegacyFields` 补旧平铺。
4. `applyGroup`：按目标分组类型白名单取键——parse 组（tableMode/keyframeIntervalSeconds/keyframeHashThreshold）、chunk 组（strategy/chunkLength/overlap/titlePrefix/headingPath/delimiters/parentMaxChunkLength/parentAggLevel/semanticThreshold/embeddingModel）、index 组（embedFields）；每组内 `setIfPresent`（键存在且非 null 才 set，setter 抛错则 warn 跳过单项）。
5. `applyLegacyFields`：仅当 `adv` 无 "chunk" 键时映射平铺 chunkLength/overlap/strategy/delimiters/单数 delimiter（转单元素数组）/titlePrefix/headingPath/parent*/semanticThreshold/embeddingModel；仅当无 "parse" 键时映射 tableMode/keyframe*。
6. `getIntConfig`：查 SysConfig → 有值则 parseInt(trim)（异常/空→debug 记账）→ 返回兜底。
7. RETURN 类型化 ParseStrategyConfig（字段恒非 null）。

**Module Interactions:**
- Calls：KbParseStrategyMapper（selectById）、ConfigManageService.getConfig（platform 模块跨模块调用——platform 提供配置，本类只读）。
- Called by：IngestionConsumer（:189 分片分派前取配置）、FileServiceImpl.resolvePresetStrategy（:503/:518 判定策略生效分片器）、FileServiceImpl.previewChunks（:852 临时策略预览）、AiChunkServiceImpl（:1125 取 chunk 配置）。
- Shared state：kb_parse_strategy.advanced（读）；sys_config 两键（读，归 platform-config）。

**Decisions & Deviations:**
- 偏离契约已登记项：javadoc 声称四层优先级，实现仅「系统默认+策略 advanced」两层合并——上传请求参数覆盖层不存在（消息里的 processingConfig 仅复用于引擎配置，不覆盖 chunk 参数，IngestionConsumer.java:181-196 实读），KB 默认策略选择发生在绑定层（resolveStrategy）而非本类。契约按实现收敛，javadoc 待修。
- 关键取舍：advanced 为自由 JSON 透传保存（ParseStrategyServiceImpl 不解读内容，仅 overlap<chunkLength 物理校验），解读权全部集中在 Resolver——新增配置项零 DDL 变更（测试类 javadoc 明示此决策，ParseStrategyServiceImplTest.java:26-27）。
- 容错哲学：任何配置损坏都降级不阻断（三层 catch：整体 JSON、单项 set、SysConfig 读取），与全仓「降级不阻断主流程」规范一致。

**Parent Trace:** [StrategyConfigResolver.resolve] → [parse-strategy.StrategyConfigResolver] → [Design.StrategyConfigResolver] → [Spec REQ-G01、G02、G03、G06]

### ParseMethodRegistry（from contract: parse-strategy.ParseMethodRegistry）

**Logic Flow:**
1. 枚举常量承载兼容矩阵：DEFAULT 扩展名集=全部受支持；PDF(.pdf)/DOC(.doc)/DOCX(.docx)/PPTX(.pptx,.ppt)/XLSX(.xlsx,.xls)/VIDEO(7)/AUDIO(7)/IMAGE(7) 专属集。
2. `of(code)`：null 入参直接 null；否则流匹配 code。
3. `resolveByExtension(ext)`：blank → "default"；归一化后遍历非 DEFAULT 成员，命中返回其 code；遍历完未命中 → "default"。
4. `isSupported(ext)`：归一化后任一成员（含 DEFAULT）含之即 true。
5. `isCompatible(method, ext)`：方法不存在 → false；DEFAULT → isSupported；其余 → 专属集 contains(归一化 ext)。
6. `supportedExtensions()`：全成员扩展名去重保序拉平。
7. `normalize(ext)`：trim → Locale.ROOT 小写 → 无点补点。

**Module Interactions:**
- Calls：无外部依赖（纯函数枚举）。
- Called by：ParseStrategyServiceImpl（validate 校验 :227-243）、ParseStrategyMetaController（meta 端点 :34-44）。
- Shared state：无表无缓存（编译期常量）。
- 值得注意的偏离：DocumentParser 的解析方法分派与扩展名推断**不经**本注册表（解析器内部自行按 extension/parseMethod 路由，本特性链不展开）；本注册表的「推断」仅服务于保存校验与元数据暴露。

**Decisions & Deviations:**
- 归一化仅在本枚举内生效是登记债务（自动匹配两实现直接字符串比较）；契约 Invariants 已声明，修复归属 ParseStrategyBindingGuard 深化项。
- DEFAULT 兼容全集的设计使「混合文档库」可用单一策略覆盖全部类型，专用方法用于精确路由——与模板端点四模板口径一致（ParseStrategyTemplateController 的 parseMethod 值均能通过 isCompatible 自校验）。

**Parent Trace:** [ParseMethodRegistry 全部静态方法] → [parse-strategy.ParseMethodRegistry] → [Design.ParseMethodRegistry] → [Spec REQ-B03、B04、G04]

### ParseStrategyBindingGuard（from contract: parse-strategy.ParseStrategyBindingGuard；宿主 FileServiceImpl）

**Logic Flow:**
- **resolveRequestedStrategy**（process 时裁决）：1. requestedId 非空 → selectOne(kbId+id AND (file_id IS NULL OR file_id=本文件))；2. 命中 → 返回其 id；3. 未命中 → warn 回退；4. `resolveStrategy(kbId, extension)`：四级循环（默认+扩展名 → 扩展名 → 默认 → 首个），全空返回 null，异常 catch warn 返回 null。
- **reChunkFile**（本守卫仅负责守卫与换绑裁决）：1. 取文件（跨 kb 拒）→ 三守卫（回收站/processing/qa 模式均 400，E_RECHUNK_STATE_CONFLICT）；2. presetStrategy 且未显式传 strategyId → `resolvePresetStrategy` 三步（当前绑定生效→库内 KB 级优先复用→建文件级预设）；3. strategyId 三态——null 沿用；空串 `setParseStrategyId(null)`；非空 selectOne 校验归属（含本文件专属），查无 400（E_REBIND_NOT_OWNED），命中写回绑定。其后清理→重置→重发为纯编排，重切编排全文见 implement/chunk-management.md#RechunkOrchestrator。
- **rebuildProcessRequest**：processing_config 可解析 → 反序列化；损坏 → warn + 最小默认请求；无 → 最小默认请求；最后**无条件** `req.setParseStrategyId(f.getParseStrategyId())`（绑定真源覆盖快照历史值）。
- **update 守卫**：patch 含 parseStrategyId 且 String 化后 ≠ 当前绑定 → 400「…请调用 re-chunk 接口」；相等或不含 → 其余字段（name/folderId/enableGraphBuild）正常更新。
- **saveFileStrategy**：`parseStrategyService.saveFileStrategy`（upsert 文件级）→ 直接 `reChunkFile(kbId, fileId, saved.getId())`——保存与重切一个调用栈原子完成。
- **级联清理**（cleanupFileData 末段）：文件删除/重试清库时 deleteFileStrategy（按 kbId+fileId 物理删），失败 warn 不阻断。

**Module Interactions:**
- Calls：KbParseStrategyMapper、KbFileMapper、KbChunkMapper、GraphService.deleteFileGraph（knowledge-graph 契约面）、MilvusService.deleteByIds（infra）、MessagePublisher.publishIngestion（RabbitMQ `fastrag.ingestion.queue`）、KnowledgeBaseMapper（graph 开关继承）、ParseStrategyService（文件级保存/清理）、StrategyConfigResolver（预设判定）。
- Called by：FileController（re-chunk :149、strategy :376、update :638 链路）、retryFile（:344）、copyFile（跨库复制 :778 重新 resolveStrategy 绑定到目标库）、AiChunkServiceImpl（落库链路经绑定消费）。
- Shared state：kb_file.parse_strategy_id（绑定真源，本模块写、全管线读）、kb_file.processing_config（引擎配置快照，写时内嵌最终 strategyId）、kb_parse_strategy（换绑校验读、文件级写）、RabbitMQ `fastrag.ingestion.queue`（重切触发）。

**Decisions & Deviations:**
- **ADR-0001 符合性**：四处落点（元数据拒绝 :643-649、换绑+清理+重跑同流程 :405-478、真源覆盖 :245、保存即重切 :559）全部符合，无冲突、无漂移路径。
- 关键取舍 1：删除非默认策略不清理文件绑定（ParseStrategyServiceImpl.delete 仅删主表），悬空绑定依赖读路径回退（resolveRequestedStrategy 查无 warn → 自动匹配）——影响面由 fileCount 提示前置给用户，属**有意为之的最终一致**，非遗漏（javadoc :50-52 明示）。
- 关键取舍 2：重切保留 manual 分片（用户手工边界是用户数据），仅删 auto——与「重新分片会冲掉手工边界」的 AI分片 区分语义一致（CONTEXT.md 两词条分立）。
- 关键取舍 3：预设策略创建为文件级而非 KB 级（:492-495 javadoc 明示理由：KB 级会进自动匹配候选改变他文件行为）——文档级覆盖不外溢原则的守卫应用。
- 偏离登记：①双自动匹配实现并存（本守卫用 4 级版，策略管理查询用 2 级版，语义分叉见 tdd S6 HITL）；②扩展名比较不归一大小写（"."+extension 直接匹配 :918）；③预设硬编码扩展名集含 Registry 外扩展名（:531，因文件级不入自动匹配故今日无害）；④resolveStrategy 整体 try-catch 返回 null（异常静默降级为「无策略」而非报错）。

**Parent Trace:** [FileServiceImpl 六个守卫方法] → [parse-strategy.ParseStrategyBindingGuard] → [Design.ParseStrategyBindingGuard] → [Spec REQ-C01、C02、C04、D03、D04、E01~E05]

## 3. Testing

引用 tdd/parse-strategy.md 切片。**现有覆盖**：StrategyConfigResolverTest 3 例 = S3 的「新字段映射/代码默认/null 策略」三格；ParseStrategyServiceImplTest 7 例 = S5 的「advanced 透传创建/整体替换更新/空名称与空扩展名创建更新拒绝」格。**缺口**：S1 E2E、S2 守卫全片（reChunkFile/retry/元数据守卫/预设/四级回退零测试）、S4 注册表全片、S5 的默认唯一性/删除守卫/冲突检测/文件级 upsert/overlap 约束、S3 的 parse-index 组与旧平铺回退与容错降级、S6 仲裁用例——全部待实施（红绿循环未执行，从 S1 Tracer Bullet 起步）。
