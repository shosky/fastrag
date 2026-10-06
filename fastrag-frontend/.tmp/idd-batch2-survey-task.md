# 任务书：第 2 批特性链证据调研（2026-09-21）

## 背景

`fastrag-frontend`（Vue3 + TS，路径均相对本目录）正在按 IDD 建前端特性链。特性 1 iam 已建全链（样例格式见 `docs/spec/iam-frontend.md`、`docs/contract/ui.md`）。本批 4 个特性需要**页面级证据**（元素清单/状态/交互/API 消费/file:line），供撰写 spec/design/contract/implement。

**硬性边界：只读 `fastrag-frontend/src/**` 与 `docs/feature-map.md` 本特性小节；不读仓库根 docs/。**

每个特性按以下框架返回结构化 markdown（全部给 file:line 证据；查不到标 GAP）：

- `## 页面与路由`：各 view 文件的路由、入口、页内结构（区域/表格列/表单字段/对话框/按钮）
- `## 任务清单`：用户任务（TASK 候选）：动作 → 成功结果 → 失败恢复
- `## 状态与反馈`：各查询/提交的 loading/empty/error 处理实况（有则引代码，无则标「无专门处理」）
- `## API 消费`：调用了哪些 api/index.ts 函数（函数名 + URL），响应字段的消费点
- `## 交互要点`：非显然交互逻辑（联动/条件显隐/确认流程），伪代码级
- `## 异常与疑点`：stub、mock 消费、死代码引用、与直觉不符处

---

## A. kb-core（知识库管理）

文件：`src/views/knowledge/{index.vue 385, categories.vue 219, create.vue 87, edit.vue 111}`、`src/views/home/index.vue 253`、`src/views/workspace/{index.vue 377, custom-workspace.vue 270}`；常量 `src/utils/constants.ts`（KB_TYPES）。

专项问题：
1. `knowledge/form.vue`（1501 行，已判孤儿）与 create.vue/edit.vue 的关系——create/edit 是否 import 它？还是各自独立小页？
2. 知识库列表页：列结构、搜索/筛选、分页（是否 usePagination + `__pagination`）、行操作（进入详情/编辑/删除/权限设置?）。
3. categories.vue：分类树/列表 CRUD 交互。
4. create/edit 表单字段族（名称/类型/权限/描述?），KB_TYPES 如何消费。
5. home 与 workspace 各是什么（工作台聚合? 快捷入口?），与 kb-core 的关系（归属 INFERRED 待确认项）。
6. api 消费：kb 列表/CRUD/分类/标签 各函数与 URL。

## B. parse-strategy（解析策略）

文件：`src/views/knowledge/detail/parse-strategy.vue`（1079）、`src/composables/useParseStrategy.ts`（87）、`src/views/knowledge/detail/components/{StrategyChangeDialog.vue 361, ChunkLengthSelect.vue 66}`。

专项问题：
1. 页面结构：策略列表? 策略编辑表单（字段族：分片长度/重叠/分隔符/LLM/VLM 模型?）。
2. 设默认/模板/复制等操作。
3. StrategyChangeDialog 的触发时机（改绑定→提示重切?）与文案/确认流。
4. ChunkLengthSelect 用途与取值。
5. api 消费：strategy 组端点（CRUD/resolve/conflicts/meta/template）。

## C. file-ingestion（文件上传与摄取）

文件：`src/views/knowledge/detail/components/{FileManager 916, FileUploader 1706, FileTable 658, FilePreviewDialog 620, DocumentPreviewPanel 488, VideoPlayer 679, AudioPlayer 380, ProcessStatusBar 148, RecycleBinDialog 129, MoveFileDialog 273, RenameFileDialog 102, FileMetadataEditor 188, FileMetadataDialog 61}`、`src/composables/useFiles.ts`（171）；宿主 `detail/index.vue`（526）如何装配这些面板。

专项问题：
1. FileManager/FileTable 职责分工（树? 平铺? 文件夹层级?）；分页/排序/筛选。
2. FileUploader：上传方式（拖拽/选择）、并发/分片/进度/取消、错误处理。
3. 处理状态流转展示（ProcessStatusBar：pending/processing/done/failed? 轮询还是推送）。
4. 预览：8 格式如何分流（pdf.js/mammoth/pptx-preview/xlsx/图片/音视频/文本）。
5. 回收站/移动/重命名/元数据 编辑流。
6. api 消费：files 组 26 端点中实际用到的函数清单。
7. useFiles 的 FolderNode（类型来自 mock 层）实况。

## D. chunk-management（分片管理）

文件：`src/views/knowledge/detail/chunks.vue`（2634，全项目最大页面）、`src/views/knowledge/detail/components/ChunkManagementPanel.vue`（1106）。

专项问题：
1. 为什么两个大文件——职责分工（chunks.vue 是页面壳 + Panel 是主体?）。
2. 分片列表：父子两级如何展示（树/展开行?）、分片查看/编辑（弹窗?）、批量删除交互。
3. 重新分片入口与确认流（是否提示绑定一致性）。
4. 搜索/筛选/分页。
5. api 消费：chunks 组函数与 URL；count 用途。
6. 与 ai-chunking 面板的边界（同页如何切换）。
