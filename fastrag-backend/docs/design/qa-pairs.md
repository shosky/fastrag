# 问答对管理（qa-pairs）— 架构设计

> 总评：本特性为**全浅模块特性**——CRUD 面是 MyBatis-Plus 透传，控制器即接口；唯一有实质复杂度的单元是纯函数 Excel 解析器。因此 Module Map 如实标 (S) 与唯一的非 (S) 模块；按链铁律五份文档仍然齐备，契约章节按端点排除铁律只做排除说明与消费点登记（见 contract/interfaces.md `## qa-pairs`）。

## 1. Module Map

### QaPairModule — **(S)**，建议保持现状（不建议内联：是被检索链共享数据的写入口）
- **Responsibility**：QA 对的 CRUD、confirm 状态转移与 Excel 导入编排（预加载既有问题→委托解析→单事务批量写→统计+审计）。
- **Files**：`fastrag-knowledge/.../controller/QaPairController.java`、`service/QaPairService.java`、`service/impl/QaPairServiceImpl.java`、`mapper/KbQaPairMapper.java`、`entity/KbQaPair.java`、`model/QaCreateRequest|QaPairDto|QaImportResult|ImportRowResult.java`
- **Depth Rationale**：读/写基本是 `LambdaQueryWrapper + BaseMapper` 透传（list/create/delete 均单查询，QaPairServiceImpl.java:42-84 ‹CERTAIN›）；仅导入编排含预加载、归一化去重与事务分支（:100-190），该复杂度的大头已被 ExcelParserModule 吸走，余下是粘合代码——浅。 ‹CERTAIN›

### ExcelParserModule — 唯一非 (S) 模块
- **Responsibility**：把上传的 Excel 解析为逐行校验结果：列别名映射、单元格类型读取、行级校验（必填/优先级/状态）、问题归一化与文件内/库内去重分叉。不触库。
- **Files**：`fastrag-knowledge/.../util/ExcelQaPairImporter.java`
- **Depth Rationale**：简单接口（`parse(file, existingQuestions, overwrite) → QaImportResult`）背后吞掉了全部脏复杂度——8 列中英别名表（:41-57）、「（必填）/（可选）/*」后缀剥离（:261）、5 种单元格类型的取值策略（:288-308）、全角→半角归一化（:325-346）、10000 行截断与逐行失败明细（:137-226）。实现换 N 次（如换 EasyExcel）接口不动，符合深模块判据。 ‹CERTAIN›

### QaTemplateModule — **(S)**，建议内联进 QaPairController（与 import 端点同前缀，无独立存在理由）
- **Responsibility**：生成并下载 8 列导入模板（SXSSF 流式写 + 2 示例行）。
- **Files**：`fastrag-knowledge/.../controller/QaImportController.java`
- **Depth Rationale**：纯展示逻辑、无分支、无状态（:44-101），透传性质。 ‹CERTAIN›

## 2. Interface Contracts

（HTTP 暴露面在此以公共接口形式列出，按端点排除铁律不进 contract/interfaces.md。）

**QaPairModule 公共接口（QaPairService）** 〔QaPairService.java:6-22 ‹CERTAIN›〕
- `list(kbId[, fileId]) → List<QaPairDto>` — 按库（可选按文件）查 QA 对，创建时间倒序
- `create(kbId, QaCreateRequest) → QaPairDto` — 建草稿
- `update(kbId, id, patch: Map) → QaPairDto` — 部分更新 question/answer
- `delete(kbId, id) → void` — 物理删除
- `confirm(kbId, id) → void` — draft→confirmed
- `importFromXlsx(kbId, MultipartFile, overwrite) → QaImportResult` — 导入编排

**ExcelParserModule 公共接口（静态）** 〔ExcelQaPairImporter.java:70-72,325 ‹CERTAIN›〕
- `parse(file, existingQuestions: Set<String>, overwrite) → QaImportResult` — 纯解析零写库
- `normalizeQuestion(question) → String` — 问题归一化（去重判定的唯一口径）

**Hidden implementation**：列别名表、单元格类型分派、截断常量（80 字符）、雪花式 ID 生成（QaPairServiceImpl.java:195-201）、模板样式构建。

**Depth rationale**：QaPairService 六方法皆是单步领域动作，无隐藏状态机（confirm 的一行转移即是全部）；ExcelParser 的两个静态方法是仅有的「接口小于实现」点。

## 3. Seam Diagram

### 缝 1：KB 判定缝（复用 iam，非本特性所立）
- **What varies**：谁有权读/写该库的 QA 对。
- **Adapters**：`@KbAuth(KBRole.viewer/editor)` 切面 + KbAccessChecker（唯一判定实现，归属 iam 特性）——本特性零适配器、纯消费。
- **Stable side**：QaPairController 的 6 端点。
- **Placement rationale**：鉴权横切面全后端统一在 fastrag-security，QA 端点不自带逻辑。〔QaPairController.java:44-77 ‹CERTAIN›〕

### 缝 2：Excel 解析边界（假设缝，不立缝）
- **What varies**：Excel 解析库与表头方言。
- **Adapters**：仅 1 个（静态 ExcelQaPairImporter）——按判据不立缝；但其**纯函数性**（零 DB、全显式入参）构成天然测试缝，tdd 以其为独立切片。
- **Stable side**：`parse(...)` 签名与 QaImportResult/ImportRowResult 结构。
- **Placement rationale**：放 util 而非 service 内联，正是为了解析可脱离数据库单测。〔ExcelQaPairImporter.java:20「不涉及数据库写入，纯解析职责」‹CERTAIN›〕

### 缝 3：检索 qa 通道数据缝（共享表耦合，非缝——登记消费点）
- **What varies**：QA 对如何被检索利用（通道参数 qaRecallCount/enableKeywordMatch）。
- **Adapters**：仅 1 个读取方 RetrievalServiceImpl 直连 KbQaPairMapper——单适配器，不立缝；跨模块直引 knowledge 模块 mapper 属既有共享表模式（与 kb_chunk/kb_file 同款），登记为消费点而非新接口。
- **Stable side**：kb_qa_pair 表结构（kb_id/question/answer/status）+ 「按 kbId 取全量」的读取假设。
- **Placement rationale**：召回逻辑归检索特性所有（见 contract/interfaces.md `## retrieval`），本特性只保证表数据语义稳定。〔RetrievalServiceImpl.java:97,798-839 ‹CERTAIN›〕

## 4. Deepening Opportunities

- **QaPairModule**：把散落在 KbServiceImpl（:210-217）、FileServiceImpl（:603-611,630-634,754-756）的三套 QA 级联删除收敛为一个 `QaPairCascade` 深接口（入参 kbId+fileId，内部处理 fileId=null 孤儿与失败记账）——可同时吞掉 REQ-E01 孤儿缺陷的修复点。现状未做：级联属上游链契约（kb-core.KbLifecycleService Post 条款、file-ingestion 清理面），改动需跨链协调。
- **ExcelParserModule**：可再吞「覆盖行的写回决策」——当前解析器以人类可读 reason 魔法串 `"覆盖更新（原记录将被更新）"`（:185）传递机器决策、service 靠字符串比对分叉（QaPairServiceImpl.java:140），属于把结构化信息塞进展示字段；深化方向是 ImportRowResult 增加显式 `overwrite` 布尔。现状未做（不改源码，登记债务）。
- **QaPairModule**：confirm 与召回准入的语义联动（REQ-D04 裁决后的产品决策若为「confirmed 才召回」，在检索侧 qaChannelRecall 加一行过滤即可，无需新模块）。——**已兑现（2026-09-26）**：qa-generation 特性以 `source='manual' OR status='confirmed'` 落地该过滤（仅约束 ai 来源，manual 行为不变），见 [qa-generation.md](./qa-generation.md) 缝 4 与 [adr/0005-ai-draft-recall-admission.md](adr/0005-ai-draft-recall-admission.md)。
- 以上之外：**None**——浅模块特性无更多深化空间。

## 5. Parent Trace

| Spec REQ | Design 模块 | 说明 |
|---|---|---|
| REQ-A01~A05 | QaPairModule | CRUD/鉴权/审计 |
| REQ-B01~B03 | QaPairModule | confirm 状态机（B03 经 ExcelParserModule 落行状态） |
| REQ-C01 | QaTemplateModule | 模板下载 |
| REQ-C02~C09 | ExcelParserModule + QaPairModule | 解析/校验/归一化（解析器）+ 事务写回（服务） |
| REQ-D01~D04 | （缝 3 消费点：检索特性 RetrievalServiceImpl） | 本特性侧仅表语义稳定 |
| REQ-E01~E05 | （上游契约消费：kb-core.KbLifecycleService、file-ingestion 清理面/重切守卫） | 本特性侧仅表语义稳定 |
| REQ-A03 | QaPairModule | 2026-09-24 review 补锚：PATCH 语义部分更新仅出现字段覆盖，状态不经更新接口变更 |
| REQ-A04 | QaPairModule | 2026-09-24 review 补锚：删除为物理删除且无存在性校验（幂等） |
| REQ-B02 | QaPairModule | 2026-09-24 review 补锚：对不存在 QA 对执行确认不报错、无副作用（静默忽略） |
| REQ-C03 | ExcelParserModule + QaPairModule | 2026-09-24 review 补锚：表头列名支持中英文与「（必填）/（可选）/*」变体（大小写不敏感、重复取首列），缺问题或答案列整体拒绝并指明缺失列 |
| REQ-C04 | ExcelParserModule + QaPairModule | 2026-09-24 review 补锚：问题必填（空白行失败）、完全空白行跳过不计；单文件数据行上限 10000，超限截断并记失败明细 |
| REQ-C06 | ExcelParserModule + QaPairModule | 2026-09-24 review 补锚：优先级仅接受 1-10 整数（缺省 5），越界致该行失败——严格校验仅作用新增行，覆盖行不校验（不对称现状） |
| REQ-C08 | ExcelParserModule + QaPairModule | 2026-09-24 review 补锚：导入完成返回统计与逐行明细（行号/问题截断 80 字符/状态/原因/扩展字段），并写一条汇总审计日志 |
| REQ-D02 | （缝 3 消费点：检索特性 RetrievalServiceImpl） | 2026-09-24 review 补锚：qaRecallCount>0 时 QA 对作为独立通道参与加权融合，权重固定 0.3；本特性侧仅表语义稳定 |
| REQ-D03 | （缝 3 消费点：检索特性 RetrievalServiceImpl） | 2026-09-24 review 补锚：QA 召回按 token LIKE 命中数降序取前 N，失败降级空结果不阻断检索；本特性侧仅表语义稳定 |
| REQ-E02 | （上游契约消费：kb-core.KbLifecycleService、file-ingestion 清理面/重切守卫） | 2026-09-24 review 补锚：文件删除（回收站清除/硬删）时该文件 QA 对级联删除、失败仅告警；本特性侧仅表语义稳定 |
| REQ-E04 | （上游契约消费：kb-core.KbLifecycleService、file-ingestion 清理面/重切守卫） | 2026-09-24 review 补锚：重新分片保留该文件既有 QA 对，QA 模式文件重切请求被拒绝；本特性侧仅表语义稳定 |
| REQ-E05 | （上游契约消费：kb-core.KbLifecycleService、file-ingestion 清理面/重切守卫） | 2026-09-24 review 补锚：文件跨库移动时源库 QA 对级联删除且不随迁目标库；本特性侧仅表语义稳定 |

**对既有 ADR 的要点核对**：
- ADR-0001（解析策略绑定变更必须触发重新分片）：本特性的相容特化是「QA 模式文件拒绝重切」（FileServiceImpl.java:382-384，QA 对不受分片策略影响，不存在绑定漂移问题）——符合 ADR-0001 精神，无冲突。
- ADR-0002（图存储收敛 Neo4j）：本特性不涉图谱，无关。
- 新 ADR：**无**——未发现「难逆转+费解+真权衡」三者齐备的取舍（qa 通道权重 0.3、召回不过滤 status 属实现常量/待裁决项，不构成 ADR）。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
