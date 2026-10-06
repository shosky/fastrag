# 知识图谱 — Frontend Architecture Design（knowledge-graph-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/knowledge-graph-frontend.md`。

## 1. Page Map

| PAGE | 宿主 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| 图谱 Tab | `/knowledge/:id` Tab graph | 宿主权限 | 详情 Tab | Tab 切换 |
| 索引管理/设置/节点详情/关系详情 | Tab 内弹窗/浮层 | 随宿主 | 浮动操作区 | 关闭 |

## 2. Page/Layout/Component Map

```
┌────────────────────────────────────────────────────────────┐
│ 构建状态横幅: building(进度+X实体·Y关系·built/total) |        │
│              completed✅(8s 隐) | failed❌+错误+[重试]        │
├────────────────────────────────────────────────────────────┤
│ ┌── G6 画布（v-loading，force 布局，>400 节点降级）─────────┐ │
│ │            浮动搜索条: [🔍实体____][搜索][刷新]            │ │
│ │            浮动操作区: Chunk开关 | [索引管理] [图谱设置]     │
│ │            [展开邻居](仅选中时)                            │ │
│ └────────────────────────────────────────────────────────┘ │
│ 统计条: 实体 N · 关系 M（搜索态: 总数/可视数）                 │
│ 弹窗族: 实体类型分布(点击过滤/显示全部) | 关系详情(tag+源目标+描述) │
│         IndexManagementPanel(四态tag/进度/统计/重试)          │
│         GraphSettingsPopup(maxNodes/depth/excludeChunks/entitySchema) │
│ NodeDetailPanel(右侧，只读属性表)                             │
└────────────────────────────────────────────────────────────┘
```

组件判定：useForceGraph（G6 生命周期+数据转换+三查询通道封装，单一主消费者但行为深度高，成立）；useKnowledgeGraph **不作为图谱主实现**（平行实现，INFERRED-1）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 图数据/统计 | server → useForceGraph（allNodes/allEdges 全量缓存 + originalData） | 过滤/搜索基于缓存 |
| 选中节点/边 | client（G6 事件 → ref） | 驱动详情面板/展开浮钮 |
| 类型过滤 | client（filterByType） | 边需两端可见 |
| isSearchActive | client | 搜索/展开后置位（统计切「总数/可视数」） |
| 构建状态 | server → 父组件 5s 轮询（watch isBuilding 启停） | props.build-status 下发 banner |
| 图谱设置 | server（GET 回填/PUT 保存） | maxNodes 挂载预载一次（INFERRED-6） |
| Chunk 开关 | client（el-switch 归一） | load(excludeChunks) 参数 |

## 4. Data Flow

```
fetchGraphData(maxNodes) → transformData（source_id/target_id 确定性连线，名称映射兜底）
  → G6 渲染（降级阈值 400：animate off + 布局收紧 + 隐标签）
搜索: searchNodes(本地缓存) → 下拉 ｜ searchGraphNodes(远程) → 整图替换
展开: expandGraphSubgraph(depth=2,max=30) → 手工映射 {entities,relations}→{nodes,edges}
设置: GET 回填 → PUT 保存（entitySchema 白名单随构建生效）
状态: getGraphBuildStatus 5s 轮询 → banner
命名转换点: transformData（连线确定性）、normalizedEntityTypes（旧新版兼容+8 色板）、
          expand 响应手工映射（as any——类型债 INFERRED-5）
```

## 5. Visual System

G6：节点 circle 24 底部标签；边 line+三角箭头+白底标签；8 色循环实体色板（ENTITY_TYPE_COLORS，源自 mock 常量——**唯一存活的 mock 引用**）；banner 三态视觉。断点无。

## 6. Parent Trace

- 降级阈值 400 → spec TASK-002（大数据可用性）
- 确定性连线（source_id 优先）→ spec TASK-001（同名不同型错连防线）
- 轮询启停/8s 隐藏 → spec TASK-003
- expand 手工映射 + 双封装 → spec INFERRED-5
- merge 缺失 → spec INFERRED-2（REQ 候选落空，待裁决）
