# 工具/技能/MCP/外部数据库 — Implementation Document（tools-skills-mcp-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/application/
├── my-tools.vue(312) tool/{create 61, edit 106, ToolForm, ToolTestDialog}
├── mcp-services.vue(341) mcp/{create 48, detail 542, edit 114, McpForm}
├── skill-management.vue(396) skill/{SkillCreate 619, SkillDetail 412}
│   └── ~~skill/{create,edit,SkillForm}.vue~~ 孤儿三件（SkillForm 包装壳）
└── database-management.vue(924)
src/api/index.ts — tools/skills/mcp/databases 四组（消费 39/死导出 9）
```

## 2. Interface Implementations

### 工具变量提取与测试

**Logic Flow:**
```
extractVarsFromBody(body): 正则提取 ${xxx} 与 {{xxx}}（归一化）→ 生成入参行
runTest:
1. url = replaceVars(config.url, params)；query 拼接；body = buildRequestBody(模板, params)
2. headers += 鉴权（X-API-Key | Authorization: Bearer）
3. proxyResp = POST /tools/test-proxy {method,url,headers,body,timeout}
4. IF resp.status >= 400 → …   // ★ BUG：resp 未定义（实为 proxyResp）→ ReferenceError
   → catch 把结果覆盖为 {status:0, statusText:'Error'}  // 成功也显示失败（INFERRED-1）
5. 历史 unshift（上限 50）
```

### 技能创建三步

**Logic Flow:**
```
handleSubmit:   // ★ form 为 reactive，代码却用 form.value.* → undefined → TypeError（INFERRED-2）
1. createSkill({...SKILL.md content, codeType:'markdown', slug 归一化})
2. FOR virtualFiles: createSkillFile(slug, path, isDir, content)   // 删除操作未上送
3. created = getSkills().find(slug) → updateSkillDependencies(created.id, deps)
```

### MCP 归一与发现

```
normalizeService: status Integer→boolean(online)；toolsSchema Map→参数数组
parseMcpUrl: POST {mcpUrl, transport:'sse', authType, authValue} → 工具回填 + 切 tools Tab
refresh: POST refresh（重连+发现）→ getMcpServiceDetail
单工具测试: testMcpTool(toolId, form)（参数恒字符串）
```

### SQL 工作台

```
执行: queryDatabase(id, sql) → {columns, rows, rowCount, elapsedMs, error} → 结果表格
前端零校验（placeholder 声明 + 只读开关仅提示——约束依赖后端）
```

### 页面登记表

| 能力 | 要点 | 证据 |
|---|---|---|
| cURL 导入 | parseCurl 解析进表单 | ToolForm:237-432 |
| 测试场景 | localStorage key=`tool_test_scenarios_${url}` | ToolTestDialog:144-183 |
| 技能上传 | prepareSkillImport 一次四步（草稿 confirm/discard 端点闲置） | skill-management:91-109 |
| DB 表同步 | syncedCount 报告 | db-mgmt:269-314 |

## 3. Decisions & Deviations

- **双高危 BUG（INFERRED-1/2）**：工具测试假失败、技能创建崩溃——特征化记录，修复待裁决。
- **mock 依赖面（INFERRED-5）**：三文件常量/类型被引用（CRUD 函数全死）——解耦候选。
- **技能编辑缺失**（INFERRED-7）：无 edit 路由，孤儿三件（create/edit/SkillForm）待处置。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：extractVarsFromBody、cURL 解析器、normalizeService、技能树路径拼装（均为纯函数，最易测）。

## 5. Implementation Trace

```
TASK-001 → 工具管理+表单 → my-tools + ToolForm
TASK-002 → 测试对话框 → ToolTestDialog:76-419
TASK-003 → MCP 全链路 → mcp-services + McpForm + detail
TASK-004 → 技能全链路 → skill-management + SkillCreate/SkillDetail
TASK-005 → 数据库+SQL 工作台 → database-management:40-623
```
