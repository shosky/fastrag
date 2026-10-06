# 问答对管理（qa-pairs）— 需求规格

## 1. 特性与范围

问答对管理为知识库提供人工维护的标准「问题—答案」数据（KbQaPair，表 `kb_qa_pair`）：用户可手动创建、查看、编辑、删除 QA 对，将其标记为已确认，并可按 Excel 模板批量导入；QA 对作为检索的独立召回通道数据源被检索链消费（裁决见 4.4），其生命周期受知识库/文件删除级联与重切口径约束（4.5）。显式排除：~~文件级 QA 自动提取（问答对提取模式）的后端流水线实现~~（2026-09-26 已立项为独立特性，见 [qa-generation.md](./qa-generation.md)）、QA 对向量Embedding入库、QA 对独立检索端点、QA 对草稿/确认之外的工作流状态（如审核流）。

## 2. Out of Scope

- 文件「问答对提取」模式的提取流水线：`kb_file.processing_mode` 虽声明 `qa` 取值并随摄入消息下发，但消费端无提取分支——本特性只管辖 `kb_qa_pair` 表本身，提取流水线属 GAP（见 4.5 REQ-E03）。
- QA 对的向量化与 Milvus 入库：QA 召回是 MySQL LIKE 分词模糊匹配，不经 Embedding。
- QA 对的独立检索 API：召回发生在检索链内部通道，不新增 `/qa-search` 之类端点。
- QA 对的多级审核流（提交→审核→驳回）：状态仅 draft/confirmed 两值。
- QA 对在跨知识库移动时的迁移：移动文件即放弃源库 QA 对（级联删除，不搬家）。

## 3. 领域术语

- **知识库（Knowledge Base）**：QA 对的宿主聚合根，表 `kb`。——CONTEXT.md
- **知识分片（Chunk）**：检索最小单元；QA 对与其并列作为检索内容来源，互不隶属。——CONTEXT.md
- **重新分片（Re-chunk）**：重切会保留 QA 对（「原文档与 QA 对保留」）；QA 模式文件禁止重切。——CONTEXT.md + FileServiceImpl.java:352,382-384 ‹CERTAIN›
- **向量检索 / 混合检索（Hybrid Retrieval）**：QA 对是混合检索中「qa 通道」的数据源。——CONTEXT.md + RetrievalServiceImpl.java:792-839 ‹CERTAIN›
- **统一响应（ApiResponse）**：全部 QA 端点返回包装；业务错误 = HTTP 200 + body.code。——CONTEXT.md
- 本特性新增术语：**QA 对（KbQaPair）**——人工维护的问答知识单元，来源 manual/ai，状态 draft/confirmed〔KbQaPair.java:36-37 ‹CERTAIN›〕。

## 4. 功能需求

### 4.1 QA 对 CRUD 与查询

（叙述）QA 对以知识库为作用域挂载（`/api/kb/{kbId}/qa-pairs`），是编辑者向知识库补充「标准答案」的直接手段；可选关联源文件（fileId），让 QA 对在文件详情页可按文件筛选。查询按创建时间倒序返回全量（无分页），创建强制以草稿状态落库。

- **REQ-A01**：用户在知识库内可创建 QA 对，问题与答案必填；来源缺省 manual，可指定 source 与可选关联 fileId，创建后状态一律为 draft。〔证据：QaPairController.java:48、QaCreateRequest（@NotBlank question/answer）、QaPairServiceImpl.java:55-64 ‹CERTAIN›〕
- **REQ-A02**：用户可按知识库列出 QA 对（创建时间倒序），并支持按 fileId 过滤；读取权限为 viewer 及以上。〔证据：QaPairController.java:44-45、QaPairServiceImpl.java:42-50 ‹CERTAIN›；〔决议 2026-09-24：按现状为准——list 无分页与全后端 `PageResult{list,total,page,pageSize}` 约定不一致，无意图证据，维持现状；已登记债务（本条 + implement 偏差登记），万级库全量返回有放大风险（代裁，用户可推翻）〕〕
- **REQ-A03**：用户可部分更新 QA 对的问题与答案（PATCH 语义：仅出现的字段覆盖），状态不经更新接口变更。〔证据：QaPairServiceImpl.java:70-77 ‹CERTAIN›〕
- **REQ-A04**：用户可删除 QA 对，删除为物理删除且无存在性校验（幂等）。〔证据：QaPairServiceImpl.java:82-84 ‹CERTAIN›；‹CERTAIN（2026-09-24 深证）›「幂等」成立：delete 直调 MyBatis-Plus deleteById（:83），对不存在 id 返回影响行数 0 不抛异常（框架确定性语义），无存在性校验为代码实读〕
- **REQ-A05**：全部写操作（创建/更新/删除/确认/导入）要求 editor 及以上角色并记录操作审计日志。〔证据：QaPairController.java:46-57,69-71（@KbAuth(KBRole.editor)+@Loggable）、QaPairServiceImpl.java:179-187 ‹CERTAIN›〕

### 4.2 确认生效（状态机）

（叙述）QA 对有两个状态：draft（草稿）与 confirmed（已确认）。确认操作是编辑者对 QA 内容的「审核通过」标记，单向 draft→confirmed，无撤销确认路径。状态可由 Excel 导入逐行指定。注意：确认与否不改变 QA 对是否参与召回（裁决见 4.4；2026-09-26 修订——该语义仅对 manual 来源成立，ai 来源须确认后才有召回准入，见 [qa-generation.md](./qa-generation.md) REQ-C02）。

- **REQ-B01**：用户可确认 QA 对，使其状态从 draft 变为 confirmed；确认是单向转移，系统不提供「取消确认」操作。〔证据：QaPairServiceImpl.java:89-95（唯一写 confirmed 的单条路径，无 revert）；grep 全仓无 unconfirm/revert ‹CERTAIN›〕
- **REQ-B02**：对不存在的 QA 对执行确认时不报错、无副作用（静默忽略）。〔证据：QaPairServiceImpl.java:91-94（null 检查后直接跳过）‹CERTAIN›〕
- **REQ-B03**：Excel 导入可逐行指定初始状态，仅接受 draft/confirmed（含中文「草稿/已确认」变体），缺省 draft；非法状态值导致该行导入失败。〔证据：ExcelQaPairImporter.java:209-219,367-374 ‹CERTAIN›〕

### 4.3 Excel 批量导入（模板下载 / 解析 / 批量写入）

（叙述）批量导入是 QA 对最主要的规模录入手段：用户先下载标准模板，填写后上传（.xlsx/.xls），系统逐行解析、校验、去重，然后在单事务内批量写入，并返回逐行明细（成功/跳过/失败统计）。去重按「问题归一化」（全角→半角、trim、压缩连续空白）判定，支持 overwrite 模式覆盖同问题旧记录。

- **REQ-C01**：用户可下载 QA 对导入模板，模板含 8 个标准列：问题（必填）、答案（必填）、分类、关键词（逗号分隔）、优先级、状态、生效时间、失效时间，并附 2 行示例。〔证据：QaImportController.java:69-86 ‹CERTAIN›〕
- **REQ-C02**：导入仅接受 .xlsx/.xls 文件，空文件、加密文件、损坏文件以业务错误拒绝并给出可操作提示。〔证据：ExcelQaPairImporter.java:75-96 ‹CERTAIN›〕
- **REQ-C03**：解析取第一个非隐藏工作表，首行为表头；表头列名支持中英文与「（必填）/（可选）/*」后缀变体，大小写不敏感，同一字段重复出现取首列；缺问题或答案列时整体拒绝并指明缺失列。〔证据：ExcelQaPairImporter.java:104-131,246-271 ‹CERTAIN›〕
- **REQ-C04**：问题为必填，空白问题行记为失败；完全空白行跳过不计；单文件数据行上限 10000，超限截断并记失败明细。〔证据：ExcelQaPairImporter.java:137-164,37（MAX_DATA_ROWS=10_000）‹CERTAIN›〕
- **REQ-C05**：问题归一化（全角→半角、trim、压缩连续空白）后判定重复：文件内重复跳过；与库内已有重复时，overwrite=false 跳过、overwrite=true 覆盖更新该记录的问题/答案/状态。〔证据：ExcelQaPairImporter.java:166-189,325-330、QaPairServiceImpl.java:104-132,140-148 ‹CERTAIN›〕
- **REQ-C06**：优先级仅接受 1-10 整数（缺省 5），非整数或越界导致该行失败——但该严格校验仅作用于新增行；覆盖更新行不执行此校验。〔证据：ExcelQaPairImporter.java:191-207,178-185 ‹CERTAIN›；〔决议 2026-09-24：按现状为准——不对称由分支结构决定、无注释表明意图，维持现状；导入覆盖行沿旧值跳过校验不产生脏数据新增，风险有限（代裁，用户可推翻）〕〕
- **REQ-C07**：成功行在单事务内批量落库：新增行 source=manual、fileId=null、答案缺省空串、状态缺省 draft；覆盖行按归一化问题匹配既有记录执行更新；事务失败则整批回滚。〔证据：QaPairServiceImpl.java:135-162 ‹CERTAIN›；‹CERTAIN（2026-09-24 深证）›「事务失败整批回滚」成立：全部写入包裹于 `transactionTemplate.executeWithoutResult` 单回调（:135-162 实读），回调内任一 insert/update 抛异常即整体回滚（TransactionTemplate 确定性语义）〕
- **REQ-C08**：导入完成返回统计（总行数/成功/跳过/失败）与逐行明细（行号、问题截断至 80 字符、状态、原因、解析出的扩展字段），并写一条汇总审计日志。〔证据：QaImportResult、ImportRowResult、ExcelQaPairImporter.java:38,349-353、QaPairServiceImpl.java:164-187 ‹CERTAIN›〕
- **REQ-C09**：导入模板中「分类/关键词/优先级/生效时间/失效时间」5 列为解析回显字段：被解析、参与逐行明细展示，但不落库（表中无对应列）。〔证据：ExcelQaPairImporter.java:151-156（解析）、QaPairServiceImpl.java:144-160（仅写 question/answer/status）、schema.sql:205-216（表无 5 列）‹CERTAIN›；〔决议 2026-09-24：按现状为准——模板承诺与存储能力不符属实但无意图说明，维持现状不补列；已登记债务（本条 + implement + 验收清单），属产品决策（代裁，用户可推翻）〕〕

### 4.4 QA 对与召回的关系（裁决）

（叙述）feature-map 遗留疑问「QA 对是否参与召回未见独立端点」裁决如下：**参与，且为 CERTAIN**——QA 对是混合检索的独立召回通道（qa 通道），经由检索链内部消费 `kb_qa_pair` 表实现，无（也不需要）独立端点。匹配方式是 query 分词后的逐 token LIKE 模糊匹配问题列。

- **REQ-D01**：启用关键词匹配（enableKeywordMatch）时，检索结果前插入 QA 命中项（数量 qaRecallCount，缺省 5），QA 项以 Q/A 拼接文本作为内容、source/channel=qa 标识返回。〔证据：RetrievalServiceImpl.java:224-246,820-833 ‹CERTAIN›〕
- **REQ-D02**：多路召回模式下，qaRecallCount>0 时 QA 对作为独立通道参与融合（与向量/全文通道并列），加权融合下 QA 通道权重固定 0.3。〔证据：RetrievalServiceImpl.java:703-706,1055 ‹CERTAIN›〕
- **REQ-D03**：QA 召回的匹配规则：query 切分为 token（英文/数字连续段≥2 字符、中文连续汉字块），任一 token LIKE 命中问题列即为候选，按命中 token 数降序取前 N；召回失败降级为空结果不阻断检索。〔证据：RetrievalServiceImpl.java:791-839,842-852 ‹CERTAIN›〕
- **REQ-D04**：QA 召回查询仅按 kbId 过滤，**不过滤 status**——draft 与 confirmed 的 QA 对同样参与召回，确认操作不具备「生效准入」检索语义。〔证据：RetrievalServiceImpl.java:803-812（查询条件仅 kbId）‹CERTAIN›；〔决议 2026-09-24：按现状为准——qaChannelRecall 无任何 status 过滤与意图注释，draft 参与召回维持现状；确认语义仅为管理标记、不作召回准入；若需 confirmed-only 准入属产品决策（代裁，用户可推翻）。S4 断言维持「draft 可召回」不反转。〕〕
  ▸ **2026-09-26 修订（qa-generation spec REQ-C02）**：引入 ai 来源后，召回准入规则改为 `source='manual' OR status='confirmed'`——manual 问答对维持本条现状口径（draft 即召回），「确认＝生效准入」仅对 ai 来源成立；检索实现须加准入过滤。〔修订裁决：fastrag/.tmp/idd-qa-generation-grill-decisions.md D10〕

### 4.5 生命周期级联（与宿主资源的一致性）

（叙述）QA 对的存续受宿主资源约束：删除知识库或删除（含回收站清除）文件时，关联 QA 对级联清理；QA 模式文件的重试会清旧 QA 对；重切保留手工 QA 对但拒绝 QA 模式文件；跨库移动文件时源库 QA 对被放弃。

- **REQ-E01**：删除知识库时，该库各文件名下 QA 对（kbId+fileId）随生命周期级联删除，清理失败不阻断删除主流程；但**级联粒度仅限有 fileId 的 QA 对**——fileId=null 的 QA 对（Excel 导入路径写入的全部记录）不在清理范围，将成为孤儿残留（登记缺陷）。〔证据：KbServiceImpl.java:210-217（qaPairMapper.delete 全方法仅此一处、条件 kbId+fileId）、QaPairServiceImpl.java:158（导入行强制 fileId=null）‹CERTAIN›〕
- **REQ-E02**：删除文件（回收站清除/硬删路径）时，该文件的 QA 对（kbId+fileId）级联删除，失败仅告警。〔证据：FileServiceImpl.java:601-611 ‹CERTAIN›〕
- **REQ-E03**：QA 模式（processing_mode=qa）文件重试时清理该文件旧 QA 对；但「问答对提取」流水线在消费端无实现，ai 来源 QA 对无写入方。〔证据：FileServiceImpl.java:625-634、grep IngestionConsumer 无 processingMode 分支、grep 全仓无 setSource("ai") 写入 ‹CERTAIN›；〔决议 2026-09-24（R4 代裁）：提取流水线的产品意图与实现计划不可考，取**不实现**——processing_mode=qa 保持预留枚举与重试清旧语义，提取流水线待产品立项后按 IDD 新特性开发（依赖 LLM 结构化抽取，属 ai-chunking 邻域）；现状差距：qa 模式文件入库后无 QA 产出（已在本条登记，孤儿 REQ 豁免见 tdd §4）。〕〕
  ▸ **2026-09-26 清偿**：提取流水线已立项落地为独立特性 qa-generation（qa 模式文件摄入完成后自动触发生成，见 [qa-generation.md](./qa-generation.md) REQ-A03），本条「不实现」裁决就此失效。
- **REQ-E04**：重新分片保留该文件既有 QA 对（QA 对为用户手工数据），但 QA 模式文件请求重切被拒绝。〔证据：FileServiceImpl.java:350-352（javadoc 口径）、382-384（守卫）‹CERTAIN›〕
- **REQ-E05**：文件跨知识库移动时，源知识库中该文件的 QA 对被级联删除且不随迁目标库。〔证据：FileServiceImpl.java:750-756（先删 QA 对、无复制逻辑）‹CERTAIN›〕

## 5. 非功能约束

- 全部 QA 端点鉴权走 @KbAuth 切面（viewer 读、editor 写），与全后端 KB ACL 一致。〔QaPairController.java:44-77 ‹CERTAIN›〕
- 业务错误 = HTTP 200 + body.code（BusinessException.badRequest），统一 ApiResponse 包装。〔ExcelQaPairImporter.java:76-95 + CONTEXT.md ‹CERTAIN›〕例外登记：模板下载失败经 response.setStatus(500) 直写 HTTP 状态。〔QaImportController.java:104-107 ‹CERTAIN›〕
- 导入批量写入必须单事务原子。〔QaPairServiceImpl.java:135-162 ‹CERTAIN›〕
- 性能阈值（导入耗时上限、召回延迟预算）：〔决议 2026-09-24（R4 代裁）：取 **10000 行导入 P95 ≤ 60s（单事务批量写、逐行 LIKE 查库为主项）、QA 通道召回 P95 ≤ 200ms（kb 内 QA 对千级、LIKE 全表扫为瓶颈）**；理由：与 kb-core/retrieval 保守基线同量级；现状差距：无压测证据，QA 召回 LIKE 无索引覆盖，登记为观测义务。〕

## 6. 验收清单

- [ ] 创建 QA 对缺问题或答案时被拒绝（@Valid 400 语义）（REQ-A01）
- [ ] 新建 QA 对 status 恒为 draft、source 缺省 manual（REQ-A01）
- [ ] 按 fileId 筛选列表仅返回该文件的 QA 对，列表按创建时间倒序（REQ-A02）
- [ ] 更新仅携带 question 时答案保持不变，status 不变（REQ-A03）
- [ ] 确认后状态为 confirmed；对不存在 id 确认返回 HTTP 200 且无异常（REQ-B01、REQ-B02）
- [ ] 全角问题与半角同文问题在导入中被判为重复（REQ-C05）
- [ ] overwrite=false 导入已存在问题时该行 skipped；overwrite=true 时原记录问题/答案/状态被更新（REQ-C05）
- [ ] 优先级填 11 的新增行导入失败且明细注明「1-10」；同样数据在覆盖行被静默钳制（REQ-C06）
- [ ] 状态填「已确认」的行落库 status=confirmed（REQ-B03）
- [ ] 10001 行文件导入截断至 10000 行并附超限失败明细（REQ-C04）
- [ ] 加密 xlsx 导入返回业务错误（HTTP 200 + body.code）而非 5xx（REQ-C02）
- [ ] enableKeywordMatch 开启且 QA 命中时，返回结果首项 source=qa（REQ-D01）
- [ ] ~~draft 状态 QA 对同样被召回（现状口径）（REQ-D04）~~ 修订（2026-09-26）：manual draft 仍召回；ai draft 不召回、确认后召回（见 qa-generation REQ-C02）
- [ ] 删除知识库后，有名下文件的 QA 对无残留（现状：fileId=null 的导入 QA 对残留——REQ-E01 登记缺陷；豁免：占位/债务登记，见 implement 文档）
- [ ] 重切后该文件 QA 对数量不变；QA 模式文件请求重切被拒绝（REQ-E04）
- [ ] 文件移入他库后，源库中该文件 QA 对不复存在（REQ-E05）
- [ ] 删除 QA 对为物理删除且对不存在 id 幂等成功（REQ-A04）
- [ ] 创建/更新/删除/确认/导入全部写操作要求 editor 及以上角色并记录操作审计（REQ-A05）
- [ ] 模板下载返回 8 个标准列与 2 行示例（REQ-C01）
- [ ] 表头中英文与「（必填）/（可选）/*」变体可解析，缺问题或答案列时整体拒绝并指明缺失列（REQ-C03）
- [ ] 导入成功行在单事务内批量落库，事务失败整批回滚（REQ-C07）
- [ ] 导入完成返回统计（总行/成功/跳过/失败）与逐行明细并写汇总审计日志（REQ-C08）
- [ ] 分类/关键词/优先级/生效时间/失效时间 5 列仅解析回显、不落库（模板承诺与存储能力不符，登记债务）（REQ-C09；豁免：占位/债务登记，见 implement 文档）
- [ ] 多路召回下 qaRecallCount>0 时 QA 对作为独立通道参与融合，加权融合权重固定 0.3（REQ-D02）
- [ ] QA 召回按 token LIKE 命中数降序取前 N，召回失败降级空结果不阻断检索（REQ-D03）
- [ ] 删除文件（回收站清除/硬删路径）后该文件 QA 对级联清理，失败仅告警（REQ-E02）
- [ ] QA 模式文件重试清空该文件旧 QA 对；问答对提取流水线消费端无实现（GAP 登记）（REQ-E03；豁免：占位/债务登记，见 implement 文档；流水线已立项 qa-generation，2026-09-26 清偿）

## 7. 测试策略

经由公共接口（QaPairService / QaPairController）与纯函数解析器（ExcelQaPairImporter）测试：① CRUD/confirm 状态机全转移与幂等语义（4.1/4.2）；② 导入解析边界——列别名、归一化去重、overwrite 分叉、优先级/状态校验不对称、10000 行截断、加密/损坏文件（4.3，重点用例族）；③ 检索消费点回归——qaChannelRecall 的分词命中与排序、draft 可召回（4.4，经 RetrievalServiceImpl 既有测试缝）；④ 级联一致性——KB/文件删除、重切守卫、跨库移动（4.5，联合 kb-core/file-ingestion 公共接口验证）。Excel 写入路径用真 MySQL（MyBatis-Plus），解析器为纯函数零 mock；检索链仅允许 mock Milvus/Embedding/Rerank（既有 RetrievalServiceImplTest 模式）。
