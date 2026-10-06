# UI Contract — fastrag-frontend

> 由 /idd-contract 维护，按特性分节原地生长（勿 fork 新文件）。API 契约未发布前，消费端以 `TBD — pending API Contract` 标注；字段级映射以 `src/api/index.ts` + 消费代码实况（CERTAIN，引 file:line）为准。
> API Contract Source：后端 springdoc 运行时（`/api → :8081`）。统一响应 `ApiResponse{code,data,message}`；业务错误 = HTTP 200 + body.code；HTTP 401 → request.ts 清态跳 `/login`。

---

# 特性 1：IAM 认证与账号权限（iam-frontend）

## 1. UI Contract

### PAGE-001 — 登录页

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 登录方式切换（账号/微信） | static tab | 恒显〔形态 INFERRED-7〕 | 切换登录面板 |
| 用户名/密码输入 | form model | 恒显 | 回车等效提交 |
| 验证码输入 + 图形 | API（验证码端点）〔[GAP: 端点未核实]〕 | 账号登录面板 | 点击图形刷新 |
| 登录按钮 | static | STATE: 表单通过校验且非提交中 | 提交 → API-AUTH-001；成功 → `/home` |
| 忘记密码/注册链接 | static | 恒显 | 路由 → PAGE-003 / PAGE-002 |
| 预填 admin/admin123 | static（:28-31,252-296） | 初始进入 | — （INFERRED-2：生产应移除） |

### PAGE-005 — 角色管理

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 角色表格 | 角色列表 API ← usePagination | 页面加载 | 分页/刷新 |
| 新增/编辑对话框 | form model | 行操作或工具栏 | 提交 → 角色 CRUD API |
| 删除 | 行操作 + useConfirm | v-permission 通过 | 确认 → 删除 API → 刷新列表 |
| 设默认角色 | 行操作 | 非默认角色行 | 调用 set-default API |
| 权限配置入口 | 行操作 → PAGE-006 | **默认角色行禁用**（roles.vue:53-57,133） | 路由携带角色 id |

### PAGE-006 — 角色权限配置

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 权限树 | MENU_PERM_TREE（前端静态）+ 存量权限 API 回显 | 页面加载 | 勾选/半选；联动组自动补齐（ACTION_API_LINKS） |
| 全选 | 树句柄 | 恒显 | 全选所有键 |
| 保存 | 去重全量键 → 保存 API | 非保存中、非默认角色 | 成功 ElMessage；失败保留勾选态 |
| 禁用遮罩 | 角色是否默认 | 默认角色 | 树只读 |

### PAGE-008 — 人员管理

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 人员表格 | 人员列表 API ← usePagination | 页面加载 | 分页（`personnel__pagination`） |
| 新增/编辑对话框 | form model；角色下拉 ← 角色列表 API | 行操作/工具栏 | 默认选中第 3 项或 id='3'（personnel.vue:150，INFERRED-5） |
| 删除 | 行操作 + useConfirm | v-permission 通过 | 删除 API |

### PAGE-009 — 权限项管理

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 权限表格 + 分类筛选 | 权限列表 API | 页面加载 | 筛选/分页 |
| 新增对话框（分类变形） | form model | 工具栏 | API 类：方法+路径 → 标识自动生成 `api:method:path` 且禁改；页面操作类：「所属页面」仅存表单不提交（:206-284,386-462，INFERRED-6） |
| 编辑对话框 | 行数据回填 | 行操作 | 标识字段禁改 |

### PAGE-007 — 组织管理 / PAGE-002 注册 / PAGE-003 忘记密码 / 内嵌 ApiTokenManager

| Element | Data source | 备注 |
|---|---|---|
| 组织树 | 组织 API | 树形 CRUD；字段映射 [GAP: 未逐行核实，contract 细化项] |
| 注册/重置表单 | form model | 分步结构 [GAP]；提交 → auth API 组 |
| Token 列表/创建/吊销 | api-tokens API | 创建成功一次性明文展示 [GAP: 细节未核实] |

### PAGE-004 — 无权限页
静态插画 + 文案 + 「返回首页」按钮（static，无数据源——页面级 static rationale 成立）。

## 2. State Contract

### 登录查询/提交（PAGE-001，API-AUTH-001）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| initial | 进入页面 | 表单（预填 admin/admin123） | — |
| loading | 提交在途 | 登录按钮 loading + 禁用（防双提交） | — |
| success | body.code=0 | 跳 `/home`（token 已持久化） | — |
| error | 业务错误码 | 表单内 ElMessage 错误〔错误码→文案映射 [GAP: 待后端 ERR 契约]〕 | 输入保留，可重试 |
| disabled | — | — | — |

### 列表查询（PAGE-005/007/008/009 通用）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| initial | 挂载 | 空表格 | — |
| loading | 查询在途 | 表格 loading（v-loading） | — |
| empty | 0 行 | 「暂无数据」 | — |
| success | 200 | 渲染 + 分页 total | — |
| error | 网络/5xx/业务码 | ElMessage 错误，保留旧数据或空表 | 手动重试（重新查询） |
| disabled | 无权限 | 行操作经 v-permission 移除 | — |

### 表单提交（新增/编辑对话框通用）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| submitting | 提交在途 | 确定按钮 loading + 禁用（防双提交） | — |
| success | body.code=0 | 关对话框 + ElMessage + 列表刷新 | — |
| failure | 业务错误码 | 对话框不关，字段错误/消息提示 | **输入全保留**，可改后重提 |

### 权限树加载/保存（PAGE-006）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | 存量权限 + 树数据拉取 | 树 loading | — |
| fallback | 后端树加载失败 | 回退前端 MENU_PERM_TREE 全量（不过滤），可继续编辑 | 保存仍尝试 |
| saving | 保存在途 | 保存按钮 loading | — |
| save-error | 业务错误码 | ElMessage，**勾选态保留** | 重试保存 |

## 3. Data Mapping

### API-AUTH-001 登录 → stores/user.ts（认证模型适配器）

| API field | VM field | Transform location |
|---|---|---|
| `data.token`〔TBD — pending API Contract〕 | `user.token` → localStorage `ais_` 键 | stores/user.ts setToken（:10-11） |
| `data.userInfo`〔TBD〕 | `user.userInfo`（含 roles/permissions 判权字段） | stores/user.ts setUserInfo |
| body.code=401 类 | 清态 + `/login` | utils/request.ts:41-54 |

### 角色权限保存（PAGE-006）

| VM field | API field | Transform location |
|---|---|---|
| 树勾选键集（联动补齐后） | 权限键数组（去重全量） | role-permissions.vue 页内具名映射（:264-327） |
| 存量 `*` / 空 / 部分配置 | 树回显态（全选/默认全选/回显+联动补齐） | 同上（:18-193） |

### 列表行（PAGE-005/007/008/009）

| API row〔TBD — pending API Contract〕 | 表格行 | Transform location |
|---|---|---|
| 原始字段 | 列渲染字段 | 各页页内具名行映射函数（contract 细化时以 api/index.ts 实况回填字段名） |

## 4. API Dependency

| API（消费端命名） | 调用点 | Source | Version consumed | Compatibility window | Mock/fixture |
|---|---|---|---|---|---|
| API-AUTH-001 登录 | POST /api/auth/login | stores/user.ts:42-64 | TBD — pending API Contract | TBD | fixture 待 tdd 阶段建 |
| API-AUTH-00x 注册/微信/用户信息 | auth 组其余端点（api/index.ts auth 组 8 端点） | src/api/index.ts | TBD | TBD | 同上 |
| 角色 CRUD + 权限读写 + set-default | api/index.ts roles 组（6 端点） | src/api/index.ts | TBD | TBD | 同上 |
| 人员 / 组织 / 权限项 | personnel 7 / org 7 / permissions 5 端点 | src/api/index.ts | TBD | TBD | 同上 |
| API Token | api-tokens 端点 | src/api/index.ts | TBD | TBD | 同上 |

兼容策略：后端 springdoc 运行时生成、无版本化契约 → 前端以消费代码为锚；后端字段变更在本文件「Data Mapping」处显式改，不允许组件内静默适配。

## Form Addendum

**登录表单（PAGE-001）**：字段 用户名（必填）/ 密码（必填）/ 验证码（条件必填 [GAP]）；校验 el-form rules 必填性；提交中按钮禁用（防双提交）；失败输入保留；成功路由跳转。
**角色表单（PAGE-005）**：名称必填；编辑回填；提交禁用防双击；失败保留。
**人员表单（PAGE-008）**：用户名/姓名等必填 [GAP: 字段集待逐行核实]；角色下拉默认第 3 项或 id='3'。
**权限项表单（PAGE-009）**：分类决定形态；API 类标识自动生成禁改；页面操作类「所属页面」不提交。
**权限树（PAGE-006）**：非表单但具提交语义——保存 = 去重全量键；联动组保存前补齐；失败保留勾选态。

## Parent Trace

PAGE/STATE/Data Mapping → spec TASK-001..013 / PAGE-001..009；守卫与权限四层件 → design §3 状态所有权 + ADR static-permission-map-default-allow；token 持久化 → ADR token-in-localstorage。

---

# 特性 5：分片管理（chunk-management-frontend）

## 1. UI Contract

### PAGE-001 — 分片管理页（`/knowledge/:id/chunks/:fileId`，四类目分支）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 页头计数「N 个片段」 | 分片列表 API `total` | 恒显 | — |
| 类目分支 | 文件 `processingMode`（getFiles/getKnowledgeBaseDetail） | qa/document/image/video-audio 四选一 | 切换渲染分支 |
| [Markdown\|Chunks] 切换 | static radio（懒加载 markdown，`markdownLastSavedAt` 防覆盖） | document 类目 | 切换视图 |
| 搜索（按ID/按内容） | 页内过滤（**仅当前页**） | document+Chunks | 实时过滤，清空恢复 |
| 批量条（全选/计数/批量删除/批量导出） | 页内 selection | document+Chunks | 删除 confirm（父选级联文案）→ batch-delete API；导出 = stub toast（INFERRED-2） |
| 分片列表（展开行） | getChunks → mapChunk 视图模型 | document/video-audio | 单选行 = 展开父分片 + 侧栏联动 |
| 行编辑 | 行数据回填弹窗 | 子分片/切片；**父分片拦截 warning** | updateChunk → toast + 重载 |
| 行删除（音视频） | confirm | video/audio | deleteChunk → 本地移除 + 重载 |
| [新增分片] | 弹窗（类目条件字段：类型/页码 或 起止时间；插入位置 append/指定索引） | 页头恒显 | createChunk → 回第 1 页重载 |
| [重新分片] | confirm（绑定一致性文案）→ reChunkFile（沿用当前绑定）→ 3s 轮询 | 页头恒显 | completed → 列表+文件信息刷新；failed → toast |
| [下载分片] | **stub**（仅 toast） | 页头恒显 | 无真实下载（INFERRED-2） |
| 元数据侧栏 | getKnowledgeBaseDetail/getFileMetadata/getFiles + 选中分片 | document 类目，可折叠；attrSchema 空则元数据节隐藏 | — |
| QA 表格 | getQaPairs 等 | qa 类目 | 确认/编辑（无错误处理 INFERRED-5）/删除（confirm） |
| 图片 + OCR 列表 | getChunks（imageKeys blob） | image 类目 | 点击新窗预览 |
| 播放器 + 时间轴 | 媒体 blob + ASR transcripts + 切片数据 | video/audio 类目 | 点击切片/时间轴 → seek+播放；播放中区间高亮 |

## 2. State Contract

### 分片列表查询（getChunks）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| initial/成功/翻页 | 加载/翻页 | 列表 + 统计刷新（**无 loading 态**，INFERRED-4） | — |
| error | 请求失败 | **静默（catch 吞错）**（INFERRED-4） | 手动刷新 |

### 重新分片（reChunkFile + 轮询）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| confirm | 点按钮 | 弹窗（删除旧分片与向量 + 策略改动需重切提示） | 取消即退出 |
| running | 提交成功 | 按钮 loading + 3s 轮询处理状态 | completed → 刷新；failed → error toast；轮询错忽略继续 |
| 卸载 | 组件销毁 | — | onBeforeUnmount 清定时器 |

### Markdown（getKbMarkdown/saveKbMarkdown）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | 首次切 Markdown 签 | markdownLoading（禁输入与按钮） | — |
| empty | 404 | el-empty「无 Markdown」 | — |
| editing | 用户输入 | 编辑器可写；切签不覆盖已编辑内容（markdownLastSavedAt 守卫） | — |
| saving | 保存（可勾「保存并重新分片」） | 按钮 loading | 成功 toast；（勾选时）切回 Chunks + 重载；失败 toast 内容保留 |

### 提交类（编辑/新增/QA 编辑）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| submitting | 提交在途 | 按钮 loading（editSaving/createSaving） | 防双提交 |
| error-验证 | 空内容 | warning toast | 输入保留 |
| error-接口 | 业务错误 | error toast；弹窗停留 | 输入保留可重试（**QA 编辑例外：无 try/catch**，INFERRED-5） |

## 3. Data Mapping

### getChunks → ViewModel（transform location：chunks.vue `mapChunk` :345-372）

| API field | VM field | Transform location |
|---|---|---|
| `list[]` | 分片行（`res?.list || res` 兼容解包 :290；与 DocumentPreviewPanel 解包假设不一致 → INFERRED-6） | chunks.vue 加载函数 |
| `chunk.content.length` | `tokenCount`（字符充当 Token，INFERRED-6） | mapChunk |
| `chunk.imageKeys`（string 或 array） | `string[]`（JSON.parse 兼容） | mapChunk |
| `chunk.chunkType/parentId/children` | 父子展开行结构（孤立子分片混排为普通行） | mapChunk |
| `total/parentCount/tokenTotal` | 侧栏统计 | :291-295 |

### 入口定位契约（URL → 页面行为）

| 入口 query | 行为 | Transform location |
|---|---|---|
| `?chunkId={fileId}_chunk_{index}` | 正则解析 index → 换算目标页 → 翻页 → 展开父分片 → `[data-chunk-id]` scrollIntoView + `--highlight`；watch 支持同页复跳 | chunks.vue :285-337 |
| `?category=` | 初始类目（FileManager 携带） | 路由 query |

## 4. API Dependency

| API（消费端命名） | 调用点 | Source | Version consumed | Mock/fixture |
|---|---|---|---|---|
| 分片列表 | GET `/kb/{kbId}/chunks` | api/index.ts:331-332 | TBD — pending API Contract | 待 tdd 建fixture |
| Markdown 读写 | GET/PUT `/kb/{kbId}/files/{fileId}/markdown(?rechunk=)` | api:339-352 | TBD | 同上 |
| 分片 CRUD/批量 | POST/PUT/DELETE `/kb/{kbId}/chunks*`、`/batch-delete` | api:363-384 | TBD | 同上 |
| 重新分片 | POST `/kb/{kbId}/files/{fileId}/re-chunk` | api:391-394 | TBD | 同上 |
| 处理状态 | GET 处理状态端点 | api:182 | TBD | 同上 |
| QA 四操作 | `/kb/{kbId}/qa-pairs*` | api:443-461 | TBD | 同上 |
| ~~chunks/count、getChunk~~ | **零消费死导出**（INFERRED-8） | api:355-360 | — | — |

兼容策略：字段变更在 Data Mapping 显式改（mapChunk 为唯一归一点）；后端增删字段必须同步 mapChunk 与本表。

## Form Addendum

**编辑分片弹窗**：内容 textarea 必填（空 → warning）；Token/索引字段展示；提交中按钮 loading；失败弹窗停留输入保留。
**新增分片弹窗**：类目条件字段（document：类型 text/image + image 页码；audio/video：起止时间）；插入位置 append 或索引之后（max=totalChunks）；空内容 warning。
**QA 编辑**：行内/弹窗编辑保存——现状无错误处理（INFERRED-5，修复合意性待裁决）。
**元数据编辑**：FileMetadataEditor；attrSchema 驱动字段；空 schema 隐藏入口。

## Parent Trace

PAGE/状态/映射 → spec TASK-001..012；重新分片绑定一致性 → design §6 + parse-strategy 链 ADR-0001（绑定变更必须重切）；Panel 死原型 → spec INFERRED-1。

---

# 特性 2：知识库管理（kb-core-frontend）

## 1. UI Contract

### PAGE-001 — 知识库列表

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| Tabs 全部/我的 | 页内 tab | 恒显 | 「我的」按 creator===userId 过滤并重置页码 |
| 分类栏（色点+badge） | getKbCategories | 恒显 | 单选 toggle 过滤（点击已选取消）；侧栏搜索框实际过滤 KB 名（INFERRED-5） |
| 标签多选面板 | 列表内 tags 汇总 | 标签按钮点开 | 任一命中即纳入（some 语义） |
| KB 卡片 | getKnowledgeBases | 分页切片（前端 12/页） | 整卡点击 → `/knowledge/:id` |
| 类型徽标 | `permission`（private→个人，否则团队） | 每卡 | — |
| [创建知识库] | static | `kb:create` | → PAGE-003 |
| 分页 | usePagination(12) | filtered.length > pageSize | 前端切片翻页（服务端参数闲置，INFERRED-1） |

### PAGE-003/004 — 创建/编辑（共享表单体）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 四 Tab 表单 | KnowledgeBaseForm model | 恒显 | 提交 → POST/PUT `/kb` |
| 分类下拉 + [+] | getKbCategories | 基本信息页签 | + 快捷新建 → 自动选中（失败静默） |
| 标签输入 | el-autocomplete ← getAllKbTags | 同上 | closable tags |
| 共享三卡 | form.shareType | 共享设置页签 | 互斥；部门/指定人 popover 互开即关对方；默认部门/自己不可移除 |
| 部门成员 | getDepartmentMembers（按需拉取） | department 卡 | 去重排除自己 → viewer |
| 指定人 | getPersonnelSimple | specified 卡 | 每人角色 editor/viewer；自己固定所有者 |
| 嵌入模型 | getModels?purpose=EMBEDDING | 高级配置；**edit 禁用 + tooltip** | — |
| 解析策略 | edit→跳转链接；create→模板下拉（fetchParseStrategyTemplates） | 高级配置 | edit 无 id → warning「请先保存知识库」 |
| 图谱开关 + LLM | form | 高级配置；LLM 仅开关开时显示 | — |
| 元数据字段编辑器 | form.customAttrSchema | 高级配置 | select 才显选项；region 强制 searchable；空 name 过滤 |
| 检索设置 | RetrievalSettingPanel | 检索设置页签 | 归 retrieval 链 |
| 侧栏 | computed | create→进度 4 步（名称/分类/简介/embeddingModelTouched）；edit→元数据 + 危险区（owner/超管 + kb:delete） | 删除 confirm「无法恢复」 |
| [创建/保存] | form | 底部恒显 | loading；成功 toast 回列表；失败 toast 留页 |

**ACL 提交语义（契约核心）**：仅 edit 调 setKbAcl；构造 = 自己 owner 必含 + global 追加 `userId:'*'` viewer + department 逐部门成员 viewer + specified 按人角色；**回填** = `'*'` → global、非自己条目 → specified（无 department 分支，INFERRED-3）。

### PAGE-002 — 分类管理

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 分类表格 | getKbCategories | v-loading | 行编辑/删除（`kb:category:edit/delete`，删除 confirm） |
| [新增分类] | dialog | `kb:category:create` | 名称必填；组织（超管 tree-select 必选，普通用户锁本组织）；颜色 8 预设；排序 0-999 |

### PAGE-005 — 首页

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 快捷入口 4 卡 | static + 权限过滤 | 有对应权限 | 路由跳转 |
| 推荐知识库 | getHomeData.recommendKBs | 有数据，空则 el-empty | 点击 → 详情 |
| 热门文档/应用/动态 | getHomeData | 各自 el-empty | — |

## 2. State Contract

### KB 列表（getKnowledgeBases）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | 挂载 | 页面 v-loading | — |
| empty | 过滤后 0 卡 | el-empty | 调整筛选 |
| error | 请求失败 | **无 catch**——依赖 request.ts 拦截器 toast；页面保持空 | 手动刷新 |

### 表单提交（创建/编辑/删除）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| submitting | 提交中 | 按钮 loading 防双提交 | — |
| success | 成功 | toast → 回列表 | — |
| failure | 失败 | toast「创建/保存失败」，**留页输入保留** | 修改后重提 |
| 初始数据失败 | Promise.all reject | **console.error 静默**（表单空白，INFERRED 缺陷记录） | — |

### 分类 CRUD / 首页聚合

同列表模式：分类保存失败显式 toast「操作失败」；首页 catch 保持空数据 + 各区块 el-empty。

## 3. Data Mapping

### form 表单模型 ↔ API（transform location：form.vue 具名点）

| VM | API field | Transform location |
|---|---|---|
| `shareType` | `permission`（global→public / department→team / specified→private） | form.vue:511-518 |
| 共享编辑集 | ACL 数组 `{userId,kbRole}`（自己 owner 必含；`'*'`=global；部门成员拉取去重） | form.vue:520-565（唯一写入点） |
| ACL 响应 | shareType + 指定人列表（人名反查 personnel） | form.vue:321-349（唯一回填点） |
| 元数据字段行 | `customAttrSchema[]`（options 逗号拆数组；region 强制 searchable） | form.vue:571-584 |
| KB 卡片 | name/description/category/tags/permission/embeddingModel/dimension/creator(Name)/usedSize | index.vue:63,190-207 |
| 首页聚合 | recommendKBs/hotDocs/hotApps/recentActivities | home/index.vue:32-35 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| KB 列表/详情/CRUD | GET `/kb`、GET/POST/PUT/DELETE `/kb(/:id)` | api:74-126 | TBD — pending API Contract | 待 tdd |
| 分类 | GET/POST/PUT/DELETE `/kb-categories` | api:81-92 | TBD | 同上 |
| ACL | GET/PUT `/kb/:kbId/acl` | api:1351-1357 | TBD | 同上 |
| 支撑 | models/personnel/simple、org/tree、org/:id/members、kb-tags、parse-strategy-templates | api 各处 | TBD | 同上 |
| 首页 | GET `/home` | api:11-13 | TBD | 同上 |
| ~~getKnowledgeBaseCategories~~ | **零消费死导出**（INFERRED-6） | api:78-80 | — | — |

## Form Addendum

- 必填：名称（rules:353）；分类必选（超管组织必选）。
- 失败保留输入；提交中禁双击；编辑模式嵌入模型锁定。
- 元数据字段行：类型 text|number|select|date|boolean|region；select 必填 options（逗号/中文逗号拆分）。

## Parent Trace

→ spec TASK-001..008 / PAGE-001..005；ACL 语义 → design §4 具名点；workspace 隔离 → spec INFERRED-4。

---

# 特性 3：解析策略（parse-strategy-frontend）

## 1. UI Contract

### PAGE-001 — 策略管理页

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 策略表格 | fetchStrategies（全量，v-loading） | 恒显 | 名称客户端过滤；无分页（INFERRED 缺陷候选） |
| 默认 tag / 覆盖链 tooltip | `isDefault` / static 文案 | 恒显 | — |
| [创建策略]/[编辑] | el-dialog 表单 | 恒显/行操作 | 提交 → POST/PUT → toast + 刷新（失败无兜底 INFERRED-2） |
| [设默认]/[删除] | 行操作 | **仅非默认行** | 设默认 confirm；删除 confirm（默认禁止；fileCount 影响提示 + 自动匹配回退文案） |
| 冲突内联警告 | detectConflicts（防抖 300ms） | 扩展名与他策略冲突 | 提交前二次强确认（返回修改/仍然保存） |
| 文档类型▾ | meta.methods | 弹窗 | 切换重置扩展名勾选；default 可选全部 |
| 分片策略 5 卡片 | CHUNK_STRATEGY_OPTIONS | 弹窗 | 切换重置 chunk 参数为 DEFAULT |
| 五类参数面板 | form.adv（Proxy 绑定） | 随策略渲染 | 字段族见 spec TASK-002 |
| 高级折叠 | LLM 模型/表格模式/视频关键帧/图片提示 | 条件显隐（TABLE_METHODS/KEYFRAME_METHODS） | [恢复默认] → DEFAULT_ADVANCED |
| 刷新↻ | load() | 恒显 | **无条件「列表已刷新」toast（假成功，INFERRED-3）** |

### PAGE-002 — StrategyChangeDialog（换绑重切）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 常驻 el-alert | static 重切告知文案 | 恒显 | — |
| 策略卡片+参数面板 | 基线 = 文件当前生效 advanced.chunk 合并 | 非 QA、非处理中 | 修改 |
| [保存并重新分片] | saveFileStrategy（parseMethod='default'，file_id 隐藏专属） | 非 QA 文件且非处理中 | 提交 → FileManager load()+轮询该文件 |
| QA 文件提示 | static | processingMode=qa | 「不受分片策略影响」保存禁用 |
| 处理中锁死 | 文件状态 | processing | 「正在处理中…」 |

### PAGE-003 — 上传向导策略步骤（FileUploader 宿主）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 策略下拉 | fetchStrategies + 首项「自动匹配」value='' | step2 | 空串不转发（语义=自动匹配）；默认策略名带「（默认）」 |
| 分块预览 | previewFileChunks?strategyId | step3，已上传文件 | per-file 三态（loading/错误+重载/未上传空态/分块列表） |
| 分块参数提示 | static alert | step2 | 指向「解析策略管理」（参数不在上传时配） |

## 2. State Contract

### 策略列表（fetchStrategies）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | 加载 | v-loading | — |
| success | 200 | 渲染 | — |
| empty | 0 条 | **无专门空态**（INFERRED 缺陷候选） | — |
| error | 失败 | **无 catch，静默空列表**（INFERRED-2） | 手动刷新 |

### 表单提交（创建/更新）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 验证失败 | rules | 字段错误 | — |
| conflict | 提交前复检命中 | 强确认弹窗 | 返回修改 / 仍然保存 |
| submitting | 提交中 | 按钮 loading | 防双提交 |
| failure | API 抛错 | **无 catch：弹窗停留、无提示**（INFERRED-2） | — |
| success | 成功 | toast + 关弹窗 + 刷新 | — |

### 换绑保存（saveFileStrategy）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| submitting | 保存中 | 按钮 loading+disabled | 防双提交 |
| failure | 失败 | `e?.message\|\|'保存失败'` | 保留输入 |
| success | 成功 | 「已保存分片策略并提交重新分片任务」→ 关闭 → 列表刷新+轮询 | — |

## 3. Data Mapping

### 表单模型 ↔ advanced（transform location：parse-strategy.vue 具名点）

| VM | API field | Transform location |
|---|---|---|
| 表单顶层 chunk 参数 | `advanced.chunk.*` | `adv` Proxy（:150-170，唯一双向绑定点） |
| 旧平铺格式响应 | 新 `{parse,chunk,index}` 结构 | `normalizeAdvanced`（:178-208，唯一归一点） |
| 策略卡片切换 | chunk 参数重置 DEFAULT_ADVANCED | `resetChunkParams`（:229-242） |
| 提交载荷 | 剥离 `advanced.index`（后端兜底） | `buildPayload`（:351-360） |
| 换绑表单 | 当前生效 chunk 基线合并 + `parseMethod:'default'` + `strategyType:'file_id'` 隐藏专属 | StrategyChangeDialog 提交函数（:141-162） |
| 类型/扩展名联动 | methods[].extensions / supportedExtensions | `handleMethodChange`（:99-108） |
| 预览响应 | previewChunks[].{title,content,index}、totalChunks、pages | FileUploader（:477-486） |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| 策略 CRUD+设默认 | GET/POST/PUT/DELETE `/kb/{kbId}/parse-strategies`、`/{id}/set-default` | api:491-513 | TBD — pending API Contract | 待 tdd |
| 冲突检测 | POST `…/conflicts` | api:519-525 | TBD | 同上 |
| 元数据 | GET `/parse-strategies/meta` | api:487-489 | TBD | 同上 |
| 文件换绑 | POST `/kb/{kbId}/files/{fileId}/strategy` | api:205-218 | TBD | 同上 |
| 分块预览 | GET `…/preview?strategyId=` | api:186-199 | TBD | 同上 |
| 模型 | GET `/models?purpose=LLM|EMBEDDING` | api:1088-1090 | TBD | 同上 |
| ~~detail()、resolve、templates、customConfig~~ | **零消费**（resolve 仅 FileManager:89 死解构；templates 归 kb-core 创建页；INFERRED-7/8） | api:495-517,482-484 | — | — |

## Form Addendum

- 必填：策略名称（≤50）；扩展名非空；描述 ≤200。
- advanced 默认：chunkLength=1000/overlap=100/delimiters=['\n\n']/semanticThreshold=30/parentMaxChunkLength=2000；重叠 0-500 步进 10；父片最大 500-10000 步进 500。
- ChunkLengthSelect：自定义 0 值 → 输入框，`Number(value)||1000` 兜底。
- 失败保留输入；策略卡片切换重置参数（脏参数防护）。

## Parent Trace

→ spec TASK-001..007；换绑语义 → design PAGE-002 + ADR-0001（绑定变更必须重切）；覆盖链 → spec TASK-001。

---

# 特性 4：文件上传与摄取流水线（file-ingestion-frontend）

## 1. UI Contract

### 文件管理面板（宿主 detail Tab，无独立路由）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| [上传] | FileUploader 向导 | `kb:upload` | 打开 4 步向导 |
| [回收站] | getDeletedFiles（打开时拉取） | 恒显 | 对话框：恢复/彻底删除/清空（各二次确认） |
| 面包屑 | folders 树回溯 | 恒显（根「全部文件」） | 点击跳层 |
| [新建文件夹] | dialog（≤50 字符） | 恒显 | root 时 folderId=null |
| 批量操作条 | selectedFiles | **选中>0 才渲染** | 批量移动/打标（增+移）/删除/导出（逐个下载） |
| 工具栏筛选 | 页内过滤 | 恒显 | 文件名/类型 4/状态 4/全部-文件夹-文件；刷新 |
| 文件表格 | getFiles + folders（前端切片分页 10/20/50/100） | v-loading | 排序 4 列（仅当前页有效 INFERRED-1）；文件夹行禁选 |
| 状态列 | ProcessStatusBar | 恒显 | pending/processing（进度+类目阶段步骤条）/completed/failed（失败于：stage） |
| 策略 tag | 文件策略信息 | QA 文件不可点 | 点击 → 换绑对话框（parse-strategy 链） |
| 图谱开关 | el-switch 1/0 | 恒显 | 开 buildGraphIndex('full')/关 deleteFileGraph（knowledge-graph 链） |
| 操作下拉 17 项 | 条件显隐矩阵 | 见 spec §4 | 预览/AI分片/分片管理/下载/重试/重命名/复制/元数据/移动/删除/在线编辑… |

### 上传向导（FileUploader）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| step1 文件列表 | 页内队列（即选即传 + onUploadProgress 进度） | 恒显 | 同名冲突 → 确认跳过；单文件失败标 error |
| step2 引擎区 | config | hasImages→OCR；hasAudio‖hasVideo→ASR；hasVideo→视频策略+关键帧 | TXT/CSV 才显编码；时间裁剪互相 clamp；优先级/QA 方式禁用（记录不生效） |
| step3 分块预览 | previewFileChunks?strategyId | 已上传文件 | per-file 三态 + 重载 |
| step4 汇总+确认 | configSummary（视频策略重复两项——已知 UI 瑕疵） | 固定 2s 后可确认 | 逐文件 processFile（fire-and-forget）→ toast「已提交 N 个处理任务」→ 轮询启动 |

### 预览对话框（FilePreviewDialog 六分支）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 分支渲染 | blob（认证 fetch→匿名重试→objectURL，关闭 revoke） | 按扩展名分流 | pdf.js/图片/office(mammoth/XLSX/pptx-preview)/文本(**getChunks 分片拼接**，Markdown\|Chunks 双视图)/音视频/未知→下载兜底 |
| [使用 OnlyOffice 打开] | 路由 | 仅 office | `/knowledge/:id/office/:fileId` |
| 预览失败 | — | — | el-empty + 下载兜底 |

## 2. State Contract

### 文件列表查询（getFiles，3s 轮询共用）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | load 在途 | v-loading | — |
| success | 200 | 渲染（容忍 list‖records‖数组三形态） | — |
| error | 失败 | **无 catch**——拦截器统一 toast | 手动刷新 |
| polling | 有 processing 文件 | 3s 全量重拉；全部离开 processing 自停 | 卸载清理 |

### 上传单文件

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| uploading | 即选即传 | el-progress | — |
| error | 上传失败 | error 态 + 行内「重试」 | 重试重新上传 |
| conflict | 同名 | MessageBox「将跳过这些文件」 | 确认跳过 |

### 处理提交（processFile）/ 重试 / 删除等

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 提交 | 确认上传/重试 | toast（「已提交 N 个任务」；重试为**乐观 toast** INFERRED-6） | 失败靠拦截器提示 + 行内重试 |
| 删除 | 行/批量删除 | 乐观 toast → 回收站可恢复 | 彻底删除/清空需二次确认 |

## 3. Data Mapping

| VM | API field | Transform location |
|---|---|---|
| 文件行 | `list‖records‖数组` 兼容 | useFiles.load（UF:27）/HOST:59 |
| 上传文件标识 | `id‖fileId` 双字段 | FileUploader（FU:285-292） |
| 预览 | `previewChunks[].{title,content,index}`、`totalChunks`、`pages` | FileUploader（:477-486） |
| 文本预览视图模型 | getChunks 按 chunkIndex 排序拼 markdown | DocumentPreviewPanel（:71-88，唯一拼接点） |
| 元数据 | `attrSchema[]` + `values`（schema 驱动渲染，必填校验） | FileMetadataEditor（:32-98） |
| 文件夹层级 | folders 树 → folderRows + 面包屑回溯 | FM:191-247 |
| 上传配置 | config.processConfig（含 parseStrategyId 空串不转发；mediaConfig.timeRanges） | FM:111-137 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| 列表/文件夹组 | getFiles、fetchFolders、folder CRUD | api:132-134,305-321 | TBD — pending API Contract | 待 tdd |
| 上传/处理 | **裸 request.post**（FU:268-271）+ processFile | api:140-148 | TBD | 同上 |
| 状态变更 | updateFile（重命名/移动/批量/图谱共用）、retryFile | api:150-156 | TBD | 同上 |
| 回收站 | delete/restore/permanent/empty/deleted | api:136-172 | TBD | 同上 |
| 移动/复制/下载 | moveFileToKb、copyFile、downloadFile(blob) | api:174-222 | TBD | 同上 |
| 预览/分块 | previewFileChunks、getChunks（文本预览用） | api:186-199,331 | TBD | 同上 |
| 元数据/打标 | get/updateFileMetadata、batchSetFileTags | api:268-294 | TBD | 同上 |
| ~~uploadFile、fetchFolderName、setFileTags、extractFileMetadata~~ | **零 UI 消费死路径**（INFERRED-8） | api:140-144,284-299,323-325 | — | — |

## Form Addendum

- 上传配置：无必填项（默认值全可提交）；时间裁剪 start<end 互相 clamp；编码仅 TXT/CSV。
- 新建/重命名文件夹：名称必填 ≤50；删除要求空文件夹。
- 重命名文件：去扩展名编辑，保存时 append 原后缀。
- 元数据：schema 驱动；必填缺失阻止保存（FME:87-91）。
- 双提交防护：确认上传/保存类按钮 loading；删除/重试等为乐观 toast（INFERRED-6 悬置）。

## Parent Trace

→ spec TASK-001..009；跨链入口矩阵 → design §1；轮询/阶段条 → spec TASK-003；死路径 → INFERRED-8。

---

# 特性 6：OnlyOffice 在线编辑（onlyoffice-editing-frontend）

## 1. UI Contract

### PAGE-001 — 在线编辑页

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 页头状态文本 | modified ref | 恒显 | 「● 有修改（未自动保存…）」+ 黄条 is-modified |
| [保存] | forcesave API | 非 saving | result 五分支提示矩阵（见状态契约）；initiated → 轮询 updatedAt → 「已保存…已自动触发重新切片」（8s 去重） |
| [重新加载] | destroy+重载 | 恒显 | 重跑 loadEditor |
| 编辑器容器 | DocsAPI.DocEditor（config 接口下发，前端仅覆盖 customization/events） | 加载遮罩后 | onAppReady → 进度 100 |

### PAGE-002 — 内嵌查看器（AiChunkPanel 宿主）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| DocsAPI 容器 | config（user.id=origin 前缀；**不注入 plugins；不强制 view**——契约级约束） | 宿主弹窗内 | 精简 UI 编辑构建 |
| 选区捕获 | 插件桥（ping 存活/oo-selection 文本） | 编辑构建运行中 | 防抖 800ms → captured → 宿主建分片 |
| 桥状态指示 | warming→alive→down 状态机 | down 时 | 宿主 warning 恢复指引 |
| chunk 跳页 | props.focusChunkId | chunk 卡点击 | scrollToPage(pageNumber??1) |

## 2. State Contract

### 编辑器加载（getOnlyOfficeConfig + api.js）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | 进入页面 | 遮罩 + 合成进度（config=50） | finally 兜底不卡死 |
| ready | onAppReady | 进度 100、可编辑 | — |
| error | config/api.js 失败 | Notification「OnlyOffice 加载失败」 | [重新加载] destroy 重跑 |
| 超时 | onAppReady 永不触发 | 状态文本停「初始化中…」 | **无看门狗 [GAP]**（INFERRED-7） |

### 手动保存（forcesave）

| STATE (result) | Visible feedback | Recovery |
|---|---|---|
| initiated | 保存钮 loading → 2s×10 轮询 updatedAt → 「文档已保存…已自动触发重新切片」 | 20s 未确认 → warning；**基线 null → 必然超时（INFERRED-1）** |
| no-changes | info | — |
| no-session | warning「请重新加载」 | 重新加载 |
| no-file/disabled/其它 | error（附 ooError 码） | — |

### 选区桥（插件侧）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| warming | 启动 60s 宽限 | 无报警 | 收到 ping → alive |
| alive | ping ≤20s 间隔 | 正常捕获 | — |
| down | >20s 无 ping | 宿主 warning「插件选项卡重开 ChunkBoundary 或点重置」（INFERRED-3：仅 visual 版可操作） | 恢复信号 → 自动回 alive |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| config 整包（token/callbackUrl/document.url 等由后端下发） | DocsAPI.DocEditor 入参 | office-edit/Viewer 页内具名覆盖函数（edit：customization.autosave=false/forcesave=true + events；viewer：user.id=origin 前缀 + 精简 UI） |
| forcesave `result`（initiated/no-changes/no-session/no-file/disabled/failed）+ `ooError` | 提示分支 | office-edit.vue:168-183 |
| files[].updatedAt | 保存基线/轮询比较 | :146-194 |
| 插件消息 `oo-selection{text≥2}`/`oo-selection-ping` | captured/桥存活 | OnlyOfficeViewer:213-277 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| config | GET `…/onlyoffice/config` | api:408-410 | TBD — pending API Contract | 待 tdd |
| forcesave | POST `…/onlyoffice/forcesave` | api:418-423 | TBD | 同上 |
| 文件列表（基线） | GET `/kb/{kbId}/files` | api:132-134 | TBD | 同上 |
| ~~raw、callback~~ | **前端零运行时消费**（config 下发；仅探针页拼 URL） | — | — | — |

## Form Addendum

无表单；双提交防护=保存钮 saving loading。探针脚本硬编码 dev secret/浏览器路径（INFERRED-2）。

## Parent Trace

→ spec TASK-001..010；Viewer 两条契约级覆盖（不注入 plugins/不强制 view）→ design §4 + 探针差分结论；保存感知 → spec TASK-003/006。

---

# 特性 7：AI 分片（ai-chunking-frontend）

## 1. UI Contract

### AiChunkPanel（弹窗，FileManager 宿主）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| Header tags + 进度条 | 分片数/dirtyCount/含手动 + status | 恒显 | 阶段文本随 SSE stage |
| 原件预览区 | file.url（Bearer blob）→ Overlay/Viewer/empty | 按类型分支 | 承载选区与联动 |
| [生成分片] | 四类选区 → imageOcr | 有选区（无则 warning）；ocrLoading | 合并一张手动卡 + 选区清空 |
| [一键自动分片] | stream SSE → 失败回退 preview | loadingLeft 禁用；已有分片先 confirm | done 后卡片入列 + 降级来源提示 |
| 批量操作条 | 勾选集 | 有卡片 | 合并/删除/全选/反选 |
| 分片卡（浏览/编辑态） | 卡模型 | 恒显 | 编辑/拆分热区(>400字)/合并/删除/上移下移/复制；hover/click → 联动高亮 |
| [撤销] | 撤销栈 ≤20 | 栈空 info | 恢复快照 |
| [应用 AI分片] | aiChunkApply | 空拦截 warning；dirty>0 confirm | 成功关窗 + applied → 列表刷新 |
| [重置]/[取消] | initPanel | parsing/applying/loadingLeft 中禁用 | 全量重置/关闭 |

### AiChunkPdfOverlay（左栏 PDF 分支）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 结构块框 | layoutBlocks | pointer-events:none；几何命中驱动 | 点击/拖拽命中 → toggle-select |
| 图片盒（绿） | imageBoxes | 恒显（z 压文字层） | 点击选中 → 生成分片走 imageOcr |
| 区域拖拽 | mousedown 固化各页 rect | 拖拽 ≥4px | 与每页求交 ≥1%×1% → 逐页 region-select；边缘 56px 自动翻页 |
| 文字拖选 | window.getSelection ≥2 字 | 未命中块时 | text-select |
| 联动高亮 | activePdfIds | 联动激活 | toggle is-selected/is-active（不重建 DOM） |
| 扫描件探测 | probeTextLayer（<50 字符/页） | 面板打开时 | 过半 → 延迟 OCR 提示 |

## 2. State Contract

### 解析（aiChunkPreview + layout SSE）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loadingLeft | Office 即解析/PDF 渲染中 | 状态文本 | — |
| parsing | preview 在途 | 1s ticker + 进度条 | 失败 → error + 清模型回 idle（仍可自动分片） |
| done | 解析完成 | 提示区切换 | — |

### 自动分片（stream SSE + 回退）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| confirming | 已有分片 | confirm「清空现有（含手动）」 | 取消退出 |
| streaming | SSE 在途 | progress{percent,stage} 阶段文案 | — |
| done | done{chunkSource} | 卡片入列 + 「完成（来源：LLM/向量/规则）」显式告知 | — |
| sse-error | 失败/流提前结束 | warning「SSE 不可用，回退一次性预览」 | preview 一次性返回（假进度渐进出卡，INFERRED-5）；**再失败卡 parsing 无兜底（INFERRED-1）** |

### 应用（aiChunkApply）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 拦截 | 0 卡 | warning | — |
| confirm | dirtyCount>0 | 「修改 N 处，替换旧数据」 | 取消保留 |
| applying | 提交中 | 按钮 loading | 失败 toast、status 回 done |
| applied | 成功 | 「已应用并落库」→ 关窗 | emit applied → FileManager.load() |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| preview 响应 `alignParagraphs/imageBoxes/layoutBlocks` | 段落对齐模型（paragraphId 统一锚点） | Panel initPanel 消费（types AiChunkParagraph:683-718） |
| SSE `chunk{text,paragraphIds}` | 分片卡（**与 AiChunk 类型声明不匹配** INFERRED-4） | Panel:990-1034 |
| layout SSE `layout[]` | layoutBlocks（键 `page#x|y|w|h` 去重整表替换） | Panel:677-683 |
| imageOcr `{blocks:[{type,text}]}` | 手动卡块 + imageKey 直渲 | Panel:166-187,805-850 |
| apply payload `{chunks:[{paragraphIds,text('\n\n' 拼接),source,pageRange(未声明 INFERRED-3)}]}` | — | Panel:1169-1175 |
| Office captured 文本 | 跨段匹配 → parasToMarkdown（表格/标题/列表/代码重建） | Panel:195-234,242-291 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| preview | POST `…/ai-chunk-preview?chunk=`（timeout 300s） | api:229-236 | TBD — pending API Contract | 待 tdd |
| apply | POST `…/ai-chunk-apply`（300s） | api:239-245 | TBD | 同上 |
| stream SSE | GET `/api/kb/{kb}/files/{f}/ai-chunk/stream`（fetch 硬编码 /api） | api:249-253 | TBD | 同上 |
| layout SSE | GET `…/ai-chunk/layout` | api:255-261 | TBD | 同上 |
| imageOcr | POST `…/ai-chunk/image-ocr` | api:425-437 | TBD | 同上 |
| 图片 | GET `…/images/{imageKey}`（Bearer blob） | Panel:524-529 | TBD | 同上 |

mock 层零 ai-chunk 模块（全真实后端）。

## Form Addendum

编辑态 textarea 非空校验（空 → warning）；快照撤销 ≤20 步；双提交防护 = status 机（parsing/applying 禁用相应按钮）。

## Parent Trace

→ spec TASK-001..009；SSE 双流回退链 → design §4；覆盖语义 → spec TASK-007（契约与 api:238 注释一致）。

---

# 特性 9：检索与查询增强（retrieval-frontend）

## 1. UI Contract

### PAGE-001 — 检索测试（SearchTestPanel + RetrievalSidebar）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 图片预览条 | 页内队列（≤5、10MB、去重） | 有图 | 有图自动切 image 模式（占位语义 INFERRED-5） |
| 输入框 + 搜索钮 | form | 恒显 | Enter（非 Shift）/点击 → 预处理管线 → searchRetrieval；空输入 warning |
| 纠错/重写提示条 | autoCorrect/rewriteQuery（本地 mock 委托） | 命中/开关开 | 展示 suggestedQuery/rules tags |
| 图谱扩展提示 | useGraphExpansion（Map 缓存） | 开关开（默认关） | isExpanding 提示条；无 catch 冒泡 |
| 同义词提示 | useSynonyms（catch 静默回退） | 开关开（默认开） | matchedTerms→addedTerms（同数组 INFERRED-8） |
| 历史面板 | localStorage `search-history:${kbId}` ≤50 | 恒显 | 点击回填+自动重搜；清空 success |
| 结果卡片 | SearchResultItem[] | hasSearched | 相似度 toFixed(2)%；graph/qa tag；v-html+mark 高亮；`<details>` 全文；溯源链接 |
| Sidebar 字段族 | config（RetrievalConfig 共享类型） | 320px sticky | 即改即生效（深度 watch 自动重搜，已搜索过）；[保存] → updateKnowledgeBase |

### PAGE-002 — 检索设置（RetrievalSettingPanel，六段受控）

六段字段族见 spec TASK-005；Rerank 模型列表加载失败回退硬编码 `bge-reranker-v2-m3/base`；无保存钮（随表单提交）。

### PAGE-003 — 检索日志分析

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 5 指标卡 | GET logs/analysis（`??0` 兜底） | 恒显 | — |
| Top10 两面板 | analysis.topQueries/noResultQueries | 空则 el-empty | 纯列表（非图表库） |
| 明细表 | GET `/retrieval/logs`（kbId/hasResult/page） | v-loading | KB/状态筛选 + 查询/重置 + 分页 10-100；已删 KB 显「已删除（kbId）」 |

## 2. State Contract

### 检索查询（searchRetrieval）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 拦截 | 空输入 | warning「请输入检索内容或上传图片」 | — |
| loading | 在途 | 按钮 loading + 结果区文案（区分图片/文本） | — |
| success | 200 | 卡片渲染 + 写历史（纠错/重写后的中间 query 入史） | — |
| empty | 0 结果 | el-empty「未找到相关文档块」 | 调整参数 |
| error | 失败 | 「检索失败，请重试」+ 清空结果 | 重试；无超时处理 |

### 配置保存（Sidebar）

submitting（保存钮 loading）→ success toast / failure「检索配置保存失败」。

### 分析页（logs/analysis）

logs：v-loading + **try/finally 无 catch（unhandled rejection，INFERRED-7）**；analysis/KB 选项：catch 静默。

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| `SearchResultItem{index,similarity,content,source,fileId,chunkIndex,distance,highlights?,previewSnippet?}` | 结果卡 | SearchTestPanel 渲染；highlightFull（转义+token 降序 mark，:282-295） |
| expand-synonyms（`string[]` 旧 ‖ `{addedTerms}`） | 拼接 `${query} ${added}` | useSynonyms:20-34 |
| graph/expand `{entities,relations,expandedQuery}` | 扩展提示 + 缓存键 | useGraphExpansion |
| analysis `{totalQueries,noResultCount,noResultRate,avgLatencyMs,avgHitCount,topQueries,noResultQueries}` | 指标卡/Top10 | retrieval-analysis（`??0` 兜底） |
| retrievalConfig（RetrievalConfig 共享类型） | 三面板配置模型 | 回填=非空合并默认（detail:73-112） |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| search | POST `/retrieval/search` | api:531-533 | TBD — pending API Contract | 待 tdd |
| synonyms | POST `/query/expand-synonyms` | api:548-550 | TBD | 同上 |
| graph expand | POST `/graph/expand` | api:535-542 | TBD | 同上 |
| suggest/rules | POST `/query/suggest`、`/query-rules/apply` | api:544-554 | TBD | 同上（**仅 debug 页消费**） |
| logs | GET `/retrieval/logs(+​/analysis)` | api:1453-1459 | TBD | 同上 |
| ~~updateRetrievalLog~~ | **零消费死代码**（INFERRED-3） | api:1461-1463 | — | — |
| 本地 mock | autoCorrect←search-correction、rewriteQuery←query-rules | services/query-preprocess:8-9 | —（mock 委托） | 即 mock 本体 |

## Form Addendum

Sidebar/SettingPanel 无表单校验（数值 range 由控件 min/max 约束）；保存双提交=按钮 loading；SettingPanel 改动即 emit（受控）。

## Parent Trace

→ spec TASK-001..007；预处理双轨 → spec INFERRED-6；溯源 → chunk-management 链入口契约。

---

# 特性 10：知识图谱（knowledge-graph-frontend）

## 1. UI Contract

### 图谱 Tab（KnowledgeGraphPanel + 弹窗族）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 构建横幅 | getGraphBuildStatus（5s 轮询，watch isBuilding 启停） | building/completed/failed | building 进度+统计；completed 8s 隐；failed 错误+[重试] |
| G6 画布 | fetchGraphData（maxNodes 设置覆盖，默认 500 硬顶 5000） | v-loading；>400 节点降级 | drag/zoom/drag-node；node/edge click 详情；canvas click 清选 |
| 浮动搜索条 | 本地过滤（input）+ searchGraphNodes（enter） | 恒显 | 本地出下拉；远程整图替换 + isSearchActive；远程失败仅 console（INFERRED-4） |
| Chunk 节点开关 | el-switch（归一） | 恒显 | load(excludeChunks) |
| [索引管理]/[图谱设置] | 弹窗 | 恒显 | 打开对应弹窗 |
| [展开邻居] | expandGraphSubgraph(depth=2,max=30) | 仅选中节点 | 手工映射渲染 + isSearchActive |
| 类型分布弹窗 | normalizedEntityTypes（兼容旧新+8 色板） | 打开 | 点击过滤/取消/显示全部；边需两端可见 |
| 统计条 | stats | 恒显 | 搜索态显「总数/可视数」 |
| IndexManagementPanel | 构建状态 | v-model:visible | 开始索引(全库)/重试(failed)/进度/统计/alert；「重置」=warning+retry（INFERRED-3） |
| GraphSettingsPopup | GET/PUT settings | v-model:visible | maxNodes(10-2000)/depth(1-10)/excludeChunkNodes(默认 true)/entitySchema 白名单；保存「下次构建生效」（maxNodes 实际下次 load 生效 INFERRED-6） |
| NodeDetailPanel | props（纯渲染） | 选中节点 | 只读；attributes 兼容 JSON 字符串 |
| 关系详情弹窗 | 选中边（id 兜底 source+target） | edge:click | tag+源目标+描述（拼句） |

## 2. State Contract

### 图数据查询（load/搜索/展开共用）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | 任一查询在途 | 画布 v-loading | — |
| success | 200 | 渲染（transformData 确定性连线） | — |
| empty | 0 节点 | stats=0 统计条（**无专门空态**） | — |
| error | 失败 | **console.warn，画布空白无提示**（三处一致，INFERRED-4） | 手动刷新 |

### 构建（build/retry/status）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 启动 | 开始索引/开关开 | toast「开始索引」+ 轮询启动 | catch「启动索引失败: msg」 |
| building | status 轮询 | banner 进度 + built/total | — |
| completed | 终态 | ✅ 8s 隐 + 轮询自停 | — |
| failed | 终态 | ❌ + 错误 + 重试入口（banner/面板两处） | retryGraphBuild |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| graph data `{nodes,edges}`（source_id/target_id） | G6 数据（历史数据名称映射兜底） | useForceGraph.transformData（:172-226，唯一连线点） |
| 实体类型 `string[]`（旧）‖ `{name,count}[]` | 归一 + 8 色板 | KnowledgeGraphPanel.normalizedEntityTypes（:97-115） |
| expand `{entities,relations,expandedQuery}` | `{nodes,edges}`（id←entity_id/triple_id） | useForceGraph 手工映射（:314-331，`as any`） |
| build-status `{status,progress,entityCount,relationCount,builtChunks,totalChunks}` | 横幅/面板 | index.vue + KGP |
| settings `{maxNodes,searchDepth,excludeChunkNodes,entitySchema}` | 弹窗表单 | GraphSettingsPopup |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| data/stats/search | GET graph 三端点 | api:560-576,603-605 | TBD — pending API Contract | 待 tdd |
| expand | POST `/graph/expand` | api:565-572 | TBD | 同上 |
| settings | GET/PUT graph/settings | api:594-600 | TBD | 同上 |
| build/status/retry | POST build、GET build-status、POST retry | api:586-620 | TBD | 同上 |
| 单文件 | DELETE graph/file/{fileId} | api:613-615 | TBD | 同上 |
| ~~node detail、index status、labels、rank~~ | **零消费死代码 ×4**（INFERRED-7） | api:578-629 | — | — |
| ~~merge~~ | **不存在**（INFERRED-2） | — | — | — |

mock 依赖：`ENTITY_TYPE_COLORS`（mock/knowledge-graph.ts 唯一存活导出；其余 mock 全死）。

## Form Addendum

GraphSettingsPopup：maxNodes 10-2000、depth 1-10、entitySchema textarea（逗号/换行分隔）；打开回填失败保留默认；保存双提交=actionLoading。

## Parent Trace

→ spec TASK-001..009；确定性连线/降级 → design §4/§2；merge 落空 → INFERRED-2。

---

# 特性 11：评测与基准（benchmark-evaluation-frontend）

## 1. UI Contract

### 评估基准（EvaluationBenchmarkPanel）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| [上传基准] | createBenchmarkApi（**文件不上传不解析，仅按大小估 questionCount** INFERRED-1） | 名称/文件前置校验 | toast + 列表 unshift |
| [自动生成] | generateBenchmarkApi | 名称自动填；LLM 必选 | 构建方式 vector|graph 卡（graph 显 chunkCount）；参数按方式显隐 |
| 基准卡片 | fetchBenchmarks | v-loading | ⋮ 菜单：查看详情/发起评估/删除（confirm，失败静默 INFERRED-3） |
| 详情弹窗 | fetchBenchmarkDetail | — | 问题表 4 列 + 不换行 + 前端分页；Gold tag 恒「有」（INFERRED-2） |
| [发起评估] | emit(benchmark.id) | — | 父切 Tab + 预选消费 |

### 评估结果（RagEvaluationPanel）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 最后一次评估卡 | latest（evaluations[0]） | 有记录（否则 el-empty） | 分数圆圈/状态 tag/Recall@10/耗时/数据量/完成率 |
| 历史记录表 | fetchEvaluations | v-loading | 查看/删除（confirm）+ 前端分页 |
| 配置对话框 | 基准下拉(syncOptions)/模型三用途 | 打开（消费 preselect） | [开始评估] → runEvaluationApi → 缓存+轮询 |
| 结果详情弹窗 | 缓存→fetchEvaluationDetail（失败降级列表行拼 Recall=0） | 查看/运行中自动终态同步 | 指标汇总 + 「仅查看错误」过滤 + 结果表 |

## 2. State Contract

### 基准/评估 CRUD

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | load 在途 | v-loading | — |
| submitting | generating/running | 按钮 loading（双提交防护） | — |
| success | 成功 | toast + 本地更新/刷新 | — |
| error | 失败 | toast「上传/生成/评估失败」；**删除类 catch 静默（与取消共用，INFERRED-3）** | — |
| empty | 无数据 | 仅最后评估卡 el-empty；列表无专门空态 | — |

### 评估轮询（useEvaluation.startPolling）

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| running | start 成功 | 状态 tag + 按钮 loading（**无进度百分比 [GAP]**） | — |
| terminal | completed/failed | 删轮询 id + 清缓存 + load() 刷新 | failed → 列表 tag 呈现 |
| 卸载 | unmount | — | stopPolling |
| 轮询异常 | 请求失败 | **整体静默** | — |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| `Benchmark{hasGoldChunks,hasGoldAnswer,isAutoGenerated,questionCount,…}` | 卡片条件标签/计数 | EvaluationBenchmarkPanel |
| 评估配置 `{benchmarkId,answerModel,judgeModel,retrievalMode,embeddingModel,rerank{enabled,model}}` | runEvaluationApi body | RagEvaluationPanel 收集 |
| `Evaluation{overallScore,recallAt10,status,runId,dataCount,completedCount}` | 最后评估卡（分数 `*100 toFixed(1)`） | REP |
| `EvaluationDetail{recallAt1/3/5/10,answerAccuracy,results[]}` | 详情弹窗 | REP（缓存→API→降级三段） |
| `EvaluationResult{isCorrect:number,judgeReason,generatedAnswer,retrievalMetrics}` | 结果行 + 错误过滤 | REP（`!r.isCorrect` 依赖 0 falsy） |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| benchmarks 组 | GET list/questions、POST(/generate)、DELETE | api:633-660 | TBD — pending API Contract | 待 tdd |
| evaluations 组 | GET list/detail/status、POST run、DELETE | api:662-687 | TBD | 同上 |
| models | GET `/models?purpose=LLM|EMBEDDING|RERANK` | api:1088-1090 | TBD | 同上 |
| ~~mock/benchmark.ts、mock/evaluation.ts~~ | **零外部消费 + 契约过期**（INFERRED-6） | mock:各 207/227 行 | — | — |

## Form Addendum

名称自动默认（Test-日期-xxxx / eval-日期-xxxx）；LLM 模型必选；上传名称/文件前置 warning；双提交=按钮 loading；数字参数 range（1-100/1-10/1-20）。

## Parent Trace

→ spec TASK-001..008；跨 Tab 联动 → design §4；缓存策略 → spec TASK-005/006。

---

# 特性 18：运营分析与系统监控（operation-analytics-frontend）

## 1. UI Contract

### kb-analytics / model-monitor / feedback / qa-detail / system-log

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| kb-analytics 内联指标卡 | GET `/analytics/kb`（catch 静默） | v-loading；两区 el-empty | 纯只读 |
| model-monitor 筛选族 | GET `/monitor/model/overview?timeRange&keyword&page`（单接口聚合） | watch timeRange 重置页码 | MetricCards + RankList(插槽 token/cost) + 表格分页 |
| feedback 列表/统计/明细 | GET `/feedback`、`/feedback/statistics`、`/feedback/overview`、`/chat-sessions`（Tab 懒加载） | 三区独立 loading；el-empty ×4 | 回复(operator='admin' INFERRED-5)/新增/编辑/删除(confirm)；「生成优化建议」=三规则文本+alert（非 AI）；搜索三路联动 |
| qa-detail 表格 | qa-pairs 组（固定 page:1/size:100，INFERRED-6） | KB 下拉切换即重载 | CRUD + 确认生效（仅 draft 行）；无 catch |
| data-mining | GET/POST `/data-mining`、run、DELETE | robot 组唯一真 API 页 | ruleConfig 裸 JSON 文本域不校验（INFERRED-2）；执行后无条件「执行完成」 |
| robot faq/multi/intent | **硬编码行**（multi-turn 的响应变量未使用） | 无 empty 态 | 分页 handler 永不显示（INFERRED-1） |
| system-log | GET `/logs`（records‖list 容错） | 分类单选+筛选 | 行展开 el-descriptions；只读 |

## 2. State Contract

通用模式：v-loading → 渲染/el-empty → 错误分三层（写操作 toast / 读操作静默 / qa-detail 与 robot 页无 catch 外抛）。model-monitor watch timeRange → currentPage=1 → 重拉。feedback 搜索三路联动（列表+统计+总览）。

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| analysis `{metrics,hotKBs,hotDocs}` | 内联指标卡/排行 | kb-analytics（与 robot 页期望形状冲突 INFERRED-1） |
| overview `{metrics,distribution,topApps,stats{list,total}}` | 单接口四区 | model-monitor |
| feedback overview `{metrics×5,questionCategories,hotKeywords,appSatisfactionRanking}` | MetricCards/WordCloud/RankList(插槽) | feedback |
| logs（records‖list） | 审计表格 | system-log（唯一消费方） |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| analytics/monitor/feedback/chat-sessions/data-mining/logs | api:19-21,1386-1447,1513-1531,1998-2000 | src/api/index.ts | TBD — pending API Contract | 待 tdd |
| qa-pairs 组（复用） | api:443-461 | 同上 | TBD | 同上 |
| ~~getFeedback、getAuditLogs、getLoginLogs、getDataMiningTask~~ | **零消费死导出**（getFeedback 与 getFeedbackPage 同 URL 重复定义）（INFERRED-4） | api:1379-1411,1517-1519 | — | — |

## Form Addendum

feedback 新增/编辑共用弹窗（isEditingFeedback 区分）；data-mining ruleConfig 无 JSON 校验；无其他表单。

## Parent Trace

→ spec TASK-001..007；robot 伪数据/颜色语义/死 API → spec INFERRED-1/3/4。

---

# 特性 8：问答对管理（qa-pairs-frontend）

## 1. UI Contract

### QaPanel（详情 Tab qa）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 筛选（关键词/状态/来源） | 页内 computed（纯前端） | 恒显 | 实时过滤；来源仅 manual 选项（INFERRED-3） |
| 表格 | getQaPairs（list‖res） | v-loading；**无空态 [GAP]** | 行操作 |
| [确认] | confirmQaPair | 仅 draft 行（无捕获 INFERRED-4） | 「已确认」+ reload |
| [编辑] | 行回填（keywords join 逗号） | — | 保存仅提交 question/answer（INFERRED-2） |
| [删除] | deleteQaPair | confirm | reload（取消/失败静默 INFERRED-4） |
| [导入] | QaImportDialog | 恒显 | 成功 emit → reload |
| [新增] | 默认 draft/manual/text/priority 5 | — | 非空校验 |
| 分页 | 前端切片 | total>pageSize 才显示 | — |

### QaImportDialog

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 文件选择 | .xlsx/.xls limit 1（手动上传） | — | — |
| 覆盖 checkbox | form | 恒显 | 随 FormData 提交 |
| [导入] | importQaPairs FormData | importing 禁关禁重提 | 结果三 Tab（success/skipped/failed 各 el-empty）+ 汇总 toast + emit success |
| [下载模板] | blob 下载 | — | 失败 toast 重试 |

## 2. State Contract

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | loadData | v-loading | finally 复位 |
| 保存拦截 | question/answer 空 | warning | — |
| 保存/删除失败 | API 错误 | **静默（空 catch）**（INFERRED-4） | — |
| 确认失败 | API 错误 | **无捕获（unhandled rejection）**（INFERRED-4） | — |
| 导入失败 | API 错误 | `e?.message‖'导入失败'` | 保留选择可重试 |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| `{list‖res,total}` | 表格行/分页 | QaPanel:64-67 |
| 表单 `{question,answer}`（仅此二字段） | create/update body | QaPanel:107-116 |
| `QaImportResult{successCount,skipCount,failCount,totalRows,details[{status,rowNumber,question,answer,reason}]}` | 三 Tab 分桶 + 汇总 | QaImportDialog:112-232 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| qa-pairs CRUD+confirm | GET/POST/PUT/DELETE `/kb/{kbId}/qa-pairs*` | api:443-461 | TBD — pending API Contract | 待 tdd |
| import/template | POST `…/import`（FormData）、GET `…/import-template`(blob) | api:464-476 | TBD | 同上 |
| ~~mock getFiles~~ | **死 import（勘误：无真实 mock 消费）** | QaPanel:5 | — | — |

## Form Addendum

question/answer 必填；编辑回填 keywords/similarQuestions 逗号串；导入中双提交防护。

## Parent Trace

→ spec TASK-001..006；死 import 勘误 → spec §6；字段裁剪 → INFERRED-2。

---

# 特性 12：发布、版本与知识审核（publish-review-frontend）

## 1. UI Contract

### PublishPanel（当前 `v-if="false"` 不可达——契约按恢复后形态书写）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| Tabs 版本/历史/计划 | refresh（Promise.all 双兜底） | 恒显 | — |
| 版本表行操作 | PUBLISH_TRANSITIONS 状态机 | draft→提交审核；pending+canReview→通过/驳回（备注弹窗）；approved→发布；published→回退；rejected→重新提交 | transitionVersion `{action}` 或 reviews API（工单分支现死 INFERRED-2） |
| [创建发布] | getKbUpdateLogs 变更摘要 + createVersion | — | 勾「立即发布」→ 链式 submit→approve→旧 draft→新 publish；失败降级提示 |
| 历史区 | getPublishHistory | el-empty 空态 | [发布知识](需 approved)/[撤回]/[重置上一版]；线上/线下版本查看 |
| 计划区 | getPublishPlans/createPublishPlan | — | name+publishTime 校验；执行情况弹窗 |

### review-management（唯一全真实 API 页）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 发布历史表 | getPublishHistory（revoke 记录复用为重置历史） | v-loading | 查看 |
| [发布] | publishApp（**kbId 冒用 appId** INFERRED-4） | — | — |
| [撤回] | revokeKnowledge | confirm | — |
| 重置弹窗 | 上一 offline 版自动选 + 原因必填 | — | resetKnowledge；失败「（本地模式）」本地翻转（假成功） |
| 计划/策略效果 | publish plans/strategy-effect | — | 计划失败同样「（本地模式）」 |

### 7 页族现状契约（flows/flow-design/listeners/compliance/reports/quality）

| 页 | 数据源实况 | 行为 |
|---|---|---|
| flows | 硬编码 10 行 + approve/rejectReview 真实 API | 通过/驳回 **catch ignore 后无条件成功 toast**；工具 10 项（部分不存在 API/仅 localStorage） |
| flow-design | getReviewTemplates **不存在恒空** | 模板 CRUD 同败；导入导出本地 JSON |
| listeners | getListeners **不存在抛错，finally 后兜底被跳过 → 恒空表** | CRUD 全失败；趋势/告警本地随机 |
| compliance | kbId='default'；CRUD 全不存在 | **「检查」按钮调未定义 handleExecuteCheck（运行时 BUG）** |
| reports | 纯 mock | 导出恒「导出失败」 |
| quality | 规则恒空 | 「评分」= 800ms 假耗时 + 硬编码分数 |

## 2. State Contract

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 版本创建失败 | 链式发布任一步失败 | 「版本已创建但自动发布失败」提示手动发布 | 行内手动发布 |
| 驳回 | 备注必填 | transition reject `{reason}` | — |
| loading（PublishPanel） | refresh | **死 loading（无 v-loading 绑定）**（INFERRED-3） | — |
| 7 页各操作 | 不存在 API | 「操作失败/保存失败/导出失败」toast（listeners 恒空表、compliance 抛错） | — |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| `transition {action: submit/approve/reject/publish/draft}` | 行状态机动作 | PublishPanel（types/audit.ts PUBLISH_TRANSITIONS 真源） |
| update-logs | 创建发布变更摘要（fileAdded/Updated/Removed/configChanged） | PublishPanel:46-63 |
| plans `{scheduledAt}` | 定时计划 | :231-248 |
| 27 不存在函数 | 7 页调用 | —（运行时 undefined，INFERRED-5） |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| versions 组 | GET versions(/published)、POST create、POST transition | api:693-717 | TBD — pending API Contract | 待 tdd |
| reviews 组 | POST /reviews、approve、reject | api:721-731 | TBD | 同上 |
| publish 组 | history/plans(+execution)/strategy-effect/revoke/reset/online+offline | api:1600-1651 | TBD | 同上 |
| 误用 | publishApp（app 组） | api:1720 | — | — |
| ~~27 不存在 + 8 死导出~~ | 见 spec INFERRED-5 | — | — | — |

## Form Addendum

创建发布（name/description/tags）；备注弹窗（通过/驳回原因）；计划（name+publishTime 必填）；重置（原因必填）。

## Parent Trace

→ spec TASK-001..011；可达性根问题 → INFERRED-1；状态机真源 → design §3。

---

# 特性 16：工作流编排（workflow-frontend）

## 1. UI Contract

### 业务流管理页（`/application/:id/workflow-manage`，URL 直达）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 画布头 [选择][新建][保存] | getWorkflows/createWorkflow/updateWorkflow | 未选流时灰字+引导 el-empty | 保存=逐节点 updateWorkflowNode（吞错）+ edges JSON 整体存 |
| 节点工具箱 | NODE_TYPES 8 类（页内常量） | 可搜索过滤 | 添加节点（空位防重叠）→ 类型配置弹窗 |
| 画布 | getWorkflowNodes + detail.edges(JSON.parse) | v-loading；空画布 el-empty | 节点拖拽（mouseup 落位 moveWorkflowNode，失败静默）；两击连线（查重/自环拦截）；删节点级联删边 |
| 属性面板 | 选中节点 | 点选 | 只读 Key/类型/位置/配置 JSON/入出边（✕ 删边） |

## 2. State Contract

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| loading | loadCanvas | 页面 v-loading | 失败静默清空 |
| 保存中 | 保存钮 | loading | **逐节点吞错，恒提示「画布已保存」**（INFERRED-2） |
| 连线拦截 | 重复/自环 | toast 拦截 | — |
| 删除确认 | 删流 | confirm（取消静默） | — |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| `workflow.edges`（JSON 字符串） | 边数组 | JSON.parse/stringify（唯一边序列化点，:73,97） |
| node `{nodeKey,type,name,x,y,config}` | 画布节点卡 | updateWorkflowNode 循环 |
| 8 类节点配置字段 | nodeTypeConfigs 页内常量 | :124-162 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| workflows 消费 8 函数 | getWorkflows/create/delete/detail/nodes/updateNode/move/update | api:861-877,1802-1814 | TBD — pending API Contract | 待 tdd |
| ~~其余 25 函数 + apps workflow-config 2~~ | **零消费死 API**（INFERRED-1） | api:881-897,1805-1849,1932-1975 | — | — |

## Form Addendum

创建名称必填；节点编辑按类型出字段（llm：model/temperature/maxTokens/systemPrompt/outputFormat 等）。

## Parent Trace

→ spec TASK-001..004；手写画布 → design §2；死 API → INFERRED-1。

---

# 特性 17：智能体运行时（agent-frontend）

## 1. UI Contract

### 现状契约（agent 前端无独立 API 组与管理页——CERTAIN）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 对话运行时（runtime.vue + AppChat） | apps 组会话四件套 + SSE `/apps/{id}/chat/stream` | 应用列表/编辑器菜单 | 会话生命周期/发送/停止/重试；SSE 事件 message/thinking/tool_call/tool_result/sources/end/error |
| maxSteps/maxTurns/retryTimes | BasicConfig（「Agent 最大执行步数」默认 15） | 编辑器基础配置 | saveAppMaxSteps/MaxTurns/RetryTimes |
| ~~VmConfig~~ | **孤儿组件**（无 import，251 行） | 不可达 | advancedOptions.vm 持久化（INFERRED-1） |
| ~~DebugChat~~ | **孤儿组件**（runApp 非流式调试，286 行） | 不可达 | — |
| agent_state 事件 | useAppChatStream 解析 `{todos,artifacts}` | **无消费方传回调——无 UI**（INFERRED-2） | — |
| ~~独立 agent 管理页~~ | **不存在**（无路由 + api/index.ts 零 agent 函数 + menu-perm-tree:15 注释「暂无对应菜单页面」） | — | — |

## 2. State Contract

### SSE 对话（useAppChatStream，与 app-center 链共享契约）

message（replace 整段替换=敏感词修正/追加）、thinking、tool_call（同 id 合并 arguments）、tool_result（按 id 回填）、sources（仅 runtime 传回调）、end（不一致取 end.content）、error、AbortError→onEnd(aborted)。无重连（GAP）。

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| stream 事件流 | 消息气泡/thinking 块/工具卡 | useAppChatStream switch（:146-265，唯一 SSE 解析点） |
| `advancedOptions.vm` | VmConfig 表单（镜像 5 选/CPU/内存/磁盘/包/环境变量） | 孤儿组件（不可达） |
| `advancedOptions.members` | MemberConfig 成员（app-center 域） | 同上结论参考 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| chat stream | fetch POST `/apps/{id}/chat/stream`（手带 Bearer） | useAppChatStream:98-116 | TBD — pending API Contract | 待 tdd |
| 会话四件套/max-steps/retry | apps 组 | api:810-837 | TBD | 同上 |
| ~~api:agent:*~~ | **前端零 agent 端点**（权限树登记属纯后端权限项） | — | — | — |

## Form Addendum

无独立表单；VmConfig 环境变量键值表保存前过滤空行（不可达组件，记录备查）。

## Parent Trace

→ feature-map 特性 17「INFERRED 前端面缺失」**升级为 CERTAIN**（无路由+无 API 组+权限表自证）；SSE 契约 → app-center 链共享。

---

# 特性 15：应用中心（app-center-frontend）

## 1. UI Contract

### 应用列表（/application）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 卡片网格 | getApps | v-loading；el-empty 空态 | 卡体 → 编辑器；[▶运行] → runtime |
| [添加应用] | createApp | 名称必填 | 三类型 ChatBot/Editor/LiteAgent |
| [删除] | deleteApp | confirm「同步清理历史记录」 | 取消静默 |
| 标签筛选 | 已有应用标签聚合 | 恒显 | — |

### 编辑器（9 已接线面板）

| 面板 | 职责 | 保存语义 |
|---|---|---|
| BasicConfig | 基础/模型参数/系统提示词/上下文压缩/运行控制（maxSteps 默认 15） | **一次保存串行 7 API**；apiKey 非空才进 payload |
| KnowledgeConfig | KB 勾选绑定（左分类右列表） | diff → unbind/bind 逐条；失败回滚重拉 |
| Skill/Tool/McpConfig | 三类勾选绑定 | 同 diff 模式 |
| DatabaseConfig | DB 实例绑定（dbId/alias/allowedTables） | — |
| MemberConfig | 成员/角色（**存 basic.advancedOptions.members JSON** INFERRED-3） | 随 basic 保存 |
| ChatLogs | 对话记录查询（筛选/分页/详情/删除/CSV 导出） | — |
| AppChat | SSE 对话调试（会话/停止/重试；完成发站内通知 INFERRED-10） | — |

### runtime（对话运行时）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 应用/会话切换 | query.appId watch；会话四件套 | 流式中禁切换删除；删当前回退第一个 | 切应用清会话+拉问候语 |
| 消息流 | SSE 9 事件 | 三态（loading/未选/开场白卡） | 增量渲染+滚动跟随（距底<80px） |
| 发送/停止/编辑重发 | sendMessage 8 回调 | — | 首条截 30 字作标题；停止标「(已停止)」；编辑重发 splice 原消息 |
| 消息删除/点赞点踩 | delete/feedbackAppMessage | 仅 runtime | 再点取消 |
| 知识来源+图片 | sources 事件 + Bearer blob | 仅 runtime | 溯源跳 chunks 页 |

### app-config（孤儿路由，5 Tab）

基础配置/对话设置（JSON 导入导出）、全局策略（触发器 CRUD+启停+测试运行（JSON alert）、变量 CRUD、未匹配策略、策略启停）、知识库绑定（增删+JSON）、对话测试（案例 CRUD+CSV）。

## 2. State Contract

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| SSE 流式中 | 发送后 | streaming 光标 + isThinking + 工具「执行中」 | 停止→aborted 标记；错误→气泡文案；**无重连（GAP）** |
| 面板保存 | 保存钮 | loading | 失败 ElMessage.error |
| 绑定失败 | diff 保存单项失败 | error + 回滚重拉 | — |
| 列表删除失败 | — | e.message | — |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| SSE 事件流（message/thinking/tool_call/tool_result/sources/agent_state/end/error/init） | 消息流视图模型 | useAppChatStream switch（:146-265，唯一解析点；agent_state 无消费方） |
| 绑定 diff | bind/unbind 调用序列 | 各绑定面板内 |
| basic.advancedOptions `{members,vm}` | MemberConfig/VmConfig(孤儿) | — |
| 触发器测试结果 | JSON 原样 alert | app-config:110-139 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| apps 组 89 端点（CRUD/会话/M16 配置 35/五类绑定 12/扩展 15/记录 4） | api:757-855,1652-2029 | src/api/index.ts | TBD — pending API Contract | 待 tdd |
| ~~死导出 11 个 + 仅孤儿消费（runApp/optimizations/sensitiveWords/knowledge-update）~~ | INFERRED-6/1 | api 各处 | — | — |
| ~~publishApp~~ | **app-center 内无发布 UI**（INFERRED-7） | api:1720 | — | — |

## Form Addendum

创建名称必填；BasicConfig apiKey 空值跳过（防清空）；导入 JSON 动态 file input；双提交=按钮 loading。

## Parent Trace

→ spec TASK-001..005；孤儿面板/路由 → INFERRED-1/5；SSE → agent 链共享契约。

---

# 特性 14：模型与平台配置（platform-config-frontend）

## 1. UI Contract

### model-management / api-keys / kb-config / general-settings / sensitive-words / dictionary / terminology / templates

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 模型卡片 + CRUD/复刻 | models 组 | el-empty「暂无模型，请新增或导入」 | 保存（apiKey 空值跳过防清空）；删除 confirm（catch{} 吞） |
| [上下架] | toggleModel | 每卡 | toggle + 刷新 |
| [测试] | 按 purpose 分支三端点 | 弹窗 | EMBEDDING 需 docs 非空；结果 el-descriptions（answer/dimensions/vectorPreview/results） |
| [导入 JSON]/[导出] | importModels/exportModels | — | 导入刷新；**导出无落盘仅 toast（INFERRED-3）** |
| 开放密钥表 | getDictionaries({type:'API密钥'}) | — | 前端生成 `sk-ais-`+random；明文展示+复制（**与「不再显示」alert 矛盾** INFERRED-4）；expiry/permissions 不提交 |
| kb-config 行内编辑 | updateKnowledgeBase {totalSize} | — | **改展示串提交原值——不生效**；默认配置/批量修改=假成功（INFERRED-7） |
| general-settings | getSysConfigs(brand\|general) 回填 | — | 保存=12×saveSysConfig 并发；重置=数组传单 key URL（INFERRED-8）；Logo 直连 POST /api/upload |
| 敏感词/字典/术语 | 各组 CRUD | el-empty | 敏感词导入 CSV（动态 input）；屏蔽开关 disabled（预留）；术语批量导入=「开发中」stub |
| templates 卡片 | **整页 mock（@/mock/answer-kb）** | 无空态 | 删除/保存死链回退到 FAQ mock（写错 store，列表永不更新 INFERRED-9） |

## 2. State Contract

标准管理页三态；特例：模型删除 catch{} 静默；通用设置并发保存无聚合错误；api-keys 部分函数无 catch。

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| 模型 `{purpose}` | 测试分支（EMBEDDING/RERANK/其余） | model-management:100-129 |
| 字典 `{type:'API密钥', key:name, value}` | 密钥行 | api-keys:67-82 |
| `configKey/configValue(String 归一)/configType` | 设置表单 | general-settings:118-153 |
| 品牌 config | system store 四字段 | stores/system.ts:20-33 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| models 10 消费 | api:1088-1110,1501-1558 | src/api/index.ts | TBD — pending API Contract | 待 tdd |
| config 4 消费 | api:1561-1582 | 同上 | TBD | 同上 |
| dictionaries/sensitive-words/terminology | 5+6+8 端点 | api:1112-1232 | TBD | 同上 |
| upload（Logo） | POST /api/upload 直连 | general-settings:20-24 | TBD | 同上 |
| ~~models 死导出 ×5 + config 孤儿端点~~ | INFERRED-5/6 | api:1092-1555,1564-1604 | — | — |

## Form Addendum

模型名称必填；密钥名称必填；敏感词/字典/术语标准 CRUD 校验；模板页无校验（mock）。

## Parent Trace

→ spec TASK-001..008；假成功/死链回退 → INFERRED-7/9。

---

# 特性 13：工具/技能/MCP/外部数据库（tools-skills-mcp-frontend）

## 1. UI Contract

### HTTP 工具（my-tools + ToolForm + ToolTestDialog）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 工具表格 | getTools（前端关键词/类型过滤） | el-empty | 启停 toggle/删除 confirm/编辑跳路由 |
| ToolForm 请求配置 | form | 恒显 | Method/URL/鉴权三型/Query/Headers/Body 模板；**cURL 导入解析**；`${}/{{}}` 变量自动提取入参 |
| 出入参 Schema | 双文本域 | 高级区 | inputsToSchema/schemaToInputs 互转 |
| ToolTestDialog | inputSchema→inputs→Body 变量三级合成参数 | — | 场景 localStorage；testProxy 代理执行；**成功也报错（`resp` 未定义 BUG INFERRED-1）**；历史 50；导出 JSON/Markdown |

### MCP（mcp-services + McpForm + detail）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 服务列表 | getMcpServices（normalizeService 归一） | el-empty | 搜索(名称/URL/工具名)+状态过滤；开关；删除仅非内置 |
| McpForm「解析」 | parseMcpUrl `{mcpUrl,transport:'sse',authType,authValue}` | 双 Tab | 发现工具回填 + 跳 tools Tab |
| detail 刷新 | refreshMcpService → 重连+发现 | — | 重拉详情 |
| 单工具测试弹窗 | testMcpTool(toolId, form) | tools Tab | 参数恒 el-input（number 传字符串 INFERRED-6） |

### 技能（skill-management + SkillCreate/SkillDetail）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 技能表格 | getSkills（分类过滤） | el-empty | 启停(absolute)/删除/**上传技能包**(.zip/.tar.gz/.skill → prepareSkillImport 一次四步) |
| SkillCreate 虚拟文件树 | 内存 virtualFiles | SKILL.md 不可删；删除仅内存 | 提交三步：createSkill(slug 归一)→循环 createSkillFile→按 slug 找 id→updateSkillDependencies；**`form.value` 崩溃（INFERRED-2）** |
| SkillDetail 文件树 | getSkillTree/getSkillFile/updateSkillFile | — | 路径=el-tree parent 链拼装 |
| 生效范围/导出/依赖 | toggleSkill/updateSkillShareConfig/exportSkillZip/updateSkillDependencies | — | — |

### 外部数据库（database-management）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 实例表格 | getDatabases | el-empty | CRUD（类型切换自动填端口）；测试连接→本地改状态 |
| 表浏览 | getDatabaseTables/syncDatabaseTables | — | 同步报告 syncedCount；columns JSON→列明细 |
| SQL 工作台 | queryDatabase `{sql}` | 弹窗 | 结果表格（columns/rows/rowCount/elapsedMs）；**前端零校验**（placeholder 声明只读、开关仅提示——INFERRED-3） |

## 2. State Contract

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 各列表 loading | 加载 | v-loading | — |
| 工具测试 | runTest | **成功也覆盖为 status:0 'Error'（INFERRED-1）** | — |
| 技能创建 | 提交 | **TypeError 崩溃（INFERRED-2）** | — |
| DB 测试/SQL | 执行 | 按钮 loading | error 字段展示 |
| 取消类 confirm | 用户取消 | 静默（catch{}） | — |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| McpService `{status:Integer, toolsSchema:Map}` | `online:boolean, tools[]` | normalizeService（mcp-services:14-46） |
| Body 模板 `${var}` | 入参表 | extractVarsFromBody（ToolForm:56-82） |
| 技能树 `{path,isDir,content}` | el-tree + 路径拼装 | SkillDetail:85-95; SkillCreate buildTree |
| SQL 结果 `{columns,rows,rowCount,elapsedMs,error}` | 结果表格 | database-management:340-352 |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| tools 7 / skills 15 / mcp 9 / databases 8（消费） | api:905-1082,1855-1884 | src/api/index.ts | TBD — pending API Contract | 待 tdd |
| ~~死导出 ×9 + 半截草稿流~~ | INFERRED-4 | api:1003-1035,1065,1489-1495,1858,1879 | — | — |
| mock 常量/类型 | `@/mock/tools|mcp|skills`（CRUD 函数已死） | 各表单 | — | —（INFERRED-5） |

## Form Addendum

工具名称/标识必填；MCP mcpUrl 必填（transport 固定 sse）；技能 slug 归一化；DB 类型切换自动填端口；SQL textarea 无前端校验。

## Parent Trace

→ spec TASK-001..005；双高危 BUG → INFERRED-1/2；SQL 零校验 → INFERRED-3。

---

# 特性 19：会话中心与新建对话（new-conversation-frontend）

> 后端契约：fastrag-backend `contract/interfaces.md ## new-conversation`（2026-09-25 已实现，模块测试 71/71 绿）。
> 事件词汇与旧通道同一封闭集（REQ-C02）；HTTP 路径以后端 springdoc 为准。

## 1. UI Contract

### 会话中心（/conversation）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| [+ 新建对话] | createConversation | 恒显（左栏顶部） | 建直聊会话并激活，聚焦输入框 |
| 会话列表 | listConversations（page/pageSize） | loading 骨架×5 / empty 引导 / error 重试 | 项点击 → 加载详情+恢复请求态 |
| 置顶图钉 ⭐ | isPinned | 列表项右上角常驻 | 点击翻转 → 列表即时重排（置顶优先） |
| 来源 tag | appId/appName/direct | 恒显 | 直聊=灰「直聊」；应用=蓝「{应用名}」 |
| 项操作菜单 ⋯ | — | 恒显 | ✨智能起标题 / ⭐置顶 / 🧹清空 / 删除 |
| checkbox 多选 | — | 多选模式激活 | 底部浮出「删除所选」→ ElMessageBox 确认 → deleteConversations |
| 分页 | PageResult{list,total,page,pageSize} | total > pageSize | `session-list__pagination`（usePagination） |
| 清空 | clearConversationMessages | 项菜单确认后 | 消息区空态；列表摘要置空（后端置空 first_question/answer_summary） |

### 聊天区

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 消息流 | getConversation.messages + SSE | 历史骨架 → 正序渲染 | 智能跟随（距底<80px，沿 runtime） |
| 气泡 markdown | marked + chat-markdown.scss（单源 partial） | assistant/user 分侧 | 复制/下载 docx/删除/👍👎/编辑重发 |
| ThinkingBlock / ToolCallRenderer | SSE thinking/tool_call/tool_result | 有内容时 | 共享件直接复用 |
| SourcesBlock（双形态） | 流内 sources 事件 / 历史消息 references | 命中或落库时 | stream=全文展开+图片 blob 预览；history=来源行+跳 `/knowledge/{kbId}/chunks/{fileId}?chunkId=` |
| 停止标注 | onEnd(aborted) | 停止触发 | 「(已停止)」，已生成部分保留 |

### 输入区（ChatComposer）

| Element | Data source | Display condition | Interaction result |
|---|---|---|---|
| 应用下拉 | getApps（api:757 复用，零新增调用） | 恒显（控制栏左侧） | 首项「直聊（不挂应用）」；选中=挂 composerState.appId（预设 chip 随显，KB 多选禁用保持）；选直聊=清 appId——chip 与下拉同源双向同步 |
| 预设 chip | route.query.appId / requestState.appId | 挂载应用时 | 「预设：{应用名} ×」移除=转直聊（下拉同步回「直聊」） |
| 📚KB 多选 | getKnowledgeBases + requestState.kbIds | 直聊可用；挂预设禁用+「知识库范围由应用决定」 | KbScopePicker 弹层（分类+搜索+多选） |
| 能力 popover | getTools / getSkills / getMcpServices（api 既有封装，零新增） | 仅直聊（挂预设隐藏——能力走应用绑定） | 工具/技能/MCP 三分组多选弹层（CapabilityPicker，复用 KbScopePicker 弹层模式）；已选以 chip 内联控制栏，单个可移除；随 stream body toolIds/mcpIds/skillIds 透传（空数组发 undefined，B02 演进） |
| 模型下拉 | getModels(purpose=LLM) 过滤 online | 恒显 | requestState.modelId 优先回显 |
| 发送/停止 | useConversationStream.send / stopStream | 流式中禁发+显停止 | Enter 发送（Shift+Enter 换行）；随首条消息 createConversation |

## 2. State Contract

| STATE | Trigger | Visible feedback | Recovery |
|---|---|---|---|
| 流式中 | 发送后 | isStreaming 禁重复提交 + 停止按钮 | 停止→双保险（abort+POST stop），已生成部分保留标「(已停止)」；错误→error 气泡 |
| 检索等待 | 直聊挂 kbIds 发送后 | streaming 气泡上方「正在检索知识库…」轻量动画 | sources 事件 / 首个 message 增量 / end / error 复位；纯前端状态，零新增后端事件 |
| 请求态还原 | 重开会话 | 输入框自动回填 kbIds/modelId/预设/直聊能力三字段 | 存量无请求态→挂应用用应用配置/直聊空范围（D03） |
| 起标题 | ⋯菜单触发 | 标题即时更新 | 失败静默保持原标题（后端回退截断首问） |
| 批量删除 | 「删除所选」 | ElMessageBox 确认 | 取消静默；失败 ElMessage.error |
| 单库无权限 | 后端跳过告警 | 该 KB 来源不出现，对话继续 | — |
| 工具结果结构化渲染 | tool_result / 历史 toolCalls 的 result.data（display_type 两类） | ToolCallRenderer 卡片内 | resolveToolDisplayType 路由：database_query→DatabaseQueryTable（表格/空态/异常三分支）/ knowledge_chunks→KnowledgeChunksList（score 百分比+content 截 200+source）/ generic→原始输出 pre；收起态常驻 summarizeToolCall 一行摘要；三批兜底按工具名识别：含 mcp→「MCP 工具」tag + output 可 JSON.parse 为对象/数组时键值/列表化（不强解析，失败回 pre）、含 skill 或 read_file/read→「技能」tag + renderMarkdown 渲染（后端技能读取返回 Markdown） |
| 引用角标 | assistant 且 sources/references 非空 | 消毒后 HTML 中 [N]（N ≤ 来源数）→ 上标 sup.cite-ref（$primary、hover 下划线、pointer；utils/citeSources.ts 纯函数，sanitize 之后注入自有安全 HTML） | 点击角标 emit open-sources → sourcesDrawerVisible 置 true 打开全部来源抽屉（受控经 ConversationChat/MessageBubble 透传 SourcesBlock）；N 超出来源数或无来源不替换（防模型幻觉编号伪装成引用） |
| 会话汇总行 | assistant 完成且有 toolCalls/iterations（end 事件 data.iterations） | footer 上方 chip 行：🔧 N 次工具调用 · M 轮 · 耗时 T（缺失项跳过） | 停止标注「(已停止)」不作耗时展示；无工具且无轮数不渲染 |

## 3. Data Mapping

| API field | VM field | Transform location |
|---|---|---|
| SSE 九类事件（同 useAppChatStream 封闭集） | 消息流视图模型 | useConversationStream（分帧经共享 utils/sse.ts；零新增事件） |
| request_state JSON（ConversationRequestState） | composerState{kbIds,modelId,appId} | index.vue applyRequestState；**发送成功不本地回写**，以后端固化为准 |
| 消息 references（MessageReference 投影，无 content） | SourcesBlock history 形态 | mapHistoryMessages（JSON 串/数组双形态容错） |
| sources 事件（含 content） | SourcesBlock stream 形态 | useConversationStream onSources |
| appId+appName+direct | 来源 tag 文案 | SessionListPanel（appName 每页一次批量查询，后端已防 N+1） |
| result.data（结构化数据，后端透传） | 专用渲染器 props.data | utils/toolDisplay.ts（resolveToolDisplayType/summarizeToolCall，纯函数）；两类形状：`*_query_database`→{display_type:'database_query',columns,rows,row_count,elapsed_ms}、`query_kb`→{display_type:'knowledge_chunks',kb_id,query,chunks[{content,score,source}]}；流内 useConversationStream onToolResult 整事件赋 result 自动携带，历史经 normalizeToolCalls 透传（扁平结构收编顶层 data） |

## 4. API Dependency

| API | 调用点 | Source | Version | Mock/fixture |
|---|---|---|---|---|
| POST/GET `/conversations`、GET `/{id}`、PUT `/{id}/pin`、DELETE `/{id}/messages`、POST `/batch-delete`、POST `/{id}/title`、POST `/{id}/stop` | api/index.ts（new-conversation 组） | src/api/index.ts | 后端已实现（springdoc 为准） | — |
| POST `/{id}/stream`（SSE 九事件） | useConversationStream（fetch 直连） | utils/sse.ts 共享分帧 | 后端已实现 | vitest：sse.test.ts + useConversationStream.test.ts（17 用例） |
| 消息级 DELETE/POST feedback/PUT content（`/{id}/messages/**`） | api/index.ts | 后端 M2 已补（SessionCenter 委托账本） | 同上 | — |
| GET /models?purpose=LLM、GET /knowledge-bases | 复用既有封装 | api:1088 / KnowledgeConfig 数据源 | 现网 | — |

## Form Addendum

新建会话表单全可选（appId/kbIds/modelId）；kbIds 上限 50 超限后端拒绝（前端弹层不设硬顶，交后端校验）；清空/批量删均需确认；起标题无表单。

## Parent Trace

→ spec TASK-001..010；6 INFERRED 已裁决（decisions/inferred-adjudication.md §19）；后端 REQ-A01..G04（fastrag-backend spec/new-conversation.md）。
