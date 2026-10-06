# 工具/技能/MCP/外部数据库 — Frontend Spec（tools-skills-mcp-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 13 证据包；术语 ← `CONTEXT.md`（工具/MCP 服务/技能）。

## 1. Scope

覆盖 HTTP 工具管理、MCP 服务管理、文件化技能管理、外部数据库管理（含 SQL 工作台）。**排除**：应用侧五类绑定面板（app-center 链）；mock 层类型/常量的治理（INFERRED-5 悬置）。

## 2. User Tasks

- **TASK-001 HTTP 工具管理** — my-tools 列表（前端关键词/类型过滤）+ 启停 toggle + 删除 confirm + 新建/编辑跳路由；ToolForm 三区（信息/请求配置/出入参 Schema 双文本域）；**cURL 导入解析器**；**Body 中 `${xxx}`/`{{xxx}}` 自动提取变量生成入参**（{{}} 归一化）；类型选项过滤掉 knowledge（my-tools:14-66; ToolForm:56-82,153-155,237-432,566-595 ‹CERTAIN›）。
- **TASK-002 工具测试** — ToolTestDialog：参数表从 inputSchema→inputs→Body 变量三级合成；场景模板存 localStorage（key=`tool_test_scenarios_${url}`）；`runTest` 组装后经**后端代理 `testProxy`** 发送（CORS 规避）；历史 50 条内存；结果复制/导出 JSON/Markdown 报告。**高危 BUG：`resp` 未定义（实为 proxyResp）→ 每次成功响应抛 ReferenceError 进 catch，把结果覆盖为 status:0 'Error'——工具测试成功也显示失败**（ToolTestDialog:76-125,144-183,236-342,323 ‹CERTAIN›，INFERRED-1）。
- **TASK-003 MCP 管理** — 列表（normalizeService：Integer→boolean、Schema Map→参数数组；名称/URL/工具名搜索 + online/offline 过滤；开关；删除仅非内置）；表单双 Tab + **「解析」parseMcpUrl（transport 固定 'sse'）发现工具回填并跳 tools Tab**；detail 三 Tab（工具/基础信息/调用日志）+ **「刷新」重连发现** + **单工具测试弹窗**（参数恒 el-input，number 也传字符串，INFERRED-6）；内置服务禁删（mcp-services:14-100,167; McpForm:81-142; detail:21-140,279-332 ‹CERTAIN›）。
- **TASK-004 技能管理** — 列表（分类过滤 tool/model/mcp/skill）+ 启停（absolute）+ 删除 + **上传技能包（.zip/.tar.gz/.skill → prepareSkillImport 一次性「上传→建草稿→确认安装→清理」）**；SkillCreate 三 Tab（基础信息/代码管理/依赖管理）+ **内存虚拟文件树**（SKILL.md 不可删；删除仅内存未上送）+ 提交三步（createSkill(slug 归一化) → 循环 createSkillFile → getSkills 按 slug 找 id → updateSkillDependencies）；SkillDetail：文件树浏览/编辑保存（路径由 el-tree parent 链拼装）/生效范围 toggle+shareConfig/导出 zip/依赖管理/删除。**高危 BUG：SkillCreate `form` 为 reactive 但提交全程用 `form.value.name` → TypeError，新建技能主流程不可用**（skill-management:12-109; SkillCreate:13,50-91,130-140,179-234; SkillDetail:40-186 ‹CERTAIN›，INFERRED-2）。**无技能编辑路由**（编辑=详情；skill/create.vue、edit.vue、SkillForm.vue 三文件孤儿）。
- **TASK-005 外部数据库** — CRUD（类型切换自动填端口）+ 测试连接（connected→成功 latencyMs，本地改 status）+ 表浏览/同步（报告 syncedCount；columns JSON 解析列明细）+ **SQL 工作台**：textarea 输入 SQL → `queryDatabase {sql}` → 结果表格（columns/rows/rowCount/elapsedMs）；placeholder 声明仅 SELECT/SHOW/DESCRIBE/EXPLAIN、只读开关仅 UI 提示——**前端无任何 SQL 校验，约束完全依赖后端**（database-management:40-365,506-509,546-623 ‹CERTAIN›；INFERRED-3）。

## 3. Pages & Routes

11 路由全接 Sidebar「应用」组（135-138）；技能无编辑路由（:52-54 编辑=详情）。

## 4. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 四列表 | 各页 v-loading | el-empty ×5 | toast；取消类 catch{} 吞 | 各页 |
| 工具测试 | testingId | — | **成功也报错（INFERRED-1）** | ToolTestDialog:287-342 |
| MCP 解析/刷新/单工具测试 | 按钮 loading | — | toast | McpForm:81-118; detail:114-332 |
| 技能提交 | saving | — | **reactive/value 崩溃（INFERRED-2）** | SkillCreate:179-234 |
| DB 测试连接/SQL | testingId/sqlLoading | — | error 字段展示 | db-mgmt:226-365 |

## 5. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 工具 | tools 组 7 消费（api:905-938） | TBD — pending API Contract |
| 技能 | skills 组 20 端点（15 消费）（api:944-1035） | TBD |
| MCP | mcp 组 9 消费（api:1041-1082） | TBD |
| 数据库 | databases 组 8 消费（api:1855-1884） | TBD |
| 测试代理 | POST `/tools/test-proxy {method,url,headers,body,timeout}` | TBD |
| SQL 查询 | POST `/databases/{id}/query {sql}` → `{columns,rows,rowCount,elapsedMs,error}` | TBD |
| mock 依赖 | 常量/类型（TOOL_CATEGORIES/AUTH_TYPE_OPTIONS/getStatusLabel/Tool/McpService/Skill 等，来自 `@/mock/tools|mcp|skills`）；**mock 内 CRUD 函数已死**（INFERRED-5） | — |
| ~~死导出~~ | getToolApiConfig/saveToolApiConfig、getSkillDependencies/confirmSkillDraft/discardSkillDraft、getMcpServiceTools、getDatabase/createDatabaseTable、getSkillDetail/updateSkill（仅孤儿 edit 用）、deleteSkillFile（无 UI）（INFERRED-4） | — |

## 6. Responsive & Accessibility

桌面优先 [GAP]。

## 7. Acceptance

- [ ] 工具 CRUD/启停/cURL 导入/变量提取；测试现状=成功也报错（INFERRED-1 裁决前维持）
- [ ] MCP 列表过滤/解析回填/刷新重连/单工具测试/内置禁删
- [ ] 技能列表/上传安装/新建（现状崩溃 INFERRED-2）/详情文件树编辑/导出/依赖
- [ ] DB CRUD/测试连接/表同步/SQL 工作台（前端无校验现状）

## 8. INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。要点：双高危 BUG 已修复。以下原清单保留作对照。

### 原清单（已被裁决覆盖）

INFERRED-1 **ToolTestDialog `resp` 未定义 BUG（测试成功必显失败）——修**？INFERRED-2 **SkillCreate reactive/value 崩溃（新建技能不可用）——修**？INFERRED-3 SQL 工作台前端零校验——加前端防线 or 信任后端（只读开关仅提示）？INFERRED-4 死导出清理（×9）+ deleteSkillFile 无 UI + 草稿 confirm/discard 半截流？INFERRED-5 mock 常量/类型依赖（×3 文件）解耦到 types？INFERRED-6 MCP 单工具测试参数恒字符串——按类型转？INFERRED-7 技能编辑路由缺失（编辑=详情）——补 or 维持？INFERRED-8 测试场景 localStorage 以 URL 为 key（变量 URL 易错配）——改 id？
