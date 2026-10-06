# 分片管理 — Frontend Spec（chunk-management-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式：描述已实现行为；证据分层 CERTAIN=引 file:line / INFERRED=待确认 / GAP=代码外信息）。
> Parent Trace：TASK/PAGE/STATE ← `docs/feature-map.md` 特性 5 证据包；术语 ← `CONTEXT.md`（知识分片/父分片/子分片/重新分片）。

## 1. Scope

覆盖分片管理页 `/knowledge/:id/chunks/:fileId`：父子两级分片展示与编辑、批量删除、重新分片、Markdown 查看/编辑（可选保存并重切）、QA/图片/音视频三类目分支视图、来源定位高亮。

**明确排除：** AI 分片面板（AiChunkPanel，属 ai-chunking 链，与本页为平行入口）；换绑重切对话框（StrategyChangeDialog，属 parse-strategy 链）；`ChunkManagementPanel.vue`（mock 死原型，见 INFERRED-1，不入契约）；QA 对的完整管理（QaPanel 属 qa-pairs 链——本页仅内置 QA 简表）。

## 2. User Tasks

- **TASK-001 查看分片列表** — 进入页面按文件加载分片（服务端分页 10/20/50/100），头部显示「N 个片段」，侧栏显示总分片/父分片/字符统计。〔chunks.vue:287-301,916-937,1241-1360 ‹CERTAIN›；**列表无 loading 态**（见状态矩阵缺陷注记）〕
- **TASK-002 搜索分片** — 按 ID 或内容过滤，**仅过滤当前页**（双模式 select+input，:155-156,477-487,1030-1043；是否升级服务端搜索 INFERRED-3）。
- **TASK-003 编辑分片** — 行「编辑」打开弹窗（ID/索引/Token + 内容 textarea）→ `updateChunk` → toast + 重载；空内容 warning 拦截；**父分片拦截**：「父分片由子分片自动聚合生成，请展开后编辑子片」（:509-517,563-579 ‹CERTAIN›）。
- **TASK-004 新增分片** — 页头「新增分片」弹窗按类目条件出字段：document → 类型（text/image）+ image 页码；audio/video → 起止时间；插入位置 append 或指定索引之后（max=总分片数）（:593-624,1625-1660 ‹CERTAIN›）。
- **TASK-005 删除分片** — 单删（音视频切片）与批量删除（confirm；选中父分片时文案追加「连同其全部子分片一起删除」，全选仅覆盖当前页 :491-494,821-886 ‹CERTAIN›）。
- **TASK-006 重新分片（Re-chunk）** — 页头按钮 → confirm（文案含绑定一致性提示：「如果修改了解析策略…需要重新分片后才会生效」）→ `reChunkFile`（不传 config = 沿用当前绑定）→ 3s 轮询处理状态至 completed → 刷新列表与文件信息；failed → error toast（:632-674,634-638 ‹CERTAIN›）。
- **TASK-007 Markdown 查看/编辑** — document 类目 `Markdown | Chunks` 切换；markdown 懒加载 + `markdownLastSavedAt` 防覆盖已编辑内容；保存可勾选「保存并重新分片」→ 保存后自动切回 Chunks 并重载；404 → el-empty「无 Markdown」（:693-738,1076-1110,715-732 ‹CERTAIN›）。
- **TASK-008 QA 模式管理** — qa 类目显示问答对简表（问题/答案/状态/来源/时间），行操作编辑/确认/删除；删除有 confirm，**编辑保存无错误处理**（:415-448 ‹CERTAIN›；INFERRED-5）。
- **TASK-009 图片 OCR 文本查看** — image 类目左图右 OCR 文本块列表；缩略图 blob 加载，点击新窗预览（:1364-1427,532-552 ‹CERTAIN›；类目无 CRUD/分页 INFERRED-7）。
- **TASK-010 音视频切片联动** — 播放器 + 自绘时间轴（切片按占比定位）；点切片/时间轴 → seek+自动播放；播放中按 currentTime 落区间高亮当前切片（:783-819,1506-1558 ‹CERTAIN›）。
- **TASK-011 来源定位** — 入口携 `query.chunkId`（`{fileId}_chunk_{index}`）→ 解析翻页 → scrollIntoView 居中 + 高亮，展开所属父分片；同页二次跳转经 watch 支持（:285-337 ‹CERTAIN›）。入口：FileManager 行操作、debug 页、SearchTestPanel、应用运行时引用。
- **TASK-012 下载/导出** — 「下载分片」「批量导出」**现状为假成功 stub**（仅 toast 无下载逻辑，:626-628,888-894 ‹CERTAIN›；是否补真实现 INFERRED-2）。

## 3. Pages & Routes

- **PAGE-001 分片管理页** — `/knowledge/:id/chunks/:fileId`（routes.ts:108-113，hidden，title「分片管理」）。入口 ×4：FileManager FileTable 行命令（FileManager.vue:476-486，携 category query）、debug.vue:162,232、SearchTestPanel.vue:279、runtime.vue:211（携 chunkId）。退出：面包屑/返回钮 → 知识库详情。
- **内嵌：文件元数据编辑器** — `FileMetadataEditor`（弹窗内嵌，attrSchema 为空则整节隐藏 :1349）。

页面内按 `processingMode` 四分支：`qa`（问答表）/ `document`（Markdown|Chunks + 元数据侧栏）/ `image`（图 + OCR 列表）/ `video|audio`（播放器 + 时间轴 + 切片表）（:941-1567 ‹CERTAIN›）。

## 4. Elements & Interaction

- 面包屑 + 页头（返回钮、分类图标、文件名、片段计数、新增/重新分片/下载三钮）（:906-937）。
- 分片列表 = **el-table 展开行模式**（非树控件）：单选 `selectedChunk` 作展开开关；父行带箭头图标；子行缩进 `#父.子` 编号；孤立子分片作为普通行混排（:1138-1222,345-372 ‹CERTAIN›）。
- 搜索区：双模式（按ID/按内容）；批量操作条：全选（当前页）+ 已选计数 + 批量删除 + 批量导出（:1030-1069）。
- 元数据侧栏（可折叠）：文件信息 / 分片统计 / 策略信息 / 时间 / 当前选中分片 / 元数据只读摘要（:1241-1360）。
- 弹窗 ×3：编辑分片（70% 宽）/ 新增分片 / 编辑文件元数据（:1571-1674）。
- 音视频：ASR transcripts 传播放器；媒体 blob 加载占位文案（:454-456,1450-1483）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 分片列表 | **无**（现状缺陷，INFERRED-4） | 无专门空态（列表为空即空） | **catch 静默** | :287-301 |
| 编辑/新增提交 | 按钮 loading（editSaving/createSaving） | — | toast；空内容 warning | :563-624 |
| 重新分片 | 按钮 loading + 3s 轮询 | — | failed → toast；轮询错忽略 | :632-674 |
| Markdown | markdownLoading（禁输入） | 404 → el-empty「无 Markdown」 | 非 404 warning | :693-719 |
| QA 列表 | qaLoading + v-loading | 「暂无问答对数据」 | 无专门处理 | :398-410,956 |
| 文件信息 | 无 | 无 | catch 静默；名称兜底 `文件_{fileId 尾 6 位}` | :45-98 |
| 元数据 | 无 | attrSchema 空 → 整节隐藏 | catch 置空 | :104-115 |
| 媒体 blob | 占位文案 | 占位图标 | 静默 | :192-274 |

规则：矩阵即现状契约；「无 loading / 静默吞错」为已记录缺陷（INFERRED-4/5），未经用户裁决不得在本链内擅自"修复"。

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 分片列表 `list/total/parentCount/tokenTotal` | `getChunks` GET `/kb/{kbId}/chunks`（api:331-332；消费 chunks.vue:289-295；解包 `res?.list \|\| res` :290 — 与 DocumentPreviewPanel 的解包假设不一致，INFERRED-6） | TBD — pending API Contract |
| 分片字段 | `mapChunk`（:345-372）：`id/chunkIndex/content/startTime/endTime/chunkType/parentId/imageKeys(兼容 JSON 字符串)/pageNumber/title/headingPath/children`；`tokenCount = content.length`（字符充当 Token，INFERRED-6） | TBD |
| Markdown 读写 | `getKbMarkdown`/`saveKbMarkdown`（PUT `?rechunk=`）（api:339-352；消费 :698,725） | TBD |
| CRUD/批量删除 | `createChunk`/`updateChunk`/`deleteChunk`/`batchDeleteChunks`（body=ids）（api:363-384） | TBD |
| 重新分片 | `reChunkFile`（无 config 三态语义）（api:391-394；消费 :641） | TBD |
| 处理状态轮询 | `getFileProcessingStatus`（api:182；消费 :659） | TBD |
| QA 简表 | `getQaPairs/updateQaPair/confirmQaPair/deleteQaPair`（api:443-461） | TBD |
| 文件信息/元数据 | `getFiles`/`getKnowledgeBaseDetail`/`getFileMetadata` | TBD |

死导出（零消费方，INFERRED-8）：`fetchChunkCount`（GET `/chunks/count`）、`getChunk`——页面统计实际取 getChunks 响应字段。

## 7. Responsive & Accessibility

- 桌面优先，无断点体系（与全站一致 [GAP]）；侧栏可折叠是唯一布局自适应。
- 键盘路径/读屏语义 [GAP: 无约定]。

## 8. Acceptance

- [ ] 按 fileId 进入 → 渲染对应类目分支（qa/document/image/video-audio）
- [ ] 分页翻页 → 列表与 total/parentCount/tokenTotal 刷新（现状：失败静默）
- [ ] 父分片点编辑 → warning 拦截不弹窗；子分片可编辑且保存后重载
- [ ] 新增分片字段随类目变化；指定插入索引 ≤ 总分片数
- [ ] 选中父分片批量删除 → confirm 文案含级联提示；全选仅覆盖当前页
- [ ] 重新分片 → confirm 含绑定一致性提示 → 轮询至 completed → 列表与文件信息刷新；failed → toast
- [ ] Markdown 首次切签懒加载；已编辑未保存时切换不覆盖；「保存并重新分片」→ 保存后自动回 Chunks 并重载；404 → 空态
- [ ] chunkId 入口 → 翻页定位 → 高亮 + 展开父分片；同页二次跳转生效
- [ ] 音视频：点切片/时间轴 seek+播放；播放中当前切片高亮
- [ ] 下载/批量导出 = toast（stub 现状，改动需经 INFERRED-2 裁决）

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 `ChunkManagementPanel.vue` 为 mock 死原型（fixed 覆盖层、全 mock、仅 FileManager 无 kbId 分支可达且该分支生产不可达）——删除 or 接线？INFERRED-2 下载/批量导出 stub 是否补真实现？INFERRED-3 搜索仅当前页是否升级服务端？INFERRED-4 分片列表补 loading/空态/错误处理？INFERRED-5 QA 编辑补错误处理？INFERRED-6 token 口径（字符数）与 getChunks 解包假设不一是否统一？INFERRED-7 image 类目无 CRUD/分页是否有意？INFERRED-8 死导出 `fetchChunkCount`/`getChunk` 清理？
