# 文件上传与摄取流水线 — Implementation Document（file-ingestion-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/knowledge/detail/
├── index.vue                      — 宿主页（默认 Tab 挂 FileManager；Header 权限钮）
└── components/
    ├── FileManager.vue            — 面板编排者（916：动作栏/批量条/面包屑/轮询/弹窗族装配）
    ├── FileTable.vue              — 表格（658：筛选/排序/分页/17 项条件操作）
    ├── FileUploader.vue           — 4 步上传向导（1706：即选即传/引擎区/预览/确认）
    ├── ProcessStatusBar.vue       — 四态 + 类目阶段步骤条（148）
    ├── FilePreviewDialog.vue      — 六分支预览（620：blob 认证加载/OnlyOffice 跳转）
    ├── DocumentPreviewPanel.vue   — 文本预览（488：getChunks 拼接）
    ├── VideoPlayer.vue/AudioPlayer.vue — 播放器（关键帧 mock 未接线 INFERRED-7）
    ├── RecycleBinDialog.vue       — 回收站（129）
    ├── MoveFileDialog.vue         — 移动（单/批量复用，273）
    ├── RenameFileDialog.vue       — 重命名（102）
    └── FileMetadata{Dialog,Editor}.vue — 元数据 schema 编辑（61/188）
src/composables/useFiles.ts         — 列表/文件夹树唯一封装（171；FolderNode 类型来自 mock 层）
src/api/index.ts                    — files/folders 组 22 个实际消费端点
```

## 2. Interface Implementations

### 列表组装（displayRows，INFERRED-1 缺陷位置）

**Logic Flow:**
```
load(): try getFiles + fetchFolders → loading finally 复位（无 catch，拦截器 toast）
displayRows:
1. folderRows = 当前层子文件夹（树递归取 children）→ 表格顶部（禁选）
2. fileRows = files |> 搜索 |> 类型4 |> 状态4 |> 全部-文件夹-文件
3. paged = filtered.slice((page-1)*10, page*10)   // 先切片
4. paged.sort(sort-method)                        // 后排序 → 仅当前页排序（INFERRED-1）
```

### 上传向导（即选即传 + 确认即处理）

**Logic Flow:**
```
addFiles:
1. 同名比对 existing-file-names → MessageBox 确认跳过
2. FOR file: 立即 request.post('/kb/{kbId}/files', formData, {onUploadProgress})  // 绕过 api.uploadFile
3. progress 更新；失败 → error 态 + 行内重试
step2: 引擎区条件显隐（hasImages/hasAudio/hasVideo）；时间裁剪互相 clamp
step3: FOR file: previewFileChunks（loading/error/data 三 Map）
step4: sleep(2000)（固定，非 Promise 汇合——INFERRED-2）
确认: FOR file: processFile(kbId, fileIds, config)（不 await）
      → toast「已提交 N 个文件的处理任务」→ startPolling(fileIds)
```

### 处理轮询（状态跟踪核心）

**Logic Flow:**
```
startPolling(fileIds):
1. setInterval 3s:
2.   load() 全量重拉列表（非单文件端点——INFERRED-4）
3.   IF 白名单 fileIds 全部离开 processing THEN stopPolling
换绑保存后: startPolling([fileId])；onBeforeUnmount: 清理（FM:620-622）
ProcessStatusBar: status 四态映射；processing → 阶段步骤条（类目五套数组，stage 模糊匹配容错）
                  failed → 「失败于：{stage}」
```

### 预览分流（blob 认证加载）

**Logic Flow:**
```
openPreview(file):
1. url = 需 blob 的类型: fetch(url, {Authorization: Bearer}) → 失败匿名重试 → URL.createObjectURL
2. 按扩展名六分支渲染（office 才显 OnlyOffice 按钮；文本 = getChunks 排序拼接）
3. 关闭: revoke objectURL
```

### 页面登记表

| 能力 | 状态归属 | 要点 | 证据 |
|---|---|---|---|
| 文件夹管理 | server + 页内 | 删除须空；删当前层退回根 | FM:150-168,258-291 |
| 回收站 | server | 恢复/彻底删/清空二次确认 | RBD:27-77 |
| 移动 | server | 跨 KB 文案「分片和向量将被清理」；目标=当前禁用 | MFD:85-88,176-191 |
| 元数据 | server + form | schema 驱动；必填缺失阻止保存 | FME:87-98 |
| 批量打标 | server | 增/移两组多选 | FM:807-825 |

## 3. Decisions & Deviations

- **偏差（对 conventions）**：上传绕过 api/index.ts 直接 request.post（FU:268-271 注释「禁止裸 XHR/fetch」的例外先例——自相矛盾点，INFERRED-8 悬置）。
- **乐观 toast 清单**（INFERRED-6）：删除/重试/复制/重命名成功提示先于 API 完成。
- **宿主兜底**：KB 信息加载失败回填「示例知识库」硬编码（HOST:83-94）。
- **mock 依赖**：FolderNode 类型 import 自 `@/mock/files`（×3 处，仅类型）；mock/files 运行时唯一引用为 QaPanel 死 import——数据层死代码。
- **UI 瑕疵登记**：configSummary「视频策略」重复两项（FU:555-561）。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：displayRows 筛选/切片/排序链、上传向导状态机（含同名跳过）、轮询自停/清理、预览六分支路由、元数据必填校验。

## 5. Implementation Trace

```
TASK-001/004 → 列表/文件夹 → displayRows + 树回溯 → FT:111-129, FM:173-247
TASK-002 → 上传向导 → 即选即传/引擎区/预览/确认 → FU:217-353,459-498,622-649
TASK-003 → 轮询+阶段条 → FM:28-43 + PSB:13-80
TASK-005 → 预览六分支 → FPD:114-435 + DPP:71-88
TASK-006/007 → 重试/回收站 → UF:75-78,124-142
TASK-008 → 移动/复制/重命名/元数据/批量 → MFD/RFD/FME/FM:368-580
TASK-009 → 下载 blob → FM:301,378
```
