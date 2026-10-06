# 发布、版本与知识审核 — Frontend Spec（publish-review-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 12 证据包；术语 ← `CONTEXT.md`（发布）。

## 1. Scope

覆盖 PublishPanel（版本流转/审核流/发布历史/定时发布）、knowledge-review 7 页族。**核心现状：本特性当前没有任何从正常导航可达的入口**（PublishPanel 被 `v-if="false"` 隐藏、7 页无侧边栏——INFERRED-1 待裁决）。

## 2. User Tasks

**PublishPanel（779 行，`v-if="false"` 不可达——detail/index.vue:355 ‹CERTAIN›，INFERRED-1）**：
- **TASK-001 版本流转** — 状态机 draft→pending_review→approved/rejected→published（→draft 回退；rejected→draft）（types/audit.ts:46-52 `PUBLISH_TRANSITIONS` ‹CERTAIN›）。创建发布（先 getKbUpdateLogs 算变更摘要）；「立即发布」链式 submit→approve→旧线上 draft→新 publish（任一步失败降级提示手动发布）；行内操作按状态：提交审核/通过·驳回（备注弹窗）/发布/回退/重新提交（PublishPanel:19-164,378-402 ‹CERTAIN›）。
- **TASK-002 审核工单** — **死状态**：`reviewTasks` 声明后无赋值点（:16），审核人/驳回意见恒空，审核永远走 transitionVersion 分支而非 approveReview/rejectReview 工单分支（:128-143,167 ‹CERTAIN›，INFERRED-2）。
- **TASK-003 发布历史/撤回/重置** — history 列表；「发布知识」（需 approved 存在）；撤回 revokeKnowledge；重置上一版 resetKnowledge；线上/线下版本查看（:177-317 ‹CERTAIN›）。
- **TASK-004 定时发布** — 计划列表/新建（name+publishTime 校验，scheduledAt）/执行情况弹窗（:184-258 ‹CERTAIN›）。
- loading ref 存在但**模板无 v-loading 绑定（死状态）**（:17 ‹CERTAIN›，INFERRED-3）。

**knowledge-review 7 页（路由在、侧边栏零接线，仅 URL 直达）**：
- **TASK-005 review-management（618，唯一全真实 API 页）** — 发布历史（revoke 记录复用为重置历史）；发布 `publishApp`（**kbId 冒用 appId**，:66）；撤回；发布计划（失败降级「（本地模式）」假成功）；策略效果；重置（自动找上一 offline 版 + 必填原因；失败本地模拟状态翻转）（:39-287 ‹CERTAIN›；硬编码 kbId='kb_sample' :9，INFERRED-4）。
- **TASK-006 flows（494）** — 主列表**硬编码 mock 10 条**；通过/驳回调真实 approveReview/rejectReview 但 **catch ignore 后无条件成功 toast**（传 mock 行 id）；管理工具 10 项（覆盖率/导出/性能/超时策略等大部分调不存在的 API 或仅 localStorage）（:35-260 ‹CERTAIN›）。
- **TASK-007 flow-design（582）** — `getReviewTemplates` **函数不存在恒走 catch 空**；模板 CRUD 同；模板库硬编码 4 套；导入/导出本地 JSON；效率分析本地公式降级（:21-392 ‹CERTAIN›）。
- **TASK-008 listeners（405）** — `api.getListeners` 不存在抛 TypeError，**finally 后 mock 兜底被跳过 → 页面恒空表**；CRUD/日志/保留策略全调不存在函数；趋势/告警为本地随机数（:29-215 ‹CERTAIN›）。
- **TASK-009 compliance（156）** — kbId 固定 'default'；列表/CRUD 全调不存在函数；**「检查」按钮绑定未定义的 `handleExecuteCheck`（运行时 BUG）**（:31,107 ‹CERTAIN›）。
- **TASK-010 reports（161）** — **纯 mock 页**（硬编码覆盖率/效率/3 条报告）；导出函数不存在恒「导出失败」；模板配置仅 localStorage（:14-69 ‹CERTAIN›）。
- **TASK-011 quality（242）** — 规则表/保存调不存在函数；「执行质量评分」= setTimeout 800ms 假耗时 + 硬编码分数；报告本地 JSON（:32-117 ‹CERTAIN›）。

**不存在的 API 汇总（CERTAIN）**：7 页共调 **27 个 src/api 中不存在的函数**（getReviewTemplates 族 ×4、getNodeOptimizationSuggestions、getListeners 族 ×9、flows 工具 ×9、compliance 族 ×4、getQualityRules/updateQualityRule、exportPublishReport）——同名能力仅存在于死 mock `mock/knowledge-review.ts`（函数名还不同，双重孤儿）。**另 8 个已定义死导出**：getLatestVersion/getReviews/getPendingReviews/getPublishStatus/getReviewStatus/publishKnowledge/getResetConfigs/saveResetConfig（INFERRED-5）。

## 3. Pages & Routes

- PublishPanel：详情 Tab「publish」（`v-if="false"`）。7 页：`/knowledge-review/management`（顶层）+ `/knowledge-review` redirect flows + 6 子路由（routes.ts:104-108,376-387）；Sidebar 全文零引用（‹CERTAIN›）。
- 审核权限 `review:approve` 已定义（auth.ts:133; menu-perm-tree:185）；PublishPanel `canReview` 消费（:170）。

## 4. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| PublishPanel 版本/历史/计划 | **死 loading（无 v-loading 绑定）** | el-empty ×3 | 分支化提示（创建发布降级文案） | :17,406-484 |
| flows 主列表 | v-loading | — | **假成功 toast** | :61-79,264 |
| listeners | v-loading + 恒空表 | 恒空 | 「操作失败」toast | :29-90 |
| review-management | v-loading | el-empty ×3 | 「（本地模式）」假成功降级 | :152-156,280-286 |
| compliance/quality/reports | v-loading / 假 loading | 恒空 | 「保存失败/导出失败」 | 各页 |

## 5. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 版本/流转 | getVersions/getPublishedVersion/createVersion/transitionVersion `{action}`（api:693-717） | TBD — pending API Contract |
| 审核工单 | submitForReview/approveReview/rejectReview（api:721-731） | TBD |
| 发布域 | history/plans(+execution)/strategy-effect/revoke/reset/online+offline-version（api:1600-1651） | TBD |
| 误用 | `publishApp(kbId,…)`（app 组 :1720；语义错位 INFERRED-4） | — |
| 状态机 | PUBLISH_TRANSITIONS/PUBLISH_STATUS_LABELS/COLORS、REVIEW_*（types/audit.ts:28-118） | —（前端类型） |
| ~~27 个不存在函数~~ | 7 页调用 → 运行时 undefined（INFERRED-5） | — |

## 6. Responsive & Accessibility

桌面优先 [GAP]。

## 7. Acceptance

- [ ] 现状验收 = PublishPanel 不可达 + 7 页 URL 直达（INFERRED-1 裁决前不变）
- [ ] review-management：发布历史/撤回/重置（原因必填）链路（硬编码 kbId 现状）
- [ ] 状态机流转表与标签/颜色映射正确（types/audit.ts 为真源）

## 8. INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。要点：可达性已修复（PublishPanel 恢复可达）；整族断裂保留登记（后端能力落地前维持现状）。以下原清单保留作对照。

### 原清单（已被裁决覆盖）

INFERRED-1 **PublishPanel v-if="false" 隐藏 + 7 页无入口——恢复入口 or 维持废弃？** INFERRED-2 reviewTasks 死状态（审核工单链路恒不走）——接工单 API or 删分支？INFERRED-3 死 loading 清理？INFERRED-4 review-management 硬编码 kb_sample + publishApp 语义错位——修 or 废？INFERRED-5 27 个不存在函数 + 8 个死导出 + flows 假成功/compliance 运行时 BUG/listeners 恒空/quality 假评分/reports 纯 mock——**整族处置方案**（按后端能力重建 / 降级删除 / 维持）？INFERRED-6 flows 通知配置等 localStorage 持久化是否转正？
