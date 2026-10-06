# 评测与基准 — Implementation Document（benchmark-evaluation-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/
├── index.vue                          — Tab 装配（evaluation 双子 Tab + preselect 持有/联动）
└── components/
    ├── EvaluationBenchmarkPanel.vue   — 基准管理（865：卡片/上传/生成/详情）
    └── RagEvaluationPanel.vue         — 评测全生命周期（830：配置/轮询/结果/缓存策略）
src/composables/
    ├── useEvaluation.ts               — 评估状态/轮询/缓存（149）
    └── useBenchmark.ts                — 基准列表/选项（76）
src/types/evaluation.ts                — Benchmark/Evaluation/Detail/Result + RetrievalConfig 等（236）
src/mock/{benchmark,evaluation}.ts     — ~~孤儿 mock~~（零外部消费 + 契约过期，INFERRED-6）
```

## 2. Interface Implementations

### 评估生命周期（useEvaluation）

**Logic Flow:**
```
start(config):
1. runEvaluationApi → detail
2. 缓存 detail（localStorage fastrag_evaluation_detail_cache，TTL 30min）
3. runningEvalIds.add(id) → startPolling（单定时器 3s 遍历 fetchEvaluationStatus）
4. toast「评估已启动…」；失败「评估失败，请重试」
poll 循环:
  FOR id: status = fetchEvaluationStatus
    IF completed|failed: delete id → 清该 id 缓存 → load()
  轮询异常: 整体 catch 静默
unmount → stopPolling
```

### 运行中详情同步

**Logic Flow:**
```
watch(evaluations):
  IF 弹窗中评估状态 → completed|failed:
    fetchEvaluationDetail（列表接口无逐题 results）
    → 渲染全量
    └ API 失败 → 降级: 列表行拼 detail（recallAt1/3/5=0 占位）
getCachedDetail: TTL 30min；终态主动删缓存防过期
```

### 跨 Tab 联动

**Logic Flow:**
```
基准卡「发起评估」→ emit(start-evaluation, benchmark.id)
父: preselectBenchmark = id; activeTab = 'evaluation'
RagEvaluationPanel.openStartDialog: IF preselect 非空 → config.benchmark = preselect
  → emit('consume-preselect') 清空
（onMounted 自动弹窗分支仅首次挂载生效——非 lazy Tab 不重新 mount，INFERRED-4）
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 上传基准 | form + server | 文件不解析（size→questionCount 估算） | EBP:130-131 |
| 自动生成 | form + server | 名称默认 Test-日期-xxxx；graph 卡 chunkCount ← useKnowledgeGraph | EBP:113,33,407 |
| 同步选项 | useBenchmark.syncOptions | `${name} (N 个问题)` | useBenchmark:32-37 |
| 错误过滤 | 页内 | `filter(r => !r.isCorrect)`（0/1 依赖 falsy） | REP:161-166 |

## 3. Decisions & Deviations

- **特征化记录**：上传「演示」语义（INFERRED-1）；Gold tag 恒「有」；删除 catch 共用取消分支；详情状态 tag 硬编码 success；console.log 残留 ×4（INFERRED-2/3/7/8）。
- **状态不共享（INFERRED-5）**：useBenchmark 双实例，靠手动刷新同步。
- **孤儿 mock（INFERRED-6）**：契约已过期（answerJudgment vs isCorrect/judgeReason；按 name vs 按 id），删除候选。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：useEvaluation 轮询状态机（终态清理/异常静默）、缓存 TTL、降级拼装、preselect 消费流。

## 5. Implementation Trace

```
TASK-001/002/003 → 基准管理 → EBP:102-192,216-529
TASK-004 → 跨 Tab 联动 → EBP:189-192 + index:223-228 + REP:106-109
TASK-005/006 → 启动+轮询+缓存 → REP:113-146 + useEvaluation:37-135
TASK-007 → 结果/详情/降级 → REP:173-240,297-583
TASK-008 → 刷新双面板 → EBP:102-105 + REP:256-260
```
