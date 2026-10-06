# 文件上传与摄取流水线 — Frontend Spec（file-ingestion-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 4 证据包；术语 ← `CONTEXT.md`（Ingestion Pipeline）。

## 1. Scope

覆盖知识库详情「文件管理」域：文件夹层级导航、文件表格（筛选/排序/批量）、上传向导（4 步 + 分块预览）、处理状态轮询与阶段展示、预览分流、回收站、移动/重命名/复制/元数据/批量打标、下载。**排除**：分片管理页（chunk-management 链）、AI 分片面板（ai-chunking 链）、换绑对话框语义（parse-strategy 链——本链只含触发入口）、图谱开关（knowledge-graph 链）、OnlyOffice 编辑（onlyoffice-editing 链）。

## 2. User Tasks

- **TASK-001 浏览文件** — 文件夹行 + 面包屑层级导航（无左侧树；当前层子文件夹作为表格顶部行；根目录只显 `!folderId` 文件）；工具栏：文件名搜索/类型 4 筛/状态 4 筛/全部-文件夹-文件/刷新；排序 4 列（切片数/大小/上传/更新）；分页 `usePagination(10)` **前端切片**（FT:111-129,235-291,507-515; FM:173-247 ‹CERTAIN›；**切片在前排序在后 → sort-method 仅当前页有效**，INFERRED-1）。
- **TASK-002 上传向导** — 4 步（选文件→参数→分块预览→确认）：step1 选文件后**立即上传**（逐文件进度条，axios onUploadProgress）；同名冲突比对 `existing-file-names` → 确认「跳过」；step2 参数（引擎区条件显隐：有图→OCR、有音视频→ASR、有视频→视频策略+关键帧间隔；TXT/CSV 才显编码；音视频行内时间裁剪互相 clamp）；优先级「暂未生效仅记录」、QA 处理方式「开发中」禁用；step3 per-file 分块预览（三态）；step4 固定 2s sleep 后「确认」→ 逐文件 fire-and-forget `processFile` → toast「已提交 N 个处理任务」→ 启动轮询（FU:217-301,344-353,622-649,810-836,890-1006; FM:133-141 ‹CERTAIN›；step4 非 Promise 汇合 INFERRED-2；URL 上传为 0 字节占位语义存疑 INFERRED-3）。
- **TASK-003 处理状态跟踪** — ProcessStatusBar：pending/processing/completed/failed 四态；processing 显进度条 + 按类目阶段步骤条（document/image/audio/video 五套阶段数组，模糊匹配容错）；failed 显「失败于：{stage}」。**轮询 = 3s 全量 getFiles 重拉**（仅追踪本次上传 fileIds，全部离开 processing 即停；换绑后重启；卸载清理）；`getFileProcessingStatus` 端点列表页不用（FM:28-43,141,591-622; PSB:13-80 ‹CERTAIN›；INFERRED-4）。
- **TASK-004 文件夹管理** — 新建（名称 ≤50）/重命名/删除（须空文件夹，确认文案；删除当前所在文件夹则退回根）（FM:150-168,258-291,278-285 ‹CERTAIN›）。
- **TASK-005 预览** — FilePreviewDialog 六分支：pdf.js/image（blob 认证加载，失败匿名重试，关闭 revoke）/office（mammoth/XLSX/pptx-preview，**旧二进制 .doc/.xls/.ppt 同现代库，兼容性存疑** INFERRED-5）/text/音视频/unknown 下载兜底；文本预览 = **getChunks 分片拼接 markdown**（非原文件）双视图；仅 Office 可「使用 OnlyOffice 打开」（FPD:114-144,187-209,355-417; DPP:71-88; FM:460-466 ‹CERTAIN›）。
- **TASK-006 失败重试** — 行操作（仅 failed）→ `retryFile` → 轮询（FM:338-341; UF:75-78 ‹CERTAIN›；成功 toast 在 await 前，乐观 INFERRED-6）。
- **TASK-007 回收站** — 删除 → 回收站（可恢复）；恢复/彻底删除/清空均有二次确认（FM:324-336; UF:124-142; RBD:27-77 ‹CERTAIN›）。
- **TASK-008 移动/复制/重命名/元数据/批量** — 移动：同 KB 选文件夹 / 跨 KB「移动并重新处理」（提示「分片和向量将被清理」，目标=当前禁用）；重命名（去扩展名编辑）；复制；元数据 schema 驱动编辑（必填缺失阻止保存）；批量移动/打标（增/移两组多选）/导出（逐个下载）（MFD:34-62,85-88,176-191; FM:368-396,497-580; FME:87-98,110-166 ‹CERTAIN›）。
- **TASK-009 下载** — blob 下载（failed 不显下载入口）（FT:471; FM:301,378 ‹CERTAIN›）。

## 3. Pages & Routes

本特性**无独立路由**：宿主 `/knowledge/:id`（detail/index.vue）默认 Tab「文件管理」挂 FileManager（HOST:100,255-263）。Header 的「解析策略」「编辑」带 v-permission。全部面板为 FileManager 弹窗族（FileUploader/FPD/MoveFileDialog ×2/RenameFileDialog/新建与重命名文件夹 dialog/RecycleBinDialog/FileMetadataDialog/批量打标 dialog；AiChunkPanel/StrategyChangeDialog/ChunkManagementPanel 归各自链）。

## 4. Elements & Interaction

- FileTable 列：选择框（文件夹行禁选）｜文件名（emoji 图标 + QA 徽标）｜状态 ProcessStatusBar｜时长/页数｜处理策略 tag（可点，默认策略置灰）｜切片数｜大小｜图谱 el-switch（1/0）｜双时间｜操作更多下拉（17 项条件显隐：在线编辑=isOfficeFile 且非 processing；AI 分片=document 且 5 扩展名；分片管理=completed；下载=非 failed；重试=failed；文件夹行仅重命名/删除）（FT:305-502,148-218 ‹CERTAIN›）。
- 批量操作栏仅选中时渲染（已选计数/批量移动/打标/删除/导出）（FM:657-663）。
- 媒体行内时间裁剪 start/end 互相 clamp（FU:810-836）。
- mock 层实况：`FolderNode` 类型来自 `@/mock/files`（仅类型导入 ×3 处）；`mock/files.ts` 运行时唯一引用是 QaPanel 的死 import——mock 数据层整体死代码（‹CERTAIN›）。
- VideoPlayer/AudioPlayer：关键帧硬编码 mock、transcripts 无 prop 显占位——**FPD 未传，转写/关键帧永远占位**（VideoPlayer:23-36; AudioPlayer:20-25; FPD:394-409 ‹CERTAIN›，INFERRED-7）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 文件列表 | useFiles.loading → v-loading | **表格默认「暂无数据」** | **无 catch**（拦截器统一 toast） | UF:20-32; FT:295 |
| 上传单文件 | el-progress | — | error 状态 + 行内「重试」 | FU:303-314,837-848 |
| 分块预览 | per-file Map | 「文件尚未上传完成」 | error Map + 重新加载 | FU:459-498 |
| 预览 | 各分支加载 | — | el-empty + 下载兜底 | FPD:364-435 |
| 回收站 | — | el-empty「回收站为空」 | — | RBD:73-77 |
| 元数据编辑 | v-loading | — | 加载 toast；必填缺失阻止保存 | FME:32-37,87-98 |
| 移动对话框 | 切 KB v-loading | — | warning toast | MFD:44-61 |
| 宿主 KB 信息 | — | — | catch 回填「示例知识库」硬编码 | HOST:83-94 |

乐观 toast 清单（INFERRED-6）：删除/重试/复制/重命名成功提示先于 API 完成（FM:331-332,340,522,579）。

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 文件列表 | `getFiles` GET `/kb/{kbId}/files`（api:132-134；容忍 `list\|\|records\|数组` 三形态 UF:27, HOST:59） | TBD — pending API Contract |
| 文件夹树/CRUD | `fetchFolders`/createFolderApi(root→null)/renameFolderApi/deleteFolderApi（api:305-321） | TBD |
| 上传/处理 | **FileUploader 直接 `request.post('/kb/{kbId}/files')`**（FU:268-271；`api.uploadFile` 唯一消费者 useFiles.upload 无 UI 调用——死路径 INFERRED-8）+ `processFile`（api:140-148） | TBD |
| 状态流转 | `updateFile`（重命名/移动/批量移动/图谱开关共用，api:150-152）、`retryFile`（154-156） | TBD |
| 回收站 | deleteFile/restoreFile/permanentDeleteFile/emptyRecycleBin/getDeletedFiles（api:136-172） | TBD |
| 移动/复制 | moveFileToKb/copyFile（api:174-180） | TBD |
| 预览 | previewFileChunks（186-199）；`getChunks` 拼文本预览（DPP:71-88） | TBD |
| 下载 | downloadFile（blob，api:220-222） | TBD |
| 元数据/打标 | get/updateFileMetadata、batchSetFileTags（api:268-294） | TBD |
| 上传响应 | `id\|\|fileId` 双字段兼容（FU:285-292） | TBD |

死导出（INFERRED-8）：`fetchFolderName`、`setFileTags`、`extractFileMetadata`；`getFileProcessingStatus` 列表页不用（仅 chunks.vue）。

## 7. Responsive & Accessibility

桌面优先无断点（全站一致 [GAP]）；键盘/读屏 [GAP]。

## 8. Acceptance

- [ ] 文件夹行 + 面包屑导航正确；根目录只显无 folderId 文件；筛选/排序/分页（前端切片）生效
- [ ] 上传：即选即传带进度；同名跳过确认；引擎区条件显隐；时间裁剪互相 clamp；确认后逐文件 processFile + toast N 个任务 + 轮询启动
- [ ] 处理中行显示阶段步骤条并随轮询推进；全部离开 processing 轮询自停；卸载清理
- [ ] 预览六分支正确；文本预览为分片拼接；Office 可跳 OnlyOffice；预览失败有下载兜底
- [ ] 回收站恢复/彻底删除/清空均有确认
- [ ] 跨 KB 移动提示「分片和向量将被清理」；目标=当前禁用
- [ ] 元数据必填缺失阻止保存；批量打标增/移两组生效
- [ ] failed 行显「失败于：{stage}」且有重试；failed 无下载入口

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 前端切片先于排序（排序仅当前页）是否改服务端分页排序？INFERRED-2 step4 固定 2s sleep（非 Promise 汇合，可能提前确认）是否修？INFERRED-3 URL 上传 = 0 字节占位文件，语义是否按缺陷处理？INFERRED-4 轮询全量重拉 vs getFileProcessingStatus 单文件端点闲置，是否优化？INFERRED-5 旧二进制 office 格式预览兼容性是否处理？INFERRED-6 乐观 toast（成功提示先于 API 完成）是否改为结果后提示？INFERRED-7 VideoPlayer/AudioPlayer 关键帧 mock 未接线、transcripts 恒占位——接线 or 删能力？INFERRED-8 死路径清理（api.uploadFile、fetchFolderName、setFileTags、extractFileMetadata）与 mock 类型依赖（FolderNode）是否解耦？
