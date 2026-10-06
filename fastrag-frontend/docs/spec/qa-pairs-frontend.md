# 问答对管理 — Frontend Spec（qa-pairs-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 8 证据包；术语 ← `CONTEXT.md`。

## 1. Scope

覆盖知识库详情「问答对」Tab（QaPanel）与 Excel 导入对话框（QaImportDialog）。**排除**：qa-detail 运营页（operation-analytics 链复用同组 API）；chunks.vue QA 模式简表（chunk-management 链）。

## 2. User Tasks

- **TASK-001 浏览/筛选问答对** — 筛选（关键词对 question/answer 小写匹配、status、source）+ 分页**纯前端**（computed filteredList + slice；分页条仅 total>pageSize 显示）（QaPanel:47-70,185 ‹CERTAIN›）。表格无 el-empty 空态 [GAP]。
- **TASK-002 新增/编辑** — 默认值 `{status:'draft', source:'manual', answerType:'text', priority:5}`；编辑回填 keywords/similarQuestions join 逗号；校验 question/answer 非空；**提交仅 `{question,answer}`——其余收集字段（category/answerType/keywords/similarQuestions/priority/status/effectiveTime/expireTime）静默丢弃不落库**（:73-122,206-253 ‹CERTAIN›，INFERRED-2）。
- **TASK-003 删除** — confirm；**取消/失败共用空 catch 静默**（:89-96 ‹CERTAIN›，INFERRED-4）。
- **TASK-004 确认生效** — 仅 draft 行显「确认」→ `confirmQaPair` → reload；**无 try/catch（unhandled rejection）**（:97-101,177 ‹CERTAIN›，INFERRED-4）。
- **TASK-005 Excel 导入** — 选文件（.xlsx/.xls limit 1，手动上传）+ 覆盖 checkbox → `importQaPairs`（FormData）→ 结果面板三 Tab（success/skipped/failed，各带空态）+ 汇总 toast（按 fail/skip 决定默认 Tab）→ emit success → 父 reload；importing 期间禁关禁重提（QaImportDialog:75-232 ‹CERTAIN›）。
- **TASK-006 下载模板** — blob → objectURL → a.click（`qa-pairs-import-template.xlsx`）；失败 toast（QaPanel:48-61 ‹CERTAIN›）。

## 3. Pages & Routes

无独立路由：详情 Tab「qa」（detail/index.vue:345-353）挂 QaPanel；QaImportDialog 由 QaPanel 挂载（:141-145）。

## 4. Elements & Interaction

来源下拉硬编码仅 `manual`（:153-155），而表格渲染 `row.source==='ai'` warning tag（:166）——**AI 来源 QA 既造不出也筛不到**（INFERRED-3）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 列表 | v-loading（finally 复位） | **无空态** | **无 catch（静默）** | :61-70,128 |
| 保存/删除 | — | 前置非空 warning | **空 catch 静默** | :95,102-122 |
| 确认生效 | — | — | **无捕获** | :97-101 |
| 导入 | importing 禁关禁重提 | 三 Tab 各 el-empty | `e?.message‖'导入失败'` | QID:75-232 |
| 模板下载 | — | — | toast 重试 | :48-61 |

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 列表 | GET `/kb/{kbId}/qa-pairs`（`res?.list‖res`、`res?.total`）（api:443-445; QaPanel:64-67） | TBD — pending API Contract |
| CRUD/确认 | POST/PUT/DELETE、`/{id}/confirm`（api:447-461） | TBD |
| 导入 | POST `…/import`（FormData file+overwrite）→ `QaImportResult{successCount,skipCount,failCount,totalRows,details[{status,rowNumber,question,answer,reason}]}`（api:468-476; QID:112-114） | TBD |
| 模板 | GET `…/import-template`（blob）（api:464-466） | TBD |

**死 import 勘误（CERTAIN）**：QaPanel:5 `import {getFiles} from '@/mock/files'` 全文件未使用——feature-map 所记「getFiles 消费 mock」实为死导入，本特性**无真实 mock 消费**。

## 7. Responsive & Accessibility

桌面优先 [GAP]。

## 8. Acceptance

- [ ] 筛选（关键词/状态/来源）与前端分页正确
- [ ] 新增默认 draft/manual；保存仅提交 question/answer（INFERRED-2 裁决前维持）
- [ ] 删除 confirm；确认生效仅 draft 行
- [ ] 导入三 Tab 结果与汇总消息正确；覆盖开关生效；导入中不可关闭
- [ ] 模板可下载

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 死 import（mock/files getFiles）清理？INFERRED-2 表单字段静默丢弃（仅 question/answer 落库）——补提交 or 砍字段？INFERRED-3 AI 来源 QA 无创建/筛选入口——接线 or 删 tag？INFERRED-4 删除/保存/确认的静默失败与无捕获——补错误处理？INFERRED-5 QaImportDialog 重复 import watch（:45）清理？
