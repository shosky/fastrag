# 工具/技能/MCP/外部数据库 — Frontend Architecture Design（tools-skills-mcp-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/tools-skills-mcp-frontend.md`。

## 1. Page Map

| PAGE | 路由 | 侧边栏 |
|---|---|---|
| my-tools（+create/:id/edit） | `/application/my-tools*` | :135 ✅ |
| mcp-management（+create/:id/:id/edit） | `/application/mcp-management*` | :136 ✅ |
| skill-management（+create/:slug） | `/application/skill-management*` | :137 ✅（无编辑路由） |
| database-management | `/application/database-management` | :138 ✅ |

## 2. Page/Layout/Component Map

```
my-tools: 工具表格(类型/启停开关/操作) + 前端过滤
tool/create|edit: ToolForm 三区(信息/请求配置+cURL 导入/出入参 Schema)
  测试: ToolTestDialog(参数表+场景 localStorage+代理执行+历史 50+导出)
mcp-services: 服务卡表格(状态点/开关/删除仅非内置) + 过滤
mcp/create|edit: McpForm(基础信息+工具列表双 Tab;「解析」发现工具)
mcp/detail: 元信息卡 + 三 Tab(工具/基础/调用日志) + 刷新 + 单工具测试弹窗
skill-management: 技能表格(分类过滤/启停/上传技能包)
SkillCreate: 三 Tab(基础/代码=虚拟文件树/依赖) 
SkillDetail: 文件树浏览编辑 + 生效范围 + 导出 + 依赖
database-management: 实例表格(类型色点/连接状态/测试连接) + 表浏览(结构明细/同步) 
  + SQL 工作台弹窗(textarea→结果表格 columns/rows/rowCount/elapsedMs)
```

组件判定：ToolForm/McpForm/SkillForm 形态各异（SkillForm 系孤儿）；ToolTestDialog 单消费者但行为深度高（成立）。

## 3. State Ownership

| STATE | 所有者 | 说明 |
|---|---|---|
| 四列表 | server → 页内 ref（工具/MCP 前端过滤） | — |
| 工具表单变量 | form（Body `${}` 提取生成入参） | 双向：schemaToInputs/inputsToSchema |
| 测试场景 | client localStorage（URL 为 key，INFERRED-8） | 历史 50 条内存 |
| 技能虚拟文件树 | client 内存（SkillCreate；删除不上送 INFERRED-4） | 提交时批量 createSkillFile |
| 技能文件编辑 | server（getSkillFile/updateSkillFile） | 路径=el-tree parent 链拼装 |
| DB 连接状态 | client 本地改（测试后） | — |

## 4. Data Flow

```
tools: ToolForm 变量提取 → ToolTestDialog 三级参数合成 → testProxy 代理 → 结果渲染
mcp: parseMcpUrl(transport 固定 sse) → toolsList 回填（Schema Map→数组 normalize）
     refresh → 重连+发现 → getMcpServiceDetail
skills: prepareSkillImport 一次四步（上传→草稿→确认→清理）；创建三步（create→files→dependencies）
db: queryDatabase {sql} → {columns,rows,rowCount,elapsedMs,error}
命名转换点: normalizeService（Integer→boolean/Map→数组）；extractVarsFromBody；buildTree
```

## 5. Visual System

类型色点/连接状态标签；MCP 状态点 online/offline；SQL 结果表格。断点无。

## 6. Parent Trace

- testProxy 代理（CORS 规避） → spec TASK-002
- prepareSkillImport 一次四步 vs 草稿 confirm/discard 端点闲置 → spec INFERRED-4
- SQL 工作台前端零校验 → spec INFERRED-3
- mock 常量/类型依赖 → spec INFERRED-5
