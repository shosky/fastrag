# 知识图谱 — Implementation Document（knowledge-graph-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/
├── index.vue                          — Tab 装配（graph tab 挂 4 组件 + 构建状态轮询持有者）
└── components/
    ├── KnowledgeGraphPanel.vue        — 画布面板（727：横幅/搜索/过滤/统计/两弹窗）
    ├── GraphSettingsPopup.vue         — 设置弹窗（213）
    ├── NodeDetailPanel.vue            — 节点详情只读（225；kbId 兜底 'kb_default'）
    └── IndexManagementPanel.vue       — 索引管理（358：四态/进度/重试）
src/composables/
    ├── useForceGraph.ts               — G6 生命周期 + 数据转换 + 三查询通道（432，主实现）
    ├── useKnowledgeGraph.ts           — ~~平行实现~~（仅 EvaluationBenchmarkPanel 取 chunkCount，INFERRED-1）
    └── useGraphExpansion.ts           — 检索侧查询扩展（59，Map 缓存；retrieval 链消费）
src/views/knowledge/detail/components/FileManager.vue — 单文件图谱开关（跨链入口）
```

## 2. Interface Implementations

### G6 装配与渲染

**Logic Flow:**
```
initGraph: new G6.Graph({modes:[drag-canvas,zoom-canvas,drag-node], force 布局})
事件: node:click→反查回填选中 / edge:click→id 兜底 source+target / canvas:click→清选
ResizeObserver → changeSize + fitView
load(excludeChunks=true):
1. GET graph data + stats（maxNodes=设置覆盖,默认 500 硬顶 5000）
2. transformData: source_id/target_id 优先连线 → 名称映射兜底
3. IF nodes>400: animate=false + 布局收紧 + 隐标签（degraded）
4. 失败: console.warn + 数据清空 + stats 归零（无 UI 提示，INFERRED-4）
```

### 过滤/搜索/展开

**Logic Flow:**
```
filterByType(type): 全量缓存过滤（边两端可见）→ 重渲染；再点取消
本地搜索: name/label/entity_type includes → 下拉
远程搜索: searchGraphNodes(maxNodes=100) → 整图替换 + isSearchActive（失败仅 warn）
展开邻居: expandGraphSubgraph POST → as any → 手工映射 nodes/edges → 渲染
```

### 构建状态轮询

**Logic Flow:**
```
父组件: watch(isBuilding) → setInterval(fetchBuildStatus, 5000)；非 building 清除；unmount 清理
banner: completed/failed → 8s 自动隐藏
单文件: 开=buildGraphIndex(kbId,[fileId],'full')；关=deleteFileGraph
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 设置弹窗 | server + form | 打开回填（失败保留默认）；保存「下次构建生效」 | GSP:42-127 |
| 索引面板 | server | 无 pending → disabled「已全部构建」；失败 alert+重试 | IMP:108-194 |
| 类型兼容 | 页内归一 | string[]（count=0）‖ {name,count}[] | KGP:97-115 |

## 3. Decisions & Deviations

- **平行实现（INFERRED-1）**：useKnowledgeGraph（确定性圆形布局）非主实现，仅 chunkCount 被评测面板消费——收敛待裁决。
- **类型债（INFERRED-5）**：expand 双封装返回类型声明不一致，消费侧 `as any` 手工映射。
- **kbId 兜底三值**（'kb_default'/'1'/'default'，INFERRED-8）。
- **死代码**：API ×4（node detail/index status/labels/rank）；mock/knowledge-graph 除色板外全死。
- **实体合并缺失（INFERRED-2）**：REQ 候选落空，待用户裁决（规划 or 删除候选）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：transformData（id 连线/名称兜底/降级）、normalizedEntityTypes 兼容、expand 手工映射、类型过滤边裁剪。

## 5. Implementation Trace

```
TASK-001/002 → 画布+降级 → useForceGraph:55-262
TASK-003 → 构建/重试/轮询 → IMP:38-194 + index.vue:163-192 + FM:597-613
TASK-004/005 → 搜索/过滤 → useForceGraph:229-241,371-396 + KGP:66-143
TASK-006 → 展开邻居 → useForceGraph:310-340
TASK-007/008/009 → 设置/详情/关系 → GSP + NodeDetailPanel + KGP:378-405
```
