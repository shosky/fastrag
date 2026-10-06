# 分片管理 — Implementation Document（chunk-management-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式：描述已实现行为）。上游：spec → design → `docs/contract/ui.md` 特性 5 节。

## 1. Code File Map

```
src/views/knowledge/detail/
├── chunks.vue                          — PAGE-001 真页面（路由挂载；四类目分支；全真实 API；2634 行）
└── components/
    ├── FileMetadataEditor.vue          — 元数据编辑弹窗内嵌（与 FileManager 共享，≥2 消费者）
    ├── ChunkManagementPanel.vue        — ~~mock 死原型~~（fixed 覆盖层，唯一入口分支生产不可达；INFERRED-1 待裁决）
    └── FileTable.vue / FileManager.vue — 入口供给方（行命令 manageChunks → 本页路由，属 file-ingestion 链）
src/api/index.ts                        — chunks 组端点（getChunks/getKbMarkdown/saveKbMarkdown/createChunk/
                                          updateChunk/deleteChunk/batchDeleteChunks/reChunkFile）+ qa 四操作
```

## 2. Interface Implementations

### 分片列表加载与视图模型（contract: getChunks Data Mapping）

**Logic Flow:**
```
loadChunks(page, pageSize):
1. GET getChunks(kbId, fileId, page, pageSize)
2. res = res?.list || res（兼容解包 :290）
3. FOR each chunk: mapChunk 归一（imageKeys 兼容 JSON 字符串；children 装配；tokenCount=content.length）
4. IF targetChunkId 未定位过 THEN 尝试定位流程
5. 更新 list/total/parentCount/tokenTotal
6. IF 请求失败 THEN catch 静默（INFERRED-4 现状）
```

**Module Interactions:** 调 api chunks 组；被四类目分支与 DocumentPreviewPanel（只读）消费同一端点。

### 重新分片轮询（contract: 重新分片状态机）

**Logic Flow:**
```
reChunk():
1. confirm（文案含「策略改动需重切才生效」）
2. POST reChunkFile(kbId, fileId)  // 无 config = 沿用当前绑定（api 三态注释）
3. reChunking = true；每 3s GET getFileProcessingStatus
4. IF status === completed THEN 清定时器 → loadChunks + loadFileInfo
5. IF status === failed THEN 清定时器 → error toast
6. onBeforeUnmount → 清定时器
```

### 来源定位（contract: 入口定位契约）

**Logic Flow:**
```
locateChunk(chunkId):
1. target = route.query.chunkId；格式 {fileId}_chunk_{index}
2. IF 当前页数据含 target THEN 展开所属父分片 → scrollIntoView 居中 + --highlight
3. ELSE 正则解析 index → 换算目标页 → 翻页重载 → 重试定位
4. watch(route.query.chunkId) 支持同页二次跳转
5. IF 无法解析且未命中 THEN 不动
```

### 父子展开与批量删除（contract: 交互要点）

**Logic Flow:**
```
行点击: selectedChunk = chunk（单选开关）→ 父行渲染 children 块 + 侧栏联动
startEdit(chunk):
1. IF chunkType === 'parent' THEN warning「父分片由子分片自动聚合生成…」RETURN（不弹窗）
2. ELSE 打开编辑弹窗（内容回填）
批量删除:
1. selection → IF 含父分片 THEN confirm 文案追加「连同其全部子分片一起删除」
2. POST batchDeleteChunks(ids) → 清空选择 → 重载
3. 全选 toggle 仅覆盖当前页（:850-862）
```

### 页面登记表

| 分支/能力 | 状态归属 | 实现要点 | 证据 |
|---|---|---|---|
| QA 模式 | 页内 ref + server | 独立分页；编辑保存无 try/catch（INFERRED-5） | :398-448,1006-1011 |
| Markdown | server + 页内编辑态 | 懒加载 + markdownLastSavedAt 防覆盖 + 「保存并重切」切回 | :693-738,1076-1110 |
| image | 页内 blob | 缩略图 URL.createObjectURL，点击新窗；无 CRUD/分页（INFERRED-7） | :532-552,1364-1427 |
| video/audio | 页内（播放器+blob） | 时间轴占比定位、seek 联动、区间高亮、ASR 传入 | :783-819,1506-1558 |
| 下载/导出 | — | **stub：仅 success toast** | :626-628,888-894 |

## 3. Decisions & Deviations

- **偏差（对 conventions）**：分页未用 `usePagination`，页内自管理 currentPage/pageSize（:277-279）——特征化记录；是否整改属 INFERRED-4 同批裁决。
- **偏差（对 spec 状态矩阵通用规则）**：列表无 loading、失败静默——矩阵按现状书写并标注 INFERRED-4，未经裁决不"顺手修复"。
- **死原型**：ChunkManagementPanel（Panel）与 chunks.vue 是新旧两套实现（非壳/主体）；其 mock markdown 用未 sanitize 的 `v-html`（Panel:502）——若裁决保留必须先处理；当前不接线（INFERRED-1）。
- **命名勘误**：tokenCount 实为字符数（mapChunk :362）；侧栏「总字符数」显示 tokenTotal——口径 INFERRED-6。
- 无 contract 说 X 代码做 Y 类偏差（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked（无测试基建）。解阻后优先切片（契约可直接生成 fixture）：
1. mapChunk 归一（imageKeys 双形态、父子装配、孤立子分片混排）
2. 来源定位（chunkId 解析/翻页/高亮/同页复跳）
3. 重新分片状态机（completed/failed/轮询清理）
4. 父分片编辑拦截 + 批量删除级联文案

## 5. Implementation Trace

```
TASK-001/002 → PAGE-001 列表/搜索 → getChunks + mapChunk → chunks.vue:277-301,345-372,477-487
TASK-003/004/005 → 编辑/新增/删除 → chunks CRUD + 批量 → chunks.vue:491-624,821-894
TASK-006 → 重新分片 → reChunkFile + 轮询 → chunks.vue:632-674
TASK-007 → Markdown → getKbMarkdown/saveKbMarkdown → chunks.vue:693-738
TASK-011 → 来源定位 → chunkId 契约 → chunks.vue:285-337（入口×4：FileManager:476-486, debug.vue:162, SearchTestPanel:279, runtime.vue:211）
```
