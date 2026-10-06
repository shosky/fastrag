# 运营分析与系统监控 — Frontend Architecture Design（operation-analytics-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/operation-analytics-frontend.md`。

## 1. Page Map

| PAGE | 路由 | 侧边栏 | 权限 |
|---|---|---|---|
| kb-analytics | `/operation/kb-analytics` | 知识库组:107 | menu:operation:kb-analytics |
| model-monitor | `/operation/model-monitor` | 应用组:147 | menu:operation:model-monitor |
| feedback | `/operation/feedback` | 应用组:146 | menu:operation:feedback |
| qa-detail | `/operation/qa-detail` | **无登记** | 权限键已定义未入 MAP（INFERRED-8） |
| robot ×4 | `/robot-operation/*` | **无登记**（menu-perm-tree:17 注释孤儿） | 同上 |
| system-log | `/admin/audit/system-log` | 管理→安全审计:192 | menu:admin:audit:system-log + admin:audit |

## 2. Page/Layout/Component Map

### kb-analytics / model-monitor / feedback（指标页族）
```
┌────────────────────────────────────────────────────────┐
│ kb-analytics: 内联指标卡 + 热门KB/热门文档 两 el-empty 区   │
│ model-monitor: [7|30|180天▾][搜索] MetricCards           │
│   ┌分布┐ ┌Top应用 RankList(插槽: token/cost)┐            │
│   └模型表格（分页，与总览同一接口）┘                        │
│ feedback: Tabs[反馈列表|统计|问答明细(懒加载)]              │
│   列表: 筛选+表格+分页(feedback__list-pagination)          │
│   统计: MetricCards + WordCloud(热词) + RankList(满意度)   │
│   明细: 会话表+只读详情弹窗                                 │
└────────────────────────────────────────────────────────┘
```

### qa-detail / robot 页族 / system-log
```
qa-detail: [KB▾] QA表格(问题/答案/状态/操作:确认) + 新增编辑弹窗（无分页）
data-mining: [新增任务(含 ruleConfig JSON 文本域)] 任务表格 [执行][删除] + 搜索
faq/multi/intent: 表格瘦壳（伪数据行）+ 分页 handler（永不显示）
system-log: 分类单选(审计/登录/操作) + 关键词/模块筛选 + 表格 + 行展开 el-descriptions
```

组件判定：MetricCards/RankList/WordCloud 三组件消费方仅两页（2 消费者边界成立）；kb-analytics 内联卡是第三套实现（口径不一致 INFERRED-3）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 各页数据 | server → 页内 ref | model-monitor 单接口聚合（metrics+分布+Top+分页） |
| 时间范围/筛选/分页 | client（watch timeRange 重置页码） | usePagination 模式 ×3 |
| feedback 弹窗表单 | form（新增/编辑共用 isEditingFeedback） | operator 硬编码（INFERRED-5） |
| 问答明细懒加载 | client（watch activeTab==='qa'） | — |
| data-mining ruleConfig | form（裸 JSON 文本，无校验） | — |
| system-log 分类 | client（单选重置页码） | — |

## 4. Data Flow

```
全部经 utils/request.ts 真实 axios（解包/401/403 统一）；mock/interceptor 全库未接线
读: getKbAnalytics / getModelMonitorOverview / getFeedback*(4) / getChatSessions /
    getDataMiningTasks / getLogs / qa-pairs 组
写: submit/update/delete/replyFeedback、data-mining create/run/delete、qa CRUD+confirm
命名转换点: RankList 作用域插槽注入扩展字段（token/cost、satisfaction/feedbackCount）；
          WordCloud 字号线性映射；日志容错解析 records||list
```

## 5. Visual System

MetricCards/RankList/WordCloud 三件套（仅两页消费）；el-empty 空态体系；行展开审计详情。颜色语义冲突记录（INFERRED-3）。

## 6. Parent Trace

- 侧边栏拆挂与孤儿现状 → spec §3（INFERRED-1/8 悬置）
- robot 伪数据 + 字段形状冲突 → spec INFERRED-1
- 单接口聚合（model-monitor） → spec TASK-002
- 死 API ×4 + mock/robot-operation 零消费 → spec INFERRED-4
