# 问答对管理（qa-pairs）— 测试设计

> 本特性无独立契约接口（端点排除，见 contract/interfaces.md `## qa-pairs`），切片按 design §2 公共接口分组：QaPairService 方法、ExcelParserModule 纯函数、检索消费点（回归 `## retrieval` 契约）、宿主级联（回归 `## kb-core`/`## file-ingestion` 契约）。mock 只允许在系统边界（MySQL 用内存库/测试库为真、Excel/HttpServletResponse 为真实对象或 MockMvc 封装、检索链 mock Milvus/Embedding/Rerank/GraphStore），禁 mock 内部模块。

## 1. 切片规划

| 切片 | 接口组 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 产品裁决前置 | — | ~~HITL~~→**已裁决（2026-09-24 代裁）** | 两项 GAP 已裁决（draft 维持参与、模板维持不补列，见 §1 S0 注记），期望值已定，执行前无需再人工拍板 |
| S1 CRUD 与确认状态机 | QaPairService.list/create/update/delete/confirm | AFK | 含跨库越权现状断言（债务③） |
| S2 Excel 解析纯函数 | ExcelQaPairImporter.parse / normalizeQuestion / 模板生成 | AFK | 零 mock 零 DB，最大用例族（导入解析边界） |
| S3 导入编排与事务 | QaPairService.importFromXlsx | AFK | 真库验证写入与回滚 |
| S4 检索消费点回归 | retrieval.qaChannelRecall（契约归 `## retrieval`） | AFK | 复用既有 RetrievalServiceImplTest 测试缝 |
| S5 宿主级联一致性 | kb-core.delete / file-ingestion.delete/reChunk/moveToKb | AFK | 联合上游公共接口验证 |

**S0（HITL，先行）——2026-09-24 裁决注记：两项均已代裁，S0 阻塞解除**
1. **draft QA 对是否参与召回**：〔决议 2026-09-24：按现状为准——维持参与（RetrievalServiceImpl.java:803-812 仅按 kbId 过滤），不改 confirmed-only；S4 的「draft 可召回」断言**维持不反转**，详裁决见 spec REQ-D04。〕
2. **模板 8 列 vs 表 6 业务字段的取舍**：〔决议 2026-09-24：按现状为准——维持 5 列解析回显不落库（REQ-C09），不补列；S3 无需增加 5 字段落库断言，详裁决见 spec REQ-C09。〕

## 2. 测试用例

### S1 QaPairService（contract: 无独立契约；design: QaPairModule）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建] 缺问题时 [被拒绝] | 合法 kbId、answer 非空 | create(question=null) | 业务错误（@Valid 400 语义），无落库 | A01 | 无 |
| [创建] 全输入合法时 [落为 draft/manual] | question/answer 非空、source/fileId 可空 | create | 返回 DTO status=draft、source=manual；库中记录一致 | A01 | 无 |
| [查询] 按库列出时 [倒序全量] | 库内 3 条 QA 对 | list(kbId) | 3 条、按 createdAt 倒序、无分页字段 | A02 | 无 |
| [查询] 携带 fileId 时 [仅返回该文件] | 库内 3 条（1 条 fileA、1 条 null） | list(kbId, fileA) | 恰 1 条且 fileId=fileA | A02 | 无 |
| [更新] 仅携 question 时 [答案状态不变] | 既有 draft 记录 | update(patch={question}) | answer 与 status 原样 | A03 | 无 |
| [更新] id 查无时 [500 语义兜底]（现状口径） | 不存在 id | update | RuntimeException → HTTP 200 + serverError 兜底（债务③登记断言） | A03 | 无 |
| [删除] id 查无时 [幂等通过] | 不存在 id | delete | 不抛异常、库无变化 | A04 | 无 |
| [确认] draft 记录确认后 [转 confirmed] | draft 记录 | confirm | status=confirmed | B01 | 无 |
| [确认] 已确认记录再确认时 [幂等] | confirmed 记录 | confirm | 仍 confirmed、无异常 | B01 | 无 |
| [确认] id 查无时 [静默无副作用] | 不存在 id | confirm | 不抛异常（与 update 的 500 语义差异即债务③） | B02 | 无 |
| [越权] 他库 id 经本库端点操作时 [现状放行] | QA 对属 KB-B；操作者对 KB-A 有 editor | PUT /api/kb/A/qa-pairs/{KB-B 的 id} | 现状：更新成功（跨库越权，债务③登记断言；修复后应拒绝） | A03 | 无（@KbAuth 走真实切面或等效鉴权上下文） |

### S2 ExcelParserModule（contract: 无独立契约；design: ExcelParserModule/QaTemplateModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [下载模板] 请求时 [产出 8 列含示例工作簿] | 任意 kbId | GET /import-template | Content-Type xlsx、表头 8 列与 REQ-C01 一致、2 示例行 | C01 | HttpServletResponse 经 MockMvc 封装 |
| [解析] 空文件时 [拒绝] | isEmpty 的 MultipartFile | parse | badRequest「上传文件为空」 | C02 | MultipartFile（Spring 接口=系统边界） |
| [解析] 扩展名不符时 [拒绝] | 文件名 .csv | parse | badRequest「仅支持 .xlsx 和 .xls」 | C02 | 同上 |
| [解析] 加密工作簿时 [拒绝] | 加密 xlsx | parse | badRequest「已加密或受密码保护」 | C02 | 同上 |
| [解析] 缺答案列时 [整体拒绝指明缺列] | 表头仅「问题」 | parse | badRequest 含「答案/answer」 | C03 | 同上 |
| [解析] 表头带后缀与英文变体时 [正确映射] | 表头「问题（必填）/Answer/类别」 | parse | 三列分别映射 question/answer/category | C03 | 同上 |
| [解析] 同字段重复列时 [取首个] | 两列均「问题」 | parse | colMap 仅收首列 | C03 | 同上 |
| [解析] 问题空白行时 [行失败不中断] | 第 3 行问题为空白 | parse | 该行 failed「问题不能为空」，后续行继续 | C04 | 同上 |
| [解析] 超 10000 行时 [截断记账] | 10001 数据行 | parse | 首万行解析，末尾附 failed 超限明细 | C04 | 同上 |
| [解析] 全角问题遇半角同文时 [判重] | existingQuestions 含半角版 | parse | 全角行按归一化命中重复 | C05 | 同上 |
| [解析] 库内重复且 overwrite=false 时 [跳过] | 同上 | parse | 该行 skipped「已存在相同问题」 | C05 | 同上 |
| [解析] 库内重复且 overwrite=true 时 [标记覆盖] | 同上 | parse | 该行 success 且 reason=「覆盖更新（原记录将被更新）」 | C05 | 同上 |
| [解析] 新增行优先级 11 时 [行失败] | 新行 priority=11 | parse | failed「1-10 的整数」 | C06 | 同上 |
| [解析] 覆盖行优先级 99 时 [现状钳制不失败]（不对称断言） | 覆盖行 priority=99 | parse | 行 success（债务⑥登记断言） | C06 | 同上 |
| [解析] 状态「已确认」时 [归一 confirmed]；「pending」时 [行失败] | 新行 status 两例 | parse（两次） | 分别 confirmed / failed「仅支持 draft / confirmed」 | B03 | 同上 |
| [解析] 数字/日期/公式单元格时 [按类型取值] | answer 列分别为数值/日期/公式 | parse | DataFormatter 文本 / 日期 toString / 公式值字符串 | C03 | 同上 |

### S3 importFromXlsx 编排（contract: 无独立契约；design: QaPairModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [导入] 合法行落库时 [带 manual/null/空串兜底] | 模板数据 2 行（1 行状态「已确认」） | importFromXlsx(overwrite=false) | 新记录 source=manual、file_id=null、答案缺省空串、状态逐行落库 | C07 | 无（真库） |
| [导入] 混合结果时 [统计与明细一致] | 2 成功/1 跳过/1 失败文件 | importFromXlsx | totalRows=4、success=2、skip=1、fail=1、明细行号与原因一致 | C08 | 无 |
| [导入] 完成时 [写一条汇总审计] | 任意成功导入 | importFromXlsx | kb_log 新增 qa_pair_created 操作日志，detail 含三计数 | C08 | 无（LogService 走真库） |
| [导入] 写入中途失败时 [整批回滚] | 注入使第 2 行写库失败（如超长触发约束） | importFromXlsx | 第 1 行不残留（事务回滚） | C07 | 无 |

### S4 检索消费点回归（contract: retrieval.RetrievalSearchEngine；design: 缝 3）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [检索] 关键词命中 QA 时 [前置返回 source=qa] | enableKeywordMatch、库内「续费政策是什么」 | search("续费政策") | 首项 source=qa、content 含 Q/A 拼接 | D01 | Milvus/Embedding/Rerank/GraphStore（既有测试模式） |
| [检索] 分词模糊匹配时 [跨词命中] | 库内「DeepSeek V4 Pro 参数」 | search("DeepSeek V4参数") | 命中该 QA 对（字面 LIKE 不可达） | D03 | 同上 |
| [检索] 仅 draft QA 对时 [同样召回]（现状口径，受 S0-1 裁决约束） | status=draft 命中记录 | search | 该记录出现在结果中 | D04 | 同上 |
| [检索] QA 通道异常时 [降级不阻断] | qaPairMapper 抛异常 | search | QA 通道空回退，普通结果正常返回 | D03 | mapper 抛错属边界注入 |

### S5 宿主级联一致性（contract: kb-core.KbLifecycleService / file-ingestion 清理面；design: 上游模块）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [删库] 含导入 QA 对（fileId=null）时 [现状残留]（孤儿缺陷断言） | 库内 1 条 fileId=null 记录 | KbService.delete | 现状：记录残留（REQ-E01 缺陷断言；修复后应零残留） | E01 | MinIO/Milvus/Neo4j |
| [删库] 文件名下 QA 对时 [随文件清理] | 文件 f1 名下 2 条 | KbService.delete | kbId+f1 记录清零 | E01 | 同上 |
| [删文件] 硬删文件时 [QA 对清理] | 文件名下 1 条 | FileService.delete（硬删路径） | 记录清零 | E02 | 同上 |
| [重切] qa 模式文件请求时 [拒绝] | processing_mode=qa | reChunkFile | badRequest「QA 模式文件不支持重新分片」 | E04 | 无 |
| [移动] 文件移至他库时 [源库 QA 对丢失] | 文件名下 1 条，moveToKb | moveToKb | 源库记录清零且目标库无新增（不随迁） | E05 | MinIO |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [解析] 全角判重 | （无契约——design §2 ExcelParserModule.normalizeQuestion） | ExcelParserModule | REQ-C05 |
| [导入] 合法行落库 | （无契约——design §2 QaPairModule.importFromXlsx） | QaPairModule | REQ-C07 |
| [检索] 关键词命中前置 | retrieval.RetrievalSearchEngine（interfaces.md `## retrieval`） | 缝 3 消费点（RetrievalServiceImpl） | REQ-D01 |
| [删库] 孤儿残留断言 | kb-core.KbLifecycleService delete Post（interfaces.md:189） | 上游 KnowledgeBaseModule | REQ-E01 |
| [确认] draft 转 confirmed | （无契约——design §2 QaPairModule.confirm） | QaPairModule | REQ-B01 |

## 4. 待实施说明
- 孤儿 REQ 豁免：REQ-E03（QA 模式（processing_mode=qa）文件重试清理旧 QA 对已有承接，但「问答对提取」流水线消费端无实现、ai 来源 QA 对无写入方——2026-09-24 已裁决（R4）：取不实现、维持预留枚举语义，无下游承接为既定现状；详裁决见 spec REQ-E03）

- **红绿循环未执行**（本链为测试设计；执行待用户指令，届时从 Tracer Bullet 起步）。
- Tracer Bullet 建议：S2「全角判重」→ S3「合法行落库」→ S4「关键词命中前置」三例贯通「解析→落库→召回」最小纵切，先于其余用例。
- 期望值原受 S0 两项 HITL 裁决约束的用例（S4「仅 draft 召回」、S3 落库字段面）已于 2026-09-24 代裁定稿：S4 断言=draft 可召回、S3 断言=5 列不落库；若用户日后推翻裁决，先改断言再跑红绿。
- 现状口径断言（债务登记断言：S1 更新 500 语义 / S1 越权放行 / S2 覆盖行钳制 / S5 孤儿残留）执行时若债务已先行修复，按修复后语义改判并回写本文件。
