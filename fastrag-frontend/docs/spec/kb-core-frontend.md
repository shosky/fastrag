# 知识库管理 — Frontend Spec（kb-core-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 2 证据包；术语 ← `CONTEXT.md`（知识库）。

## 1. Scope

覆盖知识库入口域：列表（卡片网格 + 分类/标签筛选）、分类管理、创建/编辑（共享表单体四 Tab）、门户首页（与 kb 真实耦合部分）。**排除**：知识库详情页内部（各面板归各自特性链）；`/workspace` 两页（纯本地原型、零 API、入口孤岛——是否拆除/接线待 INFERRED 裁决，不入契约）；孤儿页（feature-map 孤儿清单）。

## 2. User Tasks

- **TASK-001 浏览/筛选知识库** — `/knowledge`：Tab（全部/我的）+ 分类单选 + 标签多选 + 关键字，全前端过滤（`filtered = kbs |> tab |> keyword |> category |> tags`，index.vue:58-111 ‹CERTAIN›）；卡片网格分页 12/页（usePagination(12) + 前端切片，`.knowledge-list__pagination` ‹CERTAIN›）。**getKnowledgeBases 的 keyword/category/page 参数从未传参**——服务端分页能力闲置（INFERRED-1）。
- **TASK-002 创建知识库** — `/knowledge/create`（入口按钮 `v-permission kb:create`，index.vue:121-123）：form.vue 四 Tab（基本信息/共享设置/高级配置/检索设置）→ 提交 → toast → 回列表；**创建后自动建默认解析策略**（模板匹配，失败静默不阻断，create.vue:29-52 ‹CERTAIN›）。**创建模式 ACL 不落库**：仅 `permission` 字段随 POST 提交，指定人/部门条目要等下次编辑保存才写 setKbAcl（form.vue:523,559 ‹CERTAIN›；后端是否按 permission 落 ACL [GAP]，INFERRED-2）。
- **TASK-003 编辑知识库** — `/knowledge/:id/edit`（入口在详情页）：详情加载（查不到 →「知识库不存在」+重定向）→ 回填 → 保存；嵌入模型 edit 锁定（disabled + tooltip「创建后不可更改」，form.vue:910-943 ‹CERTAIN›）。
- **TASK-004 共享/权限配置** — 共享设置三卡（全局/部门/指定人）；`shareType↔permission` 映射 global→public、department→team、specified→private（form.vue:511-518 ‹CERTAIN›）；**仅 edit 模式写 ACL**，构造规则：始终含自己 owner；global 追加 `userId:'*'` viewer；department 逐部门拉成员去重 viewer；specified 按人写角色（:520-565 ‹CERTAIN›）。**edit 回填无 department 分支**——部门共享的 KB 编辑时被还原为「指定人视图」（:321-349 ‹CERTAIN›，INFERRED-3）。
- **TASK-005 删除知识库** — 编辑页危险区（`canDelete`：super_admin 或 owner；`v-permission kb:delete`）→ 确认框「数据无法恢复」→ 删除 → 回列表（edit.vue:58-75, form.vue:495-502,1106-1124 ‹CERTAIN›）。
- **TASK-006 分类管理** — `/knowledge/categories` 扁平表格 CRUD（名称/描述/所属组织/颜色 8 预设/排序 0-999）；超管可指定组织，普通用户强制本组织（categories.vue:17-20,58,147-188 ‹CERTAIN›）。
- **TASK-007 表单内快捷新建分类** — 基本信息 Tab「+」按钮（`v-permission kb:category:create`）→ 创建后自动选中；失败 console 静默（form.vue:390-411 ‹CERTAIN›）。
- **TASK-008 门户首页聚合** — `/home`：快捷入口 4 卡（按权限过滤）+ 推荐 KB（点击进详情）+ 热门文档/应用/动态（home/index.vue:12-132 ‹CERTAIN›）。

## 3. Pages & Routes

- **PAGE-001 知识库列表** — `/knowledge`；Sidebar:88-99（MAP:492-493）；退出 → 卡片点击 `/knowledge/:id`。
- **PAGE-002 分类管理** — `/knowledge/categories`；Sidebar 同组。
- **PAGE-003 创建知识库** — `/knowledge/create`（hidden）；薄壳 + 共享表单。
- **PAGE-004 编辑知识库** — `/knowledge/:id/edit`（hidden）；薄壳（loading 占位 + KnowledgeForm mode=edit）。
- **PAGE-005 门户首页** — `/home`；Sidebar「首页」:84。
- ~~PAGE-006/007 工作台~~ — `/workspace`、`/workspace/custom` 纯本地原型（零 API、入口孤岛、保存假成功 stub）→ **不入契约**，处置待 INFERRED-4。
- **共享表单体** — `form.vue`（1501 行，create.vue:6/edit.vue:6 import，**非孤儿**——勘正首批调研）。

## 4. Elements & Interaction

- 列表页：el-tabs 全部/我的；左分类栏（色点+badge 计数，单选 toggle）+ 标签 check-tag 多选面板；KB 卡片（类型徽标 `permission==='private'?'个人':'团队'`、模型/维度、creator/usedSize）；**双搜索框共享同一 keyword**——侧栏「搜索分类」实际过滤的是 KB 名称而非分类列表（index.vue:130,152 同 v-model，:65-67 ‹CERTAIN›，INFERRED-5）。
- 共享设置：部门/指定人 popover 互斥；切换 shareType 清空另一类型；默认部门/自己不可移除（form.vue:419-456 ‹CERTAIN›）。
- 高级配置：图谱开关开时才显 LLM 下拉；元数据字段编辑器（key/显示名/类型 6 种/select 选项逗号拆分/必填/检索 checkbox——region 强制 searchable，:1016-1038,571-584 ‹CERTAIN›）。
- 编辑回填：ACL 含 `userId:'*'` → global；否则非自己条目 → specified（人名反查 personnel）；无 department 分支（INFERRED-3）。

## 5. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| KB 列表 | 页面 v-loading | el-empty | **无 catch**（靠 request.ts 拦截器统一 toast） | index.vue:35-47,115,212 |
| 分类列表/CRUD | v-loading | 表格默认 | 保存失败「操作失败」toast | categories.vue:27-33,71-94 |
| 创建/编辑提交 | 按钮 loading | — | toast「创建/保存失败」，留页 | create.vue:26-61, edit.vue:39-50 |
| form 初始数据 | 页面 loading | — | **Promise.all catch 仅 console.error**（静默） | form.vue:192-194 |
| ACL 回填 hydrateShareFromAcl | — | — | **完全无 try/catch**（getKbAcl 失败冒泡） | form.vue:321-349 |
| 模板/标签联想/模型列表 | — | — | 静默置空 | form.vue:126-128,366-368 |
| 首页四区块 | — | 各自 el-empty | catch 保持空数据 | home/index.vue:37-39,86-130 |

## 6. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| KB 列表 | `getKnowledgeBases` GET `/kb`（api:74-76）；字段 name/description/category/tags/permission/embeddingModel/dimension/creator(Name)/usedSize（index.vue:63,190-207；`res.list` 回退） | TBD — pending API Contract |
| 分类 | `getKbCategories` GET `/kb-categories`（81-83）+ CRUD（84-92）；**`getKnowledgeBaseCategories`（/kb/categories）零消费死导出** | TBD |
| KB 详情/CRUD | `getKnowledgeBaseDetail`/`createKnowledgeBase`/`updateKnowledgeBase`/`deleteKnowledgeBase`（112-126） | TBD |
| ACL | `getKbAcl`/`setKbAcl`（1351-1357）；字段 userId/kbRole | TBD |
| 支撑数据 | `getModels?purpose=EMBEDDING|LLM`、`getPersonnelSimple`、`getOrgTree`、`getDepartmentMembers`、`getAllKbTags`、`fetchParseStrategyTemplates` | TBD |
| 首页聚合 | `getHomeData` GET `/home`：recommendKBs/hotDocs/hotApps/recentActivities（11-13,32-35） | TBD |
| 表单模型 | `KnowledgeBaseForm`（types/knowledge.ts:104-121）：name/category/description/tags/permission/embeddingModel/parseMode/fileTypeConfig/graphAutoBuild/graphLlmModel/customAttrSchema/retrievalConfig | —（前端类型） |

死代码：KB_TYPES 常量（constants.ts:2）全库零引用（INFERRED-6）；KbTag 写端点 createKbTag/updateKbTag/deleteKbTag 无 kb-core 页面消费。

## 7. Responsive & Accessibility

桌面优先无断点（全站一致 [GAP]）；键盘/读屏 [GAP]。

## 8. Acceptance

- [ ] 列表筛选链（tab/关键字/分类/标签）与前端分页 12/页 正确；筛选变化重置页码
- [ ] 创建 → 默认解析策略自动创建（失败不阻断）；ACL 仅 permission 字段提交（INFERRED-2 裁决前维持现状）
- [ ] 编辑 → 嵌入模型锁定；查不到详情 → 重定向列表
- [ ] 共享设置三卡互斥/清空/不可移除项；仅 edit 保存写 ACL；global 写 `userId:'*'`
- [ ] 删除仅 owner/超管可见；确认文案含「无法恢复」
- [ ] 分类：普通用户组织强制本组织；颜色/排序校验；删除有确认
- [ ] 元数据字段编辑器：select 类型才显选项；region 强制 searchable
- [ ] 首页快捷入口按权限过滤；推荐卡可进详情

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 KB 列表服务端筛选/分页参数闲置，是否改为服务端分页？INFERRED-2 创建时 ACL 不落库（仅 permission 字段）是否为既定设计？INFERRED-3 部门共享编辑回填无 department 分支（还原为指定人）是否按缺陷修？INFERRED-4 `/workspace` 两页纯原型：删除 or 接线 or 保留原型？INFERRED-5 双搜索框共享 keyword（侧栏搜索分类实际过滤 KB 名）是否按缺陷修？INFERRED-6 KB_TYPES 死常量与 kb-tag 写端点闲置是否清理？
