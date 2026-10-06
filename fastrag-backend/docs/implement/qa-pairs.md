# 问答对管理（qa-pairs）— 实现文档

> 本特性无独立契约接口（契约排除，见 contract/interfaces.md `## qa-pairs`），故 §2 按 design §2 公共接口逐个记录既有实现；「from contract」一律标注为无契约、指向 design 模块。

## 1. Code File Map

```
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── controller/
│   ├── QaPairController.java      QA 端点门面：list/create/update/delete/confirm/import 六端点，@KbAuth+@Loggable（镜像 QaPairModule）
│   └── QaImportController.java    GET /import-template：SXSSF 流式生成 8 列模板（镜像 QaTemplateModule）
├── service/
│   ├── QaPairService.java         六方法业务接口
│   └── impl/QaPairServiceImpl.java CRUD/confirm/导入编排（镜像 QaPairModule）
├── util/
│   └── ExcelQaPairImporter.java   纯函数解析器：列别名/类型读取/行校验/归一化去重（镜像 ExcelParserModule）
├── mapper/KbQaPairMapper.java     空 BaseMapper<KbQaPair>
├── entity/KbQaPair.java           表 kb_qa_pair 实体（ASSIGN_ID；createdAt INSERT 填充）
└── model/
    ├── QaCreateRequest.java       创建入参（@NotBlank question/answer；source/fileId 可选）
    ├── QaPairDto.java             出参（id/kbId/fileId/fileName/question/answer/source/status/createdAt）
    ├── QaImportResult.java        导入统计（totalRows/successCount/skipCount/failCount/details）
    └── ImportRowResult.java       行级明细（10 字段 + failed/skipped/success 三工厂）
init-scripts/schema.sql:205-216   kb_qa_pair 全量 DDL（SchemaInitializer 无本表增补）
```

跨域消费方（非本特性文件，级联/召回实现所在）：`fastrag-knowledge/.../service/impl/KbServiceImpl.java`、`FileServiceImpl.java`、`fastrag-retrieval/.../service/impl/RetrievalServiceImpl.java`、`fastrag-knowledge/.../service/impl/AiChunkServiceImpl.java`（守卫）。

## 2. Interface Implementations

### QaPairService.list / create / update / delete / confirm（from contract: 无契约 → design: QaPairModule）

**Logic Flow:**
1. `list(kbId, fileId?)`：1. `eq(kb_id)` + `orderByDesc(created_at)` 构造 wrapper；2. IF fileId 非空 THEN 追加 `eq(file_id)`；3. `selectList` → 逐条 `toDto`（含恒 null 的 fileName 透传）→ RETURN List（无分页）。
2. `create(kbId, req)`：1. new KbQaPair 填 kbId/question/answer；2. IF req.source≠null THEN 用之 ELSE source="manual"；3. fileId 原样落（不校验文件存在）；4. status 强制 "draft"；5. insert（ASSIGN_ID 雪花）→ RETURN toDto。校验失败（question/answer 空白）在进方法前被 @Valid 拦截。
3. `update(kbId, id, patch)`：1. `selectById(id)`——**不比对 kbId**；2. IF null THEN throw RuntimeException("Not found")（走兜底 500 语义）；3. IF patch 含 question THEN 覆盖；IF 含 answer THEN 覆盖；4. `updateById` → RETURN toDto。patch 含 status/fileId 等其他键被静默忽略。
4. `delete(kbId, id)`：`deleteById(id)` 即返回——查无也成功（幂等），无 kbId 比对。
5. `confirm(kbId, id)`：1. `selectById(id)`——不比对 kbId；2. IF null THEN 直接返回（静默）；3. ELSE status="confirmed" + `updateById`。无 draft 前置校验（confirmed 再确认=幂等覆写）。

**Module Interactions:**
- Calls：KbQaPairMapper（MyBatis-Plus）；
- Called by：QaPairController 六端点；
- Shared state：表 kb_qa_pair（读写主表）；无缓存/队列/主题。

**Decisions & Deviations:** 与契约（此处为 spec REQ）的偏离登记：① 三个写方法均不做 kbId 归属校验——跨库 id 可操作（债务③）；② 错误语义三态不一：update=500 兜底、confirm=静默、delete=幂等（债务③）；③ list 不分页（REQ-A02 登记）。Controller 层每个写端点挂 @Loggable 审计（qa_pair_created/updated/deleted/confirmed），与 REQ-A05 一致。

**Parent Trace:** QaPairServiceImpl → design.QaPairModule → REQ-A01~A05、B01~B02。

### QaPairService.importFromXlsx（from contract: 无契约 → design: QaPairModule 编排 + ExcelParserModule 解析）

**Logic Flow:**
1. 计时开始；2. 预加载：`selectList(kb_id=?)` 取 question 列 → 逐条 `normalizeQuestion(trim)` → LinkedHashSet（保序去重）；
3. 委托 `ExcelQaPairImporter.parse(file, existingQuestions, overwrite)`——文件级错误（空/扩展名/加密/损坏/无表/无表头/缺必填列）在此以 badRequest 直接抛出终止；行级错误进 details（见下）；
4. 构建 `normalizedToId` 映射：再查一遍全量（id+question）→ 归一化 question → id（重复归一化键取先者）；
5. `transactionTemplate.executeWithoutResult` 内遍历 success 行：IF 行 reason=="覆盖更新（原记录将被更新）"（**魔法串比对**）THEN `selectById(existingId)`，IF 存在 THEN 覆盖 question/answer（行答案 null 则保留旧值）/status（行状态 null 则保留旧值）+ updateById——**priority/category/keywords 不写**（表无列）；ELSE new KbQaPair：自拼 24 字符 ID（12 位毫秒 hex 补零 + 12 位 UUID hex）、kbId、question、answer（null→""）、source="manual"、status（行值→null→"draft"）、fileId=null + insert；
6. 汇总 successCount/skipCount/failCount/totalRows；7. info 日志（含耗时）；8. `logService.addLog(kbId, operation, qa_pair_created, "qa-pairs", "批量导入QA对: 成功=…", null, "success")` 写汇总审计；9. RETURN result。

解析器行级决策（ExcelQaPairImporter.parse，逐行循环 r=1..lastRow）：
1. IF 已达 10000 行 THEN 记 failed 超限明细 + break；2. IF 行 null 或全空白 THEN continue（不计）；3. 取 question/answer/category/keywords/priority/status 六列（列缺失→null）；4. IF question trim 后空 THEN failed「问题不能为空」+ continue；5. 归一化；6. IF 文件内 seenInFile 已含 THEN skipped「文件内重复」+ continue；7. IF 库内已存在 AND overwrite THEN success + reason="覆盖更新…"——**跳过优先级/状态校验**；8. ELSE IF 已存在 THEN skipped「已存在相同问题」；9. ELSE：优先级非空时非整数或 ∉[1,10] → failed + continue；状态非空时无法归一（draft/confirmed/草稿/已确认之外）→ failed + continue；10. success（优先级经 parsePriority 钳制 1-10、缺省 5；状态缺省 draft）。

**Module Interactions:**
- Calls：KbQaPairMapper（3 次查询 + N 次写——overwrite 行存在 selectById+updateById 的 N+1）、ExcelQaPairImporter（静态）、TransactionTemplate、publish 模块 LogService（审计）；
- Called by：QaPairController.importXlsx；
- Shared state：表 kb_qa_pair（读写）、kb_log（审计追加）。

**Decisions & Deviations:** ① 覆盖决策经人类可读 reason 魔法串传递而非结构化字段（债务⑤）；② 导入行强制 fileId=null 且自造 ID 绕开 ASSIGN_ID（孤儿缺陷①与双轨 ID 登记的根源）；③ 模板 8 列中 5 列只进明细不落库（债务④）；④ 覆盖行跳过严格校验的不对称（债务⑥）——以上均无注释说明意图，实现按现状如实记录。手工 create 走 ASSIGN_ID、导入自拼 ID 的双轨与「导入必 null fileId」共同构成 REQ-E01 孤儿缺陷的完整成因链。

**Parent Trace:** QaPairServiceImpl.importFromXlsx + ExcelQaPairImporter.parse → design.QaPairModule/ExcelParserModule → REQ-B03、C02~C09。

### GET /import-template（from contract: 无契约 → design: QaTemplateModule）

**Logic Flow:**
1. 设响应头：Content-Type=xlsx MIME、Content-Disposition attachment（文件名 UTF-8 编码，+ 替换 %20）；2. try：SXSSFWorkbook(128) 建「问答对导入模板」sheet → 表头样式（灰底+粗体+细边框）/普通样式（细边框）→ 写 8 列表头（问题（必填）/答案（必填）/分类/关键词（逗号分隔）/优先级/状态/生效时间/失效时间）→ 写 2 示例行（draft 与 confirmed 各一）→ 列宽 40*256 → `wb.write(out)` + flush + info 日志；3. catch Exception：error 日志 + `response.setStatus(500)`（**此时响应头已部分提交，客户端收到非 ApiResponse 的裸 500**——与统一响应约定的偏离，登记）。

**Module Interactions:** Calls：无其他模块（纯 POI）；Called by：QaImportController 端点；Shared state：无（不触库）。

**Decisions & Deviations:** 失败路径返回 HTTP 500 而非 HTTP 200 + body.code，属全后端统一响应约定的已登记例外（spec §5）；模板列与解析器 COLUMN_ALIASES 保持人工同步（模板列名「问题（必填）」在别名表中显式登记）。

**Parent Trace:** QaImportController.downloadTemplate → design.QaTemplateModule → REQ-C01。

### 消费点实现（from contract: retrieval / kb-core / file-ingestion 契约的 qa 相关条款 → design: 缝 3 与上游模块）

**Logic Flow（各消费点一句话）：**
1. `qaChannelRecall`（RetrievalServiceImpl.java:798-839）：token 化（正则 `[A-Za-z0-9]{2,}|[\u4e00-\u9fa5]+`）→ 空则返空 → `selectList(kb_id=? AND (question LIKE token1 OR …) LIMIT max(count*3,30))`——**无 status 条件** → 按命中 token 数降序取前 count → 组 SearchResultItem（content="Q:…\nA:…"、source/channel="qa"、similarity=0.8-i/size 人造梯度）→ 任何异常 catch 后返空列表；
2. 前置路（:224-246）：enableKeywordMatch 时取 qaHits，按 fileId_chunkIndex 去重后 **prepend** 到普通结果之前；
3. 融合路（:703-706,1055）：qaRecallCount>0 时作为第三通道进 rrf/weighted/interleave，weighted 权重表硬编码 {v,f,0.3,0.3}；
4. 级联删除三处（KbServiceImpl:210-217 / FileServiceImpl:601-611,625-634,750-756）：均 `delete(kb_id=? and file_id=?)`，前两处 try/catch warn 不阻断，moveToKb 处裸删（@Transactional 内）；
5. 守卫两处：reChunkFile 拒 qa 模式（FileServiceImpl:382-384「QA 对不受分片策略影响」）、AiChunkServiceImpl:790 拒 qa 模式 apply。

**Module Interactions:** 消费方 ← 本特性表 kb_qa_pair；RetrievalServiceImpl 直连 KbQaPairMapper（跨模块 mapper 引用，共享表模式）；Called by：检索主流程 / KbLifecycleService.delete / FileService delete/retry/moveToKb / reChunk 与 ai-chunk 准入。

**Decisions & Deviations:** 契约（`## kb-core` delete Post「kb_qa_pair 无残留」）与实现冲突：fileId=null 记录逃逸级联——仲裁为登记缺陷、契约条款保留为修复目标（已在 interfaces.md `## qa-pairs` 债务① 双节登记）。similarity 梯度为合成值非相似度语义（retrieval 链已知口径）。

**Parent Trace:** 消费点 → design.缝 3 + 上游模块 → REQ-D01~D04、E01~E05。

## 3. Testing

现有测试仅覆盖检索消费点：`RetrievalServiceImplTest`（qaPairMapper mock 注入 :64；QA 前置断言 :208-216「关键词命中时 QA 结果应优先返回」；分词模糊匹配用例 :218-216 起「DeepSeek V4参数 命中 DeepSeek V4 Pro 参数」）——对应 tdd S4 前 2 例。

缺口（tdd 各切片均无现有实现，红绿循环未执行）：S1 全部（CRUD/confirm 状态机、越权现状断言）、S2 全部（解析边界 16 例——纯函数可直接落）、S3 全部（导入编排 4 例）、S4 后 2 例（draft 召回现状、降级）、S5 全部（级联 5 例，含孤儿缺陷现状断言）。执行时从 tdd §4 Tracer Bullet（解析判重→导入落库→召回前置纵切）起步。
