# 检索与查询增强 — Implementation Document（retrieval-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/components/
├── SearchTestPanel.vue        — 检索测试主面板（877：管线/历史/结果/高亮）
├── RetrievalSidebar.vue       — 运行时调参侧栏（272，320px sticky，显式保存）
└── RetrievalSettingPanel.vue  — 初始化设置六段受控面板（278，表单 Tab 内）
src/views/operation/retrieval-analysis.vue — 日志分析页（349）
src/services/query-preprocess.ts — autoCorrect/rewriteQuery（mock 委托，33）
src/composables/{useSynonyms.ts 54, useGraphExpansion.ts 59}
src/types/knowledge.ts:323    — RetrievalConfig = RetrievalSettingConfig（三面板共享类型）
```

## 2. Interface Implementations

### 预处理管线（contract: PAGE-001 核心）

**Logic Flow:**
```
handleSearch():
1. IF 空输入/无图 THEN warning RETURN
2. IF 有图: effectiveQuery = "[图片] " + 文件名列表（占位实现 INFERRED-5）
3. IF autoCorrection(默认 true): autoCorrect(mock) → 纠错条 + 采用 corrected
4. IF queryRewrite(默认 true): rewriteQuery(mock) → 重写 tags
5. IF graphExpansion(默认 false): await graphExpand（Map 缓存；无 catch → 冒泡）
   ELSE expandedQuery=''
6. IF synonymExpansion(默认 true): await synonymExpand（catch 静默回退原 query）
7. searchRetrieval({knowledgeId||'1', finalQuery, config}) → 卡片渲染 + 写历史（中间 query）
config 深度 watch: 已搜索过且有输入 → 自动重搜
```

### 结果高亮

```
highlightFull(text, tokens): HTML 转义 → tokens 按长度降序 → 逐个包 <mark>（防嵌套替换）
```

### 配置保存（Sidebar → KB）

**Logic Flow:**
```
save → updateKnowledgeBase({name(必填随带), retrievalConfig}) → toast；失败「保存失败」
回填: loadKbDetail → retrievalConfig 非空字段合并进默认 config（detail:73-112）
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 历史 | localStorage | kbId 隔离 ≤50、seq 恢复、写入静默、失败清空 | SearchTestPanel:117-177 |
| 图片队列 | 页内 | ≤5/10MB/去重/objectURL 释放 | :52-89 |
| 分析页 | server ×2 端点 | 指标 ??0；loadLogs 无 catch（INFERRED-7）；已删 KB 文案 | retrieval-analysis:69-238 |
| Rerank 列表 | getModels | 失败回退硬编码两模型 | SettingPanel:18-28 |

## 3. Decisions & Deviations

- **双轨预处理（INFERRED-6）**：主面板本地 mock（刻意绕开 stub 后端，与 feature-map 记载一致）；debug 页走真实 API——收敛方式待裁决。
- **死代码/死入口（INFERRED-1/3/4）**：SearchTestPanel 死 emits；updateRetrievalLog；goToDebug 未接（debug 页成 URL-only）。
- **默认值不一致（INFERRED-2）**：图谱扩展 SearchTestPanel 默认 false vs SettingPanel 默认 true。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：预处理管线（开关组合/降级路径）、highlightFull（转义+降序）、历史存取（隔离/上限/恢复）、配置合并回填。

## 5. Implementation Trace

```
TASK-001/002/003 → 管线+历史+自动重搜 → SearchTestPanel:52-303
TASK-004 → Sidebar 保存 → detail/index:143-155
TASK-005 → SettingPanel 六段 → :59-247
TASK-006 → 分析页 → retrieval-analysis:109-241
TASK-007 → 溯源 → SearchTestPanel:277-280
```
