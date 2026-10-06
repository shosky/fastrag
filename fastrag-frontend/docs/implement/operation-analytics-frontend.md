# 运营分析与系统监控 — Implementation Document（operation-analytics-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/operation/
├── kb-analytics.vue        — 知识资产分析（内联指标卡，catch 静默）
├── model-monitor.vue       — 模型监控（单接口聚合 + MetricCards/RankList）
├── feedback.vue            — 反馈管理（三区 Tabs；动作最重）
└── qa-detail.vue           — 问答明细（无分页无 catch）
└── components/{MetricCards 58, RankList 38(泛型+插槽), WordCloud 40(死交互)}
src/views/robot-operation/
├── faq-analysis.vue / intent.vue   — 伪数据瘦壳（硬编码行）
├── multi-turn.vue          — 伪数据（响应变量未使用）
└── data-mining.vue         — robot 组唯一真 API 页
src/views/admin/audit/system-log.vue — 统一日志（GET /logs 全库唯一消费方）
```

## 2. Interface Implementations

### model-monitor 单接口聚合

**Logic Flow:**
```
watch(timeRange) → currentPage=1 → loadOverview()
loadOverview: GET overview?timeRange&keyword&page&pageSize → {metrics, distribution, topApps, stats{list,total}}
  → MetricCards 渲染 + RankList(插槽注入 token/cost) + 表格分页
```

### feedback 三区

**Logic Flow:**
```
列表: GET /feedback（分页）→ 行操作 回复/编辑/删除(confirm)
统计: GET statistics + overview → MetricCards + WordCloud(热词) + RankList(满意度)
明细: watch activeTab==='qa' → GET /chat-sessions → 只读详情弹窗
写: reply/update/create/delete → toast → 三路联动刷新（列表+统计+总览）
建议: 满意度<80%/反馈率<20%/未解决率>20% → 规则文本 + alert（非 AI）
```

### robot 页族差异

```
faq/intent: getKbAnalytics → 仅填 totalKBs/totalFiles/totalChunks 进硬编码行（字段形状与 kb-analytics 期望冲突）
multi-turn: const a = res || {} 后 a 未使用（纯写死两行）
data-mining: 真实 CRUD+run（ruleConfig 裸 JSON 不校验；执行后无条件「执行完成」）
```

## 3. Decisions & Deviations

- **特征化记录（不擅改）**：robot 三伪页（INFERRED-1）；颜色语义相反（INFERRED-3）；operator 硬编码（INFERRED-5）；qa-detail 无分页无 catch（INFERRED-6）；WordCloud 死交互（INFERRED-7）；死 API ×4 与 mock/robot-operation 零消费（INFERRED-4）。
- notifications/home 组与本特性页面零直接消费（CERTAIN grep）——feature-map 未误记。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：model-monitor 单接口聚合分发、feedback 三路联动、日志容错解析（records‖list）。

## 5. Implementation Trace

```
TASK-001 → kb-analytics → :37-62
TASK-002 → model-monitor 单接口 → :21-136
TASK-003 → feedback 全链路 → :14-382
TASK-004 → qa-detail → :17-111
TASK-005/006 → robot 页族 → data-mining:13-131 + 三伪页
TASK-007 → system-log → :38-122
```
