# 运营分析与系统监控 — Frontend Spec（operation-analytics-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 18 证据包；术语 ← `CONTEXT.md`（操作审计）。

## 1. Scope

覆盖运营页族（kb-analytics/model-monitor/feedback/qa-detail）、robot-operation 四页、审计系统日志页与三共享组件。**排除**：retrieval-analysis（retrieval 链已覆盖）；notifications/home 组（本特性页面无直接消费——CERTAIN grep 核实）。

## 2. User Tasks

- **TASK-001 知识资产分析** — `/operation/kb-analytics`：纯只读，onMounted 一次拉取 `/analytics/kb`（metrics/hotKBs/hotDocs），自带内联指标卡（不用 MetricCards）；catch 静默（kb-analytics:37-53,46-47 ‹CERTAIN›）。
- **TASK-002 模型监控** — `/operation/model-monitor`：时间范围（7/30/180 天，watch 重置页码）+ 模型搜索 → 单接口 `GET /monitor/model/overview`（一次返回 metrics+distribution+topApps+分页 stats）；MetricCards+RankList（作用域插槽注入 token/cost）（model-monitor:21-115 ‹CERTAIN›）。
- **TASK-003 用户反馈管理** — 动作最重页面：回复（operator 硬编码 'admin'，:218）/新增/编辑（同一弹窗）/删除（confirm）/「生成优化建议」（**非 AI**：满意度<80%、反馈率<20%、未解决率>20% 三规则文本 + alert，模板明示）；问答明细 Tab 懒加载（watch activeTab 才拉 getChatSessions）只读详情（feedback:14-309 ‹CERTAIN›）。
- **TASK-004 问答明细（qa-detail）** — QA 对 CRUD+确认生效（复用 qa-pairs 组 API）；KB 下拉切换即重载；**无分页**（固定 page:1/pageSize:100，超百条截断）；loadQaPairs 无 catch（qa-detail:17-111 ‹CERTAIN›）。
- **TASK-005 数据挖掘任务** — `/robot-operation/data-mining`（**robot 组唯一真 API 页**）：新增（ruleConfig 为裸 JSON 文本域，前端不校验）/执行/删除/搜索（data-mining:13-131 ‹CERTAIN›；执行后无条件「执行完成」不读结果，INFERRED-2）。
- **TASK-006 robot 三伪页** — faq-analysis/intent：调 getKbAnalytics 后**硬编码行**（仅填 totalKBs/totalFiles/totalChunks）；multi-turn：`const a = res || {}` 后 a 未使用，两行纯写死；与 kb-analytics 期望的字段形状冲突（即使后端返回真实数据也恒 0/undefined）；分页 handler 因伪造行数永不显示（faq-analysis:14-26; multi-turn:14-24; intent:16-24 ‹CERTAIN›，INFERRED-1）。
- **TASK-007 审计系统日志** — `/admin/audit/system-log`：分类单选（审计/登录/操作）+ 关键词/模块筛选 + 行展开详情（el-descriptions）；`GET /logs` 全库唯一消费方（容错解析 records/list）；只读（system-log:38-122 ‹CERTAIN›）。

## 3. Pages & Routes

六路由 + robot 四路由：侧边栏拆挂——kb-analytics 挂「知识库」组（Sidebar:107）、model-monitor/feedback 挂「应用」组（:146-147）；**qa-detail 无侧边栏登记**（权限键已定义 auth.ts:74 但不在 MENU_PERMISSION_MAP）；robot 4 页无侧边栏（menu-perm-tree:17 注释孤儿）。权限门禁：各登记路径有 menu:operation:* 键。

## 4. Elements & Interaction

共享组件仅 feedback/model-monitor 两页消费：MetricCards（58 行）、RankList（泛型+作用域插槽）、WordCloud（仅 feedback；字号 12+min(count/5,28)px；cursor:pointer 但无 click——死交互）。**颜色语义相反**：kb-analytics 内联卡 up=success/down=danger，MetricCards up=danger/down=success（INFERRED-3）。feedback 双分页共用 BEM 类；搜索反馈三路联动刷新（列表+统计+总览）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| kb-analytics | v-loading | el-empty ×2 | **catch 静默** | :37-62,94-108 |
| model-monitor | v-loading | el-empty ×3 | ElMessage.error | :29-136 |
| feedback 三区 | 独立 loading ×3 | el-empty ×4 | 读静默/写 toast | :27-382 |
| qa-detail | v-loading | 表格 empty-text | **无 catch 外抛** | :25-32,86,98 |
| robot data-mining | v-loading | el-empty | **无 catch**（保存/执行外抛） | :35-68 |
| robot 三伪页 | v-loading | **无 empty 态** | try/finally 无 catch | 各页 |
| system-log | v-loading | — | catch 清空 | :9,42-44 |

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| KB 分析 | GET `/analytics/kb`（api:19-21）→ metrics/hotKBs/hotDocs | TBD — pending API Contract |
| 模型监控 | GET `/monitor/model/overview?timeRange&keyword&page`（api:1998-2000） | TBD |
| 反馈 | GET `/feedback(/overview,/statistics)`、POST/PUT/DELETE/reply（api:1413-1443）；overview 消费 metrics 5 键/questionCategories/hotKeywords/appSatisfactionRanking | TBD |
| 会话明细 | GET `/chat-sessions`（api:1445-1447） | TBD |
| 数据挖掘 | GET/POST `/data-mining`、DELETE、POST `/:id/run`（api:1513-1531） | TBD |
| 统一日志 | GET `/logs`（api:1386-1396） | TBD |
| QA 复用 | `/kb/:kbId/qa-pairs` 组（qa-pairs 链契约） | — |
| 类型 | types/feedback.ts（90）/monitor.ts（47）注释声明「与后端对齐」，无 schema 验证 [GAP] | — |

死代码（INFERRED-4）：`getFeedback`（与 getFeedbackPage 同 URL 重复定义）、`getAuditLogs`、`getLoginLogs`、`getDataMiningTask`；`mock/robot-operation.ts` 全库零 import；`mock/interceptor.ts` 未接线。notifications/home 组与本特性页面零直接消费。

## 7. Responsive & Accessibility

桌面优先 [GAP]。

## 8. Acceptance

- [ ] 各页路由可达且侧边栏挂载正确（qa-detail/robot 无入口为现状）
- [ ] model-monitor 时间范围/搜索/分页联动单接口
- [ ] feedback 回复/新增/编辑/删除/建议生成全链路；问答明细懒加载
- [ ] qa-detail CRUD+确认生效；KB 切换重载
- [ ] data-mining 新建/执行/删除（ruleConfig 现状不校验）
- [ ] system-log 分类/筛选/行展开
- [ ] robot 三伪页行为与现状一致（INFERRED-1 裁决前不动）

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 robot 三伪页（faq/multi-turn/intent 数据硬编码、字段形状与后端冲突）——接真实现 or 删除？INFERRED-2 data-mining 执行后无条件「执行完成」且 ruleConfig 不校验 JSON——修？INFERRED-3 指标卡颜色语义两处相反（kb-analytics vs MetricCards）——统一？INFERRED-4 死 API ×4 + mock/robot-operation 清理？INFERRED-5 feedback 回复 operator 硬编码 'admin'——取当前用户？INFERRED-6 qa-detail 无分页（固定 100 截断）+ 无 catch——补？INFERRED-7 WordCloud 死交互（pointer 无 click）——补 or 去样式？INFERRED-8 qa-detail/robot 权限键已定义未入 MAP——补登记 or 删键？
