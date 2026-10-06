# 文件上传与摄取流水线 — Frontend Architecture Design（file-ingestion-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/file-ingestion-frontend.md`。

## 1. Page Map

本特性无独立路由，全部挂宿主 `/knowledge/:id`（detail/index.vue）默认 Tab「文件管理」。页面 = FileManager 组件（916 行）+ FileTable（658）+ 弹窗族 9 个。跨链边界入口（触发在本链、语义在他链）：策略 tag → parse-strategy、AI 分片 → ai-chunking、分片管理 → chunk-management、在线编辑 → onlyoffice-editing、图谱开关 → knowledge-graph。

## 2. Page/Layout/Component Map

### 文件管理（宿主 Tab）
```
┌────────────────────────────────────────────────────────────┐
│ [上传 kb:upload] [回收站]   面包屑: 全部文件 / 子夹 / … [新建文件夹] │
│ （选中时）批量条: 已选 N [批量移动][批量打标][批量删除][导出]      │
├────────────────────────────────────────────────────────────┤
│ 工具栏: 🔍文件名 | 类型▾4 | 状态▾4 | 全部|文件夹|文件 | 刷新↻   │
│ ┌────────────────────────────────────────────────────────┐ │
│ │ ☑|文件名(图标+QA徽标)|状态(进度/阶段)|时长|页数|策略tag|切片数│ │
│ │  |大小|图谱开关|上传/更新时间|操作▾(条件 17 项)             │ │
│ │ ▸ 子文件夹行（当前层，禁选）                                │ │
│ └────────────────────────────────────────────────────────┘ │
│ 分页 usePagination(10)（前端切片）                           │
└────────────────────────────────────────────────────────────┘
弹窗族: FileUploader(4步) | FilePreviewDialog | MoveFileDialog(单/批量复用)
        | RenameFileDialog | 新建/重命名文件夹 | RecycleBinDialog
        | FileMetadataDialog | 批量打标
```

### 上传向导（FileUploader，4 步）
```
┌──────────────────────────────────────────────┐
│ 步骤条: ①选文件 ②处理参数 ③分块预览 ④确认上传    │
│ step1: 拖拽/选择区 → 文件列表(即选即传+进度条)   │
│ step2: 引擎区(OCR/ASR/视频策略+关键帧——条件显隐) │
│        TXT/CSV 编码 | 音视频行内时间裁剪        │
│        优先级(禁用:暂未生效) | QA方式(开发中)    │
│ step3: per-file 分块预览（loading/错误重载/空/列表）│
│ step4: 配置汇总（「确认上传」→ 提交处理任务）     │
└──────────────────────────────────────────────┘
```

### 预览（FilePreviewDialog 六分支）
```
┌──────────────────────────────┐
│ 标题 + [使用 OnlyOffice 打开](仅office) │
│ pdf.js | 图片 | office(mammoth/XLSX/pptx) │
│ | 文本(=分片拼接,Markdown|Chunks) | 音视频 | 未知→下载兜底 │
└──────────────────────────────┘
```

组件判定：FileTable（单消费者但行为深度高，成立）；MoveFileDialog 复用为批量（`:file="selectedFiles[0]"` 仅借文件夹树——借用姿态记录，INFERRED-9）；VideoPlayer/AudioPlayer 有 UI 无数据接线（INFERRED-7）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 文件列表/文件夹树 | server → useFiles（list/folders/tree） | 3s 轮询复用同一 load() |
| 当前文件夹/面包屑 | client（currentFolderId + 树回溯） | 根='' |
| 筛选/排序/分页 | client（usePagination(10) 前端切片） | 排序仅当前页（INFERRED-1） |
| 上传队列（进度/状态） | client（FileUploader 内 files 数组） | step1 即传，与 processFile 解耦 |
| 轮询定时器 | client（FileManager setInterval 3s，fileIds 白名单） | 自停+卸载清理 |
| 选中集 | client（selectedFiles；文件夹禁选） | 驱动批量条 v-if |
| 预览 blob | client（objectURL，关闭 revoke） | 认证 fetch→匿名重试 |
| 回收站数据 | server（打开时拉取） | 空态 el-empty |
| 路由 :id | URL | 宿主 kbId 源 |

## 4. Data Flow

```
api files/folders 组 → request 解包 → useFiles（列表/文件夹唯一封装）→ FM/FT
上传流：FileUploader 直接 request.post（绕过 api.uploadFile 死路径，INFERRED-8）
        → emit(files,{config,fileIds}) → FM 逐文件 processFile（fire-and-forget）→ startPolling
状态流：3s 全量 getFiles → FT 重渲染 ProcessStatusBar（stage 模糊匹配）
预览流：FPD fetch(带 Bearer→匿名重试) → objectURL → 六分支渲染；文本 = getChunks 拼接（DPP）
命名转换点：displayRows 组装（FT:122-129，切片→排序顺序即 INFERRED-1 缺陷位置）
```

## 5. Visual System

- 状态可视化为本特性视觉核心：ProcessStatusBar 四态 + 五套类目阶段步骤条（PSB:22-39）。
- 批量条条件渲染；策略 tag 可点态/默认置灰态；QA 徽标。
- 断点/键盘：全站一致（无响应式 [GAP]）。

## 6. Parent Trace

- 文件夹行+面包屑（无树） → spec TASK-001/004
- 上传即传、确认即处理的解耦 → spec TASK-002（processFile 汇集在 step4）
- 3s 全量轮询 + 阶段步骤条 → spec TASK-003（INFERRED-4 优化悬置）
- 六分支预览 + blob 认证加载 → spec TASK-005（INFERRED-5/7 悬置）
- 跨链入口条件显隐矩阵 → spec §4（边界归属表）
