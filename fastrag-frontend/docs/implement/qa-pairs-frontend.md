# 问答对管理 — Implementation Document（qa-pairs-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/components/
├── QaPanel.vue         — 列表/筛选/CRUD/确认（291）
└── QaImportDialog.vue  — Excel 导入 + 结果三 Tab（363）
src/api/index.ts        — qa-pairs 组 7 端点（:439-476）
src/types/knowledge.ts  — QaPair/QaImportResult
```

## 2. Interface Implementations

### 列表加载与筛选（纯前端）

**Logic Flow:**
```
loadData: try getQaPairs(kbId) → list = res?.list || res; total = res?.total
          finally loading=false（无 catch 静默）
filteredList(computed): list |> keyword(question/answer 小写 includes) |> status |> source
分页: filteredList.slice(...)；total = filteredList.length
```

### 导入流

**Logic Flow:**
```
handleImport:
1. importing=true（禁关闭/禁重提）
2. POST import(FormData: file, overwrite)
3. result 分桶 success/skipped/failed ← details[].status
4. 汇总 toast（failCount>0 → error 类；skipCount>0 → warning 类；否则 success）
5. emit('success') → 父 loadData；失败 → e?.message || '导入失败'
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 编辑回填 | form | keywords/similarQuestions join('，') | QaPanel:83-84 |
| 默认值 | form | draft/manual/text/priority 5 | :75 |
| 确认生效 | server | 仅 draft 行显示；无 try/catch | :97-101,177 |
| 模板下载 | blob | objectURL → a.click | :48-61 |

## 3. Decisions & Deviations

- **勘误**：QaPanel:5 mock getFiles 为死 import（feature-map「消费 mock :5」记载有误）——本特性无真实 mock 消费。
- **提交字段裁剪（INFERRED-2）**：表单收集 9 字段仅提交 2 字段——契约按现状记录。
- **静默失败**（INFERRED-4）：删除/保存空 catch、确认无捕获。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：filteredList 三条件组合、导入结果分桶与汇总消息、编辑回填 join。

## 5. Implementation Trace

```
TASK-001 → 筛选/分页 → QaPanel:47-70,185
TASK-002/003 → CRUD → :73-122
TASK-004 → 确认生效 → :97-101
TASK-005 → 导入 → QaImportDialog:75-232
TASK-006 → 模板 → QaPanel:48-61
```
