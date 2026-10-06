# 知识图谱 — Frontend Spec（knowledge-graph-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 10 证据包；术语 ← `CONTEXT.md`（知识图谱）。

## 1. Scope

覆盖图谱 Tab（G6 画布/构建状态横幅/搜索/类型过滤/展开邻居/统计）、索引管理面板、图谱设置弹窗、节点详情面板、单文件图谱开关。**排除**：图谱构建执行（后端 MQ）；`useKnowledgeGraph` 主流程（平行实现，仅被评测面板取 chunkCount——INFERRED-1）；实体合并（**前后端均无 merge UI/API——feature-map REQ 候选落空，GAP**，INFERRED-2）。

## 2. User Tasks

- **TASK-001 查看图谱** — 详情 Tab「知识图谱」：G6 力导画布（drag-canvas/zoom-canvas/drag-node；force 布局 preventOverlap/linkDistance 150 等）；数据 `fetchGraphData`（maxNodes=KB 设置覆盖，默认 500、硬顶 5000）；确定性连线优先 `source_id/target_id`，历史数据回退名称映射（useForceGraph:55-226 ‹CERTAIN›）。
- **TASK-002 大图降级** — 节点 >400：`animate:false` + 布局收紧（nodeSpacing 10/linkDistance 80/alphaDecay 0.05）+ 隐藏节点标签；原始数据保留 `originalData` 供搜索/选中（:244-262 ‹CERTAIN›）。
- **TASK-003 构建与重试** — 索引管理面板「开始索引」（全库无 body）/「重试索引」（failed 态）；单文件开关（FileManager：开=buildGraphIndex `{fileIds,mode:'full'}`、关=deleteFileGraph）；构建状态 5s 轮询（watch isBuilding 启停；banner completed/failed 后 8s 自动隐藏；building 显 progress + 「X 实体 · Y 关系 · built/total chunks」）（IndexManagementPanel:38-194; KnowledgeGraphPanel:182-202,230-272; detail/index.vue:163-192; FileManager:597-613 ‹CERTAIN›）。IndexManagementPanel「重置」实为 warning 提示后转调 retry（:81-85，语义存疑 INFERRED-3）。
- **TASK-004 搜索** — 双通道：input 本地过滤下拉（name/label/entity_type includes）；enter/按钮远程 `searchGraphNodes`（maxNodes=100）整图替换 + isSearchActive（:122-143; useForceGraph:371-396 ‹CERTAIN›；远程失败仅 console.warn 无用户提示，INFERRED-4）。
- **TASK-005 类型过滤下钻** — 实体类型分布弹窗：类型项点击过滤（再点取消）、active 高亮、「显示全部」；过滤在全量缓存上做（边需两端可见）；统计条「总数/可视数」（:66-82,344-405; useForceGraph:229-241,40-44 ‹CERTAIN›）。
- **TASK-006 展开邻居** — 选中节点浮钮 → `expandGraphSubgraph`（depth=2、maxEntities=30）→ 响应 `{entities,relations}` 手工映射 nodes/edges（`as any`——与 GraphData 声明不符）；同样置 isSearchActive（KnowledgeGraphPanel:84-89; useForceGraph:310-340 ‹CERTAIN›；**与 `expandQueryWithGraph` 同一端点双封装、声明类型不同**，INFERRED-5）。
- **TASK-007 图谱设置** — GraphSettingsPopup：maxNodes(10-2000)/searchDepth(1-10)/excludeChunkNodes(默认 true)/entitySchema（KG 级实体类型白名单，逗号换行分隔）→ 保存「下次构建生效」；**maxNodes 实际下次 load 生效且仅面板挂载时预载**——应用后当前视图不刷新（GraphSettingsPopup:17-31,42-127; KnowledgeGraphPanel:205-217; index.vue:211 ‹CERTAIN›，INFERRED-6）。
- **TASK-008 节点详情** — NodeDetailPanel：名称/entity_id/标签（chunk 节点加 tag）/normalized_name/描述/属性键值（attributes 兼容 JSON 字符串，非法静默忽略）；**无操作按钮**（NodeDetailPanel:30-105 ‹CERTAIN›；`getGraphNodeDetail` API 零消费）。
- **TASK-009 关系详情** — edge:click（按 id，兜底 source+target 匹配）→ 弹窗：关系类型 tag + 源/目标 + 描述（=source→label→target 拼句，无独立 description）（useForceGraph:138-147; KnowledgeGraphPanel:378-405 ‹CERTAIN›）。

## 3. Pages & Routes

无独立路由：详情 Tab「graph」（detail/index.vue:290-319）挂 4 组件（KnowledgeGraphPanel/NodeDetailPanel/IndexManagementPanel/GraphSettingsPopup）；FileManager 单文件开关为跨链入口。

## 4. Elements & Interaction

浮动搜索条 + 浮动操作区（Chunk 节点开关 el-switch——手工归一 string|number|boolean、索引管理钮、设置钮）+ 选中节点浮钮「展开邻居」+ 底部统计条。实体类型兼容归一：`string[]`（旧，count=0）与 `{name,count}[]` 统一补 8 色循环色板（:97-115 ‹CERTAIN›）。el-switch handleChunkToggle 归一（:91-94）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 图数据 load/搜索/展开 | loading → 画布 v-loading | **无专门空态**（stats=0 统计条） | **catch 仅 console.warn**（画布空白无提示）——三处一致 | useForceGraph:269-295,335-339,383-395 |
| 构建状态轮询 | banner progress | — | 拉取失败 console.warn；banner 8s 自动隐藏 | index.vue:163-192; KGP:182-192 |
| 设置弹窗 | settingsLoading v-loading | — | 远端失败 console.warn 保留默认 | GSP:42-59 |
| 索引面板 | actionLoading 提交态 | 无 pending → disabled「已全部构建」 | el-alert 错误 + 重试 | IMP:38-194 |
| 启动/重试/开关 | toast + actionLoading | — | error toast | IMP:48-85; FM:597-613 |

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 图数据 | GET `/kb/{kbId}/graph?excludeChunks&maxNodes`（api:560-562）→ transformData（source_id/target_id 优先） | TBD — pending API Contract |
| 统计 | GET `…/graph/stats`（:574-576） | TBD |
| 搜索 | GET `…/graph/search?keyword&maxNodes=100`（:603-605） | TBD |
| 展开 | POST `/graph/expand`（:565-572；depth=2/maxEntities=30；响应手工映射） | TBD |
| 设置 | GET/PUT `…/graph/settings`（:594-600） | TBD |
| 构建/状态/重试 | POST `…/index/build`、GET `…/build-status`、POST `…/index/retry`（:586-620） | TBD |
| 单文件删除 | DELETE `…/graph/file/{fileId}`（:613-615） | TBD |
| ~~节点详情/索引状态/labels/rank~~ | **零消费死代码 ×4**（api:578,582,608,623，INFERRED-7） | — |
| ~~merge~~ | **不存在**（前后端均无，INFERRED-2） | — |

## 7. Responsive & Accessibility

画布 ResizeObserver→changeSize+fitView；桌面优先 [GAP 其余]。

## 8. Acceptance

- [ ] 画布交互三模式 + 点选节点/边 + canvas 清选；Resize 自适应
- [ ] >400 节点降级（无动画/收紧布局/隐标签）且搜索选中仍可用
- [ ] 构建横幅三态 + 5s 轮询启停 + 8s 自动隐藏；重试入口两处生效
- [ ] 搜索双通道；类型过滤下钻与复位；统计「总数/可视数」
- [ ] 展开邻居映射渲染；设置四项保存；节点/关系详情展示
- [ ] 单文件开关开/关各自 API 正确

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 useKnowledgeGraph 平行实现（确定性布局）仅评测面板取 chunkCount——收敛 or 保留？INFERRED-2 实体合并前后端均无——需求缺口确认（REQ 候选删除 or 列入规划）？INFERRED-3 IndexManagementPanel「重置」实为 retry（文案与行为不符）——修？INFERRED-4 搜索/展开失败仅 console.warn——补用户提示？INFERRED-5 `/graph/expand` 双封装类型不一致（GraphData vs 实际 entities/relations）——修类型？INFERRED-6 设置保存「下次构建生效」vs maxNodes「下次 load 生效且需切 tab」——语义统一？INFERRED-7 API 死代码 ×4 清理？INFERRED-8 kbId 兜底三处不一致（'kb_default'/'1'/'default'）——统一？
