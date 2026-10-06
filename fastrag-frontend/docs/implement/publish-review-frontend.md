# 发布、版本与知识审核 — Implementation Document（publish-review-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/
├── index.vue                    — Tab 装配（publish tab v-if="false" :355）
└── components/PublishPanel.vue  — 版本/审核/历史/计划（779，不可达）
src/views/knowledge-review/
├── review-management.vue        — 唯一全真实 API 页（618）
├── flows.vue(494) flow-design.vue(582) listeners.vue(405)
├── compliance.vue(156) reports.vue(161) quality.vue(242)
src/types/audit.ts               — PUBLISH_TRANSITIONS/LABELS/COLORS + ReviewTask（状态机真源）
src/api/index.ts                 — versions/reviews/publish 组 + 8 死导出
src/mock/knowledge-review.ts     — 死 mock（函数名与 7 页调用不匹配，双重孤儿）
```

## 2. Interface Implementations

### 创建发布链（PublishPanel）

**Logic Flow:**
```
openCreateDialog: getKbUpdateLogs → 变更摘要 {fileAdded,fileUpdated,fileRemoved,configChanged}
handleConfirmCreate:
1. createVersion(kb,{name,description,tags})
2. IF publishNow:
   submitForReview({kbId,versionId}) → transition(submit) → transition(approve)
   → 旧 published transition(draft) → 新 transition(publish)
3. 任一步失败 → 提示「版本已创建但自动发布失败」（手动发布兜底）
```

### 状态机行操作

```
行操作渲染按 PUBLISH_TRANSITIONS：
draft→[提交审核](submitForReview+transition submit)
pending_review+canReview→[通过/驳回]（备注弹窗；工单分支死→恒走 transitionVersion）
approved→[发布]（旧线上→draft、新→publish）｜published→[回退]｜rejected→[重新提交]
```

### 7 页族断裂点登记

| 页 | 断裂 | 证据 |
|---|---|---|
| flows | 主列表硬编码；通过/驳回假成功（catch ignore + mock id）；工具 10 项部分不存在 | :35-260 |
| flow-design | getReviewTemplates 不存在恒空；效率分析本地公式 | :111-126,188-251 |
| listeners | getListeners 抛错且 finally 后兜底被跳过 → 恒空 | :29-45 |
| compliance | handleExecuteCheck 未定义（点击抛错） | :107 |
| reports | 纯 mock | :14-69 |
| quality | 假评分 setTimeout 800ms | :62-82 |
| review-management | kb_sample 硬编码；publishApp(kbId) 误用；（本地模式）假成功 ×2 | :9,66,152-156,280-286 |

## 3. Decisions & Deviations

- **可达性（INFERRED-1）**：本特性无任何正常导航入口——PublishPanel v-if="false" + 7 页无侧边栏；处置待用户裁决（恢复/废弃/重建）。
- **27 个不存在函数 + 8 死导出 + 双重孤儿 mock**（INFERRED-5）：整族处置方案待裁决。
- 无 contract↔代码矛盾（特征化模式；契约按「若恢复」形态书写并显式标注现状断裂）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：PUBLISH_TRANSITIONS 状态机映射、创建发布链式流（含降级）、7 页断裂点回归锚（防误改）。

## 5. Implementation Trace

```
TASK-001 → 版本流转 → PublishPanel:19-164,378-402
TASK-002 → 审核工单死分支 → :16,128-143
TASK-003/004 → 历史/撤回/重置/计划 → :177-317
TASK-005 → review-management → :39-287
TASK-006..011 → 7 页族断裂点 → 各页登记表
```
