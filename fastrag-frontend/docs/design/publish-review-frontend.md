# 发布、版本与知识审核 — Frontend Architecture Design（publish-review-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/publish-review-frontend.md`。

## 1. Page Map

| PAGE | 路由/宿主 | 可达性 |
|---|---|---|
| PublishPanel | 详情 Tab publish（**v-if="false"**） | 不可达（INFERRED-1） |
| review-management | `/knowledge-review/management` | URL 直达 |
| flows/flow-design/listeners/compliance/reports/quality | `/knowledge-review/*`（redirect flows） | URL 直达 |

## 2. Page/Layout/Component Map

### PublishPanel（若恢复）
```
┌──────────────────────────────────────────────────────┐
│ Tabs: 版本管理|发布历史|定时发布                        │
│ 版本: [创建发布] 版本表(名称/状态说明列/变更摘要/操作按状态:   │
│   提交审核|通过·驳回(备注弹窗)|发布|回退|重新提交)         │
│ 历史: 发布记录 + [发布知识][撤回][重置上一版] + 线上/线下查看 │
│ 计划: 计列表 + [新建](name+publishTime) + 执行情况弹窗     │
└──────────────────────────────────────────────────────┘
```

### 7 页族（现状形态）
```
review-management: KB(硬编码) 发布历史表 + 发布/撤回/重置弹窗 + 计划 + 策略效果
flows: 硬编码列表 + 通过/驳回 + 管理工具下拉 10 项（部分弹窗）
flow-design: 模板列表(恒空) + 模板库硬编码 + 导入导出(本地 JSON)
listeners: 恒空表 + CRUD(全部失败) + 趋势/告警本地随机
compliance: 恒空 + 检查按钮(运行时 BUG)
reports: 纯 mock + 导出恒失败；quality: 规则恒空 + 假评分
```

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 版本列表/当前发布 | server（PublishPanel refresh Promise.all 双兜底 catch→[]/null） | — |
| 状态机 | 前端常量 PUBLISH_TRANSITIONS（types/audit.ts:46-52 真源） | 行操作按状态渲染 |
| reviewTasks | **死状态**（无赋值） | INFERRED-2 |
| 计划/执行 | server | — |
| flows 列表/模板库/报告 | **页内硬编码** | mock 现状 |
| localStorage | flows 通知配置/listeners 告警/reports 模板 | 未走 API（INFERRED-6） |

## 4. Data Flow

```
真实链（PublishPanel + review-management）:
  versions 组 + reviews 组 + publish 组（history/plans/execution/strategy-effect/revoke/reset/online/offline）
  + getKbUpdateLogs（变更摘要）+ publishApp（误用）
断裂链: 7 页 → 27 个不存在的 api 函数（运行时 undefined）→ 同名能力只在死 mock/knowledge-review.ts（函数名不同）
状态流转伪码: createVersion → [立即发布] submitForReview→transition(submit)→transition(approve)
  → 旧published transition(draft) → 新 transition(publish)；任一步失败 → 降级提示手动发布
```

## 5. Visual System

状态说明列（draft/pending/approved/published/rejected 附意见）+ 标签/颜色常量（types/audit.ts）；el-empty 三处。桌面优先。

## 6. Parent Trace

- v-if="false" 与无侧边栏 → spec INFERRED-1（本链可达性根问题）
- PUBLISH_TRANSITIONS 前端真源 → spec TASK-001
- reviewTasks 死分支 → spec INFERRED-2
- 27 不存在函数 → spec INFERRED-5（整族处置）
