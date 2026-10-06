# 分片管理 — Frontend Architecture Design（chunk-management-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/chunk-management-frontend.md`；术语：`CONTEXT.md`。

## 1. Page Map

| PAGE | 路由 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| PAGE-001 分片管理 | `/knowledge/:id/chunks/:fileId` | hidden 详情族（页面内权限） | FileManager 行操作 / debug 页 / SearchTestPanel / runtime 引用定位（chunkId） | 返回钮/面包屑 → 知识库详情 |
| ~~内嵌 ChunkManagementPanel~~ | — | — | 不可达分支（INFERRED-1 待裁决） | — |

## 2. Page/Layout/Component Map

### PAGE-001 主骨架（document 模式）
```
┌──────────────────────────────────────────────────────┐
│ 面包屑: 首页/知识库/{文件名}/分片管理                    │
│ 页头: [←] [分类图标] 文件名 · N 个片段                  │
│       右侧 [新增分片] [重新分片] [下载分片(stub)]        │
├──────────────────────────────────────────────────────┤
│ (document) [Markdown | Chunks]  搜索[ID|内容] 输入      │
│ 批量条: ☑全选(当前页) 已选N [批量删除] [批量导出(stub)]  │
├─────────────────────────────────────┬────────────────┤
│ 分片列表（el-table 展开行）            │ 元数据侧栏(可折叠)│
│  ▸ 父分片 #1 (chunkType=parent)      │  文件信息        │
│    └ #1.1 子分片（选中父行时展开块）    │  分片统计        │
│  孤立子分片作为普通行混排              │  策略信息        │
│  [编辑] [删除](音视频)                │  当前选中分片     │
│ 分页 10/20/50/100（服务端）           │  元数据只读摘要   │
├─────────────────────────────────────┴────────────────┤
│ 弹窗: 编辑分片(70%宽) | 新增分片(类目条件字段) | 元数据编辑 │
└──────────────────────────────────────────────────────┘
```

### PAGE-001 QA 模式（processingMode=qa）
```
┌──────────────────────────────────────────────────────┐
│ 页头同上（无 Markdown 切换）                            │
├──────────────────────────────────────────────────────┤
│ 问答对表格: 问题/答案/状态/来源/创建时间                  │
│   行操作: [确认] [编辑] [删除(confirm)]                 │
│ 独立分页                                               │
└──────────────────────────────────────────────────────┘
```

### PAGE-001 image 模式
```
┌───────────────────────────┬──────────────────────────┐
│ 图片预览（blob，点击新窗）   │ OCR 文本块列表（复用分片数据）│
└───────────────────────────┴──────────────────────────┘
```

### PAGE-001 video/audio 模式
```
┌──────────────────────────────────────────────────────┐
│ 播放器（blob + ASR transcripts）                       │
│ 自绘时间轴（切片 segment 按时长占比定位，当前落区间高亮）  │
├──────────────────────────────────────────────────────┤
│ 切片列表: 起止时间/内容 [编辑] [删除]  ← 点击行 seek+播放 │
└──────────────────────────────────────────────────────┘
```

组件判定（深度检查）：`FileMetadataEditor` 内嵌弹窗复用（与 FileManager 共享，≥2 消费者，成立）；**ChunkManagementPanel 判定浅且不可达**（INFERRED-1：mock 全量、唯一入口分支生产必假）——不深化、待裁决后删除或重写。

## 3. State Ownership

| STATE | 所有者（唯一） | 说明 |
|---|---|---|
| 分片列表/统计 | **server (API)** → 页内 ref 镜像（list/total/parentCount/tokenTotal） | 不入 pinia（项目约定） |
| 分页 | client（页内自管理 currentPage/pageSize —— **未用 usePagination，偏差记录**见 implement §3） | QA 视图独立分页 |
| Markdown 内容 | server + 页内编辑态；`markdownLastSavedAt` 防覆盖 | 404 → markdownNotReady |
| 选中分片（展开开关+侧栏联动） | client 单选 `selectedChunk` | 同一状态驱动展开与侧栏 |
| 批量选择 | client（selection，全选仅当前页） | 父选 → 级联提示 |
| 轮询状态 | client（reChunking + 定时器，onBeforeUnmount 清理） | 3s 间隔 |
| 播放进度 | client（mediaCurrentTime）→ 当前切片高亮 | 单向：播放 → 高亮 |
| 来源定位目标 | **URL**（route.query.chunkId）+ watch 同页复跳 | 入口契约 |
| 媒体 blob | server（fetch → objectURL，卸载释放） | 占位文案过渡 |

## 4. Data Flow

```
api/index.ts chunks 组（getChunks/getKbMarkdown/saveKbMarkdown/createChunk/updateChunk/
                       deleteChunk/batchDeleteChunks/reChunkFile/getFileProcessingStatus/getQaPairs…）
  → 传输：utils/request.ts（唯一解包点）
  → 视图模型：chunks.vue 页内 mapChunk（:345-372，唯一分片归一化点：imageKeys 兼容 JSON 字符串、
     children 装配、字符数当 tokenCount —— INFERRED-6 口径待裁决）
  → 组件：表格/时间轴/侧栏/播放器
```

具名转换点：`mapChunk`（页内）。**禁止**在模板内联解包 imageKeys 等兼容逻辑。

## 5. Visual System

- 布局：主列表 + 可折叠元数据侧栏（全站唯一的页内侧栏形态）；document 模式左右分栏比例由 el-row/el-col 承担。
- token：沿用 global.scss 公共类；`--highlight` 类（来源定位高亮，:2000-2004）。
- 反馈：toast（成功/失败）、confirm（删除/重切/级联提示）、按钮 loading（提交类）；**列表级 loading 缺失**（INFERRED-4 待裁决，不擅自补）。
- 断点：无（桌面优先，全站一致）。

## 6. Parent Trace

- 展开行父子结构 + 父分片编辑拦截 → spec TASK-003
- 重新分片确认文案（绑定一致性）→ spec TASK-006（与 parse-strategy 链 ADR「绑定变更必须重切」呼应）
- chunkId 定位 → spec TASK-011（入口契约：FileManager/debug/SearchTestPanel/runtime 四处）
- Panel 不可达判定 → spec INFERRED-1
- 未用 usePagination → implement §3 偏差记录（conventions 例外待裁决）
