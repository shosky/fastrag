# 解析策略（parse-strategy）— 架构设计

> ADR-0001（解析策略绑定变更必须触发重新分片，原 legacy 归档已丢失，仅引要点）是本特性的核心架构约束；§2 的 ParseStrategyBindingGuard 与 §5 的深化方向均以其为符合性基准——当前实现四处落点全部符合（FileServiceImpl.java:642-649、405-419、226-247、555-560 ‹CERTAIN›），无冲突。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **StrategyConfigResolver** | 把「系统默认 + 策略 advanced JSON」按固定优先级合并为类型化 `ParseStrategyConfig`，全仓唯一配置读取口 | `fastrag-module/knowledge/config/StrategyConfigResolver.java`、`model/ParseStrategyConfig.java` | **深**：吞掉 advanced 双格式（新分组/旧平铺）的全部历史包袱与逐项容错，消费方（IngestionConsumer/ChunkingService/AiChunk/预览）只见类型化对象，不知 JSON 存在 |
| **ParseMethodRegistry** | 「文档类型 ↔ 扩展名 ↔ 解析方法」唯一权威映射与全部判定函数 | `parser/ParseMethodRegistry.java` | **深（纯函数）**：九种方法与 40+ 扩展名的兼容矩阵收敛为一处；meta 端点、保存校验、扩展名推断三方共享同一事实源，增删文档类型只改枚举 |
| **ParseStrategyBindingGuard** | ADR-0001 的守卫面：绑定的合法性校验、换绑三态、重切真源修正、元数据改绑拦截 | 逻辑模块，实现在 `service/impl/FileServiceImpl.java` 私有方法（resolveRequestedStrategy :207 / reChunkFile :405 / rebuildProcessRequest :230 / update 守卫 :643） | **深但未成类**：四处守卫共同维护「绑定 ⟺ 分片结果一致」不变式，散布在文件服务的实现细节里——已登记为首要深化候选（§4） |
| **StrategyCrudModule (S)** | 知识库级策略 CRUD、默认策略唯一性、扩展名冲突检测与全部表单校验 | `controller/ParseStrategyController.java`、`service/ParseStrategyService.java`、`service/impl/ParseStrategyServiceImpl.java`、`entity/KbParseStrategy.java`、`mapper/KbParseStrategyMapper.java` | **浅**：CRUD 包装 + 校验规则直写服务层，无隐藏不变式（默认唯一性靠先清后置两步）；维持浅即可，不必抽策略域对象 |
| **StrategyMetaModule (S)** | 向前端暴露模板与文档类型元数据 | `controller/ParseStrategyTemplateController.java`、`controller/ParseStrategyMetaController.java` | **浅**：一个透传枚举、一个硬编码模板列表；模板数据建议内联进 ParseMethodRegistry 或常量类（§4） |

数据面：kb_parse_strategy（schema.sql:188-203）单表双态（file_id 空否区分知识库级/文件级）；kb_compliance_rule 死配置不入模块图（见 data.md）。

## 2. Interface Contracts

### StrategyConfigResolver

- Public interface：
  - `resolve(String strategyId) → ParseStrategyConfig` — 管线主入口：按 id 查策略合并配置，id 为 null 仅系统默认层
  - `resolve(KbParseStrategy strategy) → ParseStrategyConfig` — 预览/预设场景：对未落库的临时策略对象合并配置
- Hidden implementation：SysConfig 读取与代码兜底（general_chunk_size=1000 / general_chunk_overlap=100）、advanced JSON 的双格式解析（applyGroup/applyLegacyFields）、逐项 setIfPresent 容错——消费方全部不可见。
- Depth rationale：一个方法吞掉「配置可能来自三层、JSON 可能是两种格式、任何一项可能损坏」的组合爆炸；新增配置项只改 ParseStrategyConfig + applyGroup 一行映射。

### ParseMethodRegistry

- Public interface：
  - `of(code) → Registry|null` — 方法代码查枚举
  - `resolveByExtension(ext) → code` — 扩展名推断方法（未识别回退 default）
  - `isSupported(ext) → boolean` / `isCompatible(methodCode, ext) → boolean` — 保存校验双闸
  - `supportedExtensions() → List<String>` — meta 端点数据源
- Hidden implementation：扩展名归一化（去空格/小写/补点）、DEFAULT 与专用方法的兼容语义、去重保序。
- Depth rationale：兼容矩阵的单一事实源；前端（经 meta 端点）与后端校验共享口径，消灭「.ppt 漏映射」类漂移。

### ParseStrategyBindingGuard（逻辑契约，见 §4 深化候选）

- Public interface（语义级，实现为 FileServiceImpl 方法）：
  - `resolveRequestedStrategy(kbId, fileId, extension, requestedId) → strategyId|null` — 请求指定优先（限本库/本文件），否则自动匹配〔FileServiceImpl.java:207-220〕
  - `reChunkFile(kbId, fileId, strategyId, presetStrategy)` 的换绑段 — 换绑三态校验与绑定写回〔:369-419〕
  - `rebuildProcessRequest(file)` — 快照重建时以文件绑定覆盖历史策略值〔:230-247〕
  - `update(...)` 的 patch 守卫 — 拒绝元数据接口改绑〔:642-649〕
- Hidden implementation：四级自动匹配回退（:916-952）、预设策略三步解析与文件级创建（:497-538）、manual 分片保留的清理细节（:421-469）。
- Depth rationale：调用方（Controller/重试/复制/AI分片落库）只需表达「按这个绑定处理」，一致性与回退语义全部在缝内。

### HTTP 暴露面（实现细节，不立契约）

ParseStrategyController 8 端点（`/api/kb/{kbId}/parse-strategies`：list/get/create/update/delete/set-default/resolve/conflicts，读 viewer 写 editor + @Loggable，ParseStrategyController.java:38-58）；ParseStrategyMetaController（`/api/parse-strategies/meta`）；ParseStrategyTemplateController（`/api/parse-strategy-templates`）；FileController 3 个绑定面端点（`/{id}/re-chunk` :131、`/{id}/preview` :353、`/{id}/strategy` :373）。均为所属模块的薄包装。

## 3. Seam Diagram

### 缝 1：分片策略分派缝（真缝）

- **What varies**：分片算法（rule_fixed/rule_recursive/structure_aware/semantic/parent_child 五种及未来新增）
- **Adapters**：ChunkingService 的五个分片方法（chunkBySegments/structuralChunk/recursiveChunk/semanticChunk/chunk，IngestionConsumer.java:196-236）——2+ 个适配器，真缝
- **Stable side**：IngestionConsumer 主流程（解析 → 取 config → 按 strategy 分派 → 落库）；`ParseStrategyConfig.ChunkConfig.strategy` 是缝上的稳定词汇
- **Placement rationale**：分派放消费侧而非解析器内，使新增算法不动策略数据模型；StrategyConfigResolver 保证缝的输入类型稳定

### 缝 2：配置默认值来源缝（假设缝，不立）

- SysConfig 是唯一默认值适配器（StrategyConfigResolver.java:179-189），单适配器不满足立缝条件；若未来出现多环境默认值源再立

### 缝 3：文档衍生数据清理缝（真缝，绑定守卫的消费侧）

- **What varies**：分片数据的物理载体（Milvus 向量 / Neo4j 图谱 / MySQL 分片行）
- **Adapters**：MilvusService.deleteByIds、GraphStore.deleteFileGraph、chunkMapper.delete（FileServiceImpl.java:424-469）——2+ 适配器，真缝
- **Stable side**：reChunkFile 的「换绑 → 清理 → 重跑」骨架；清理失败降级不阻断（warn 记账）
- **Placement rationale**：清理收拢在重切流程内是 ADR-0001 的物理保证——绑定变更与旧数据清退在同一事务语义里完成

## 4. Deepening Opportunities

- **ParseStrategyBindingGuard（首要）**：把散布 FileServiceImpl 的四处守卫收拢为独立类，并吞掉自动匹配——统一 resolveStrategy（四级回退）与 resolveByExtension（两级回退）为单一实现，消灭双实现语义分叉（spec §5.1 已登记）；顺带统一扩展名大小写归一（当前仅 Registry 归一，匹配侧不归一）。
- **StrategyConfigResolver**：吞掉 javadoc 声称但未实现的「上传请求参数覆盖层」，成为真正四层合并唯一点（当前请求参数层无落点，javadoc 与实现偏离已登记）；可同时吞掉 KB 默认策略选择（现散在 resolveStrategy）。
- **StrategyCrudModule**：吞掉文件级/知识库级两套校验规则的差异（name 必填性、description 拒绝 vs 截断——spec §5.1 三层不一致的收敛点），单一校验策略 + 文件级覆盖参数。
- **StrategyMetaModule (S)**：模板硬编码数据内联进 ParseMethodRegistry 或常量类，删除 TemplateController 的独立端点（保留亦可，属可接受浅层）。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| StrategyConfigResolver | REQ-G01、G02、G03、G06 |
| ParseMethodRegistry | REQ-B03、B04、G04 |
| ParseStrategyBindingGuard | REQ-C01、C02、C04、D01、D03、D04、E01、E02、E03、E04、E05 |
| StrategyCrudModule (S) | REQ-A01~A05、B01、B02、F01、F02、G04、G05 |
| StrategyMetaModule (S) | REQ-B02、B03 |
| 缝 1 分片策略分派 | REQ-G01、G02（strategy 字段消费面） |
| 缝 3 衍生数据清理 | REQ-E02 |
| ParseStrategyBindingGuard | REQ-E04（2026-09-24 review 补锚：文件级自定义策略的保存与重新分片在同一接口内原子完成，不允许「已保存未重切」中间态） |
| StrategyCrudModule (S) | REQ-F02（2026-09-24 review 补锚：冲突检测为提示性软约束——存在冲突不阻断策略保存/更新（无保存路径前置拦截的现状口径）） |
| StrategyConfigResolver | REQ-G06（2026-09-24 review 补锚：策略级 llmModel 向下游生效——解析阶段用于 LLM 增强，图谱构建阶段优先于 KB 级图谱模型） |

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
