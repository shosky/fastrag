# 解析策略 — Frontend Spec（parse-strategy-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 3 证据包；术语 ← `CONTEXT.md`（解析策略/策略绑定/自动匹配/重新分片）。

## 1. Scope

覆盖策略管理页（列表 + 创建/编辑弹窗、五类分片策略参数面板）、文件级换绑对话框（StrategyChangeDialog，FileManager 承载）、上传向导中的策略选择与分块预览。**排除**：策略模板的消费方（知识库创建页，属 kb-core 链）；重新分片的执行与轮询（属 chunk-management / file-ingestion 链）。

## 2. User Tasks

- **TASK-001 查看策略列表** — `/knowledge/:id/parse-strategy`：名称客户端过滤 + 刷新；列 = 名称（默认 tag）/描述/扩展名 tags/文档类型/分片策略 tag/更新时间/操作；**无分页**（全量加载）；表头 tooltip 提示覆盖链「上传请求参数 > 选中的策略 > KB 默认策略 > 系统默认值」（parse-strategy.vue:400-508,427-434 ‹CERTAIN›）。
- **TASK-002 创建/编辑策略** — 内联 el-dialog（800px）：名称(≤50)/描述(≤200)/文档类型（选项来自后端 meta）/扩展名多选（随类型联动，实时冲突警告）/分片策略 5 卡片/策略参数面板（随策略切换）/高级参数折叠（LLM 模型、表格处理模式、视频关键帧、图片提示、恢复默认）（:511-836 ‹CERTAIN›）。五类面板：rule_fixed（ChunkLengthSelect+分隔符+重叠 0-500）/ rule_recursive（最大块+递归分隔符+重叠）/ structure_aware（单块最大+titlePrefix+headingPath 开关）/ semantic（Embedding 模型+突变阈值滑条 0-100+单块最大）/ parent_child（子片长度预设[100,200,500]+重叠+兜底分隔符+父片最大 500-10000+聚合层级 H1/H2/H3/auto）（:619-772 ‹CERTAIN›）。**无 VLM 模型字段**（GAP——概念不存在于前端）。
- **TASK-003 扩展名冲突检测** — watch extensions → 防抖 300ms → `detectConflicts` → 表单下方内联警告；提交前二次复检，命中 → 强确认弹窗（返回修改/仍然保存）（:126-139,337-349,580-583 ‹CERTAIN›）。
- **TASK-004 设默认/删除** — 设默认需确认；默认行隐藏「设默认/删除」（:285-301,488-505 ‹CERTAIN›）。删除：默认策略禁止；有引用文件时提示「将回退自动匹配」（fileCount，:304-328 ‹CERTAIN›）。
- **TASK-005 文件级换绑重切** — FileTable 策略 tag 点击（QA 文件不可点）→ StrategyChangeDialog：以当前生效 chunk 参数为基线合并、`parseMethod` 固定 'default'、保存为 `file_id` 隐藏专属记录（不出现在策略列表）；**对话框内常驻 el-alert**（「保存后将按新配置重新分片：删除现有分片与向量并重新分片、向量化（消耗嵌入调用）。原文件与 QA 对保留。」）+ 按钮「保存并重新分片」；处理中文件锁死；QA 文件提示不受影响并禁用保存（StrategyChangeDialog:13-19,50-55,174-187,279-295; FileTable:210-218; FileManager:582-595 ‹CERTAIN›）。ADR-0001（绑定变更必须重切）的前端落点。
- **TASK-006 上传时策略选择与分块预览** — FileUploader 向导第 2 步：首项「自动匹配（按扩展名）」（value=''，空串不转发）；默认策略名带「（默认）」后缀；第 3 步 per-file 分块预览（previewChunks/totalChunks/pages；loading/error 三 Map，失败可重载；未上传完成显空态）（FileUploader:385-400,459-493,906-914 ‹CERTAIN›）。
- **TASK-007 恢复默认高级参数** — 弹窗内一键恢复 DEFAULT_ADVANCED（chunkLength=1000/overlap=100/delimiters=['\n\n']/semanticThreshold=30/parentMaxChunkLength=2000）（:260-265; types/knowledge.ts:470-491 ‹CERTAIN›）。

## 3. Pages & Routes

- **PAGE-001 策略管理页** — `/knowledge/:id/parse-strategy`（routes.ts:78-83，hidden「解析策略管理」）。入口：详情页「解析策略」按钮（`v-permission kb:manage_strategy`，detail/index.vue:248）；孤儿 form.vue:602 亦跳此页。**路由本身无守卫字段**——直敲 URL 可达（INFERRED-6）。
- **PAGE-002 StrategyChangeDialog** — 非路由，FileManager 持有（:739-744）。
- **PAGE-003 上传向导策略步骤** — FileUploader step2/3（属 file-ingestion 宿主，策略语义归本链契约）。

## 4. Elements & Interaction

- 文档类型切换 → 重置扩展名勾选为该类型扩展集；`default` 类型可选全部 supportedExtensions（:99-108 ‹CERTAIN›）。
- `adv` Proxy：`advanced.chunk.*` 提升顶层双向绑定（:150-170）；切换策略卡片 → 重置全部 chunk 参数为 DEFAULT（:229-242）；提交剥离 `advanced.index`（二期占位，:351-355）。
- `normalizeAdvanced`：新 `{parse,chunk,index}` 分组 / 旧平铺字段双格式归一（:178-208）。
- ChunkLengthSelect：预设钮组 + 自定义（0 值显输入框，enter/blur 提交，`Number(value)||1000` 兜底）；父子场景预设 [100,200,500]（ChunkLengthSelect:22-34; types:555-568 ‹CERTAIN›）。
- 上传配置 `parseStrategyId` 空串不转发（语义 = 自动匹配，FileManager:111 ‹CERTAIN›）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 策略列表 | useParseStrategy.loading → v-loading | **无专门空态** | **无 catch**（静默空列表） | useParseStrategy.ts:9-17 |
| meta 加载 | — | — | catch 静默降级（空数组，页面其余正常） | parse-strategy.vue:88-90 |
| 模型列表 | — | — | try/catch ignore | :63-77 |
| 冲突检测 | 防抖 300ms | — | catch 返回 `[]` | :126-139; useParseStrategy.ts:67-73 |
| 创建/更新提交 | 校验 + 提交 | — | **无 catch**（弹窗停留、无提示；依赖拦截器与否 INFERRED-2） | :331-368 |
| 删除/设默认 | — | — | 显式 message | :285-328 |
| 换绑对话框 | submitting 按钮态 | 处理中锁死 | `e?.message\|\|'保存失败'` | StrategyChangeDialog:141-179 |
| 上传分块预览 | per-file loading Map | 「文件尚未上传完成」 | error Map + 重新加载 | FileUploader:459-493 |

刷新按钮**假成功**：「列表已刷新」无条件弹（:371-374，INFERRED-3）。

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 策略列表 | `fetchStrategies` GET `/kb/{kbId}/parse-strategies`（api:491-493） | TBD — pending API Contract |
| CRUD/设默认 | createStrategyApi/updateStrategyApi/deleteStrategyApi/setDefaultStrategyApi（api:499-513） | TBD |
| 冲突检测 | POST `…/conflicts` body `{extensions,excludeId}`（api:519-525） | TBD |
| 元数据 | GET `/parse-strategies/meta` → methods[]/supportedExtensions（api:487-489） | TBD |
| 模型 | GET `/models?purpose=LLM|EMBEDDING` → list，id/name/brand/code（api:1088-1090） | TBD |
| 换绑保存 | POST `/kb/{kbId}/files/{fileId}/strategy`（api:205-218；基线合并 + parseMethod='default'） | TBD |
| 分块预览 | GET `…/preview?strategyId=` → previewChunks[].{title,content,index}/totalChunks/pages（api:186-199） | TBD |
| 上传配置 | `processConfig.parseStrategyId`（FileManager:111-137 转发 processFile） | TBD |

死代码（INFERRED-8）：`useParseStrategy.detail()` 零调用；`resolveByExtension` 在 FileManager.vue:89 解构未消费（**resolve 端点实况无 UI 消费**——勘正 feature-map「resolve/conflicts 消费」）；`previewFileChunks` 的 `customConfig` 参数全仓无消费者；`ParseStrategy.fileCount` 仅删除确认用。

## 7. Responsive & Accessibility

桌面优先无断点（全站一致 [GAP]）；键盘/读屏 [GAP]。

## 8. Acceptance

- [ ] 列表渲染默认 tag 与覆盖链 tooltip；名称过滤实时生效
- [ ] 创建/编辑弹窗：类型切换联动扩展名；策略卡片切换重置参数；五类面板字段与预设正确
- [ ] 扩展名冲突：内联警告（防抖）+ 提交前强确认双拦截
- [ ] 默认行无设默认/删除；删除含 fileCount 影响提示与自动匹配回退文案
- [ ] 高级参数折叠三项按文档类型条件显隐；恢复默认还原 DEFAULT_ADVANCED
- [ ] 换绑对话框：QA 文件 tag 不可点；处理中锁死；常驻重切警告；保存为隐藏专属记录并触发轮询
- [ ] 上传向导：自动匹配空串不转发；分块预览三态与重载
- [ ] ChunkLengthSelect 自定义 0 值兜底 1000

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 策略页直敲 URL 可达（路由无权限字段）是否需补守卫？INFERRED-2 创建/更新无错误兜底（依赖 request 拦截器与否）是否补 catch？INFERRED-3 刷新假成功是否修？INFERRED-4 StrategyChangeDialog 自带 STRATEGY_OPTIONS 本地常量与主页面 CHUNK_STRATEGY_OPTIONS 文案不一致（「固定长度」vs「固定大小切分」）是否统一？INFERRED-5 复制/克隆策略不存在——是否为需求缺口？INFERRED-6 模板能力与策略页解耦（仅创建页消费）是否符合设计？INFERRED-7 URL 分块预览 customConfig 参数无消费者——保留 or 删？INFERRED-8 死代码清理（detail()/resolveByExtension 死解构）。
