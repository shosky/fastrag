# 工作流编排 — Frontend Spec（workflow-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 16 证据包；术语 ← `CONTEXT.md`（工作流）。

## 1. Scope

覆盖业务流管理页（手写画布：节点/连线/拖拽/属性面板）。**排除**：发布/执行/调试/测试用例/监控/模板/迁移/优化——**8 类能力 25 个 API 函数零 UI 消费**（INFERRED-1）；apps 域 workflow-config 2 函数零消费。

## 2. User Tasks

- **TASK-001 业务流选择与创建** — 无 wfId 时引导；选择弹窗（双击/编辑加载画布）；创建（名称必填）；删除 confirm（取消静默吞）（workflow-manage:28-52,315-320 ‹CERTAIN›）。
- **TASK-002 画布编排** — 添加节点（8 类 start/end/llm/kb_retrieval/intent/selector/function_request/sub_workflow，自动找空位防重叠）；节点编辑弹窗（按类型出配置字段：llm 有 model/temperature/maxTokens/systemPrompt/outputFormat 等）；删除节点级联删边；**两击式连线**（查重/自环拦截）；删边（属性面板 ✕ / hover 变红）；拖拽移动（mouseup 单独落位置，失败静默）（:107-174,206-283 ‹CERTAIN›）。
- **TASK-003 保存画布** — 逐节点循环 `updateWorkflowNode`（**单节点失败继续且静默吞错**）+ 边序列化 JSON 存 `updateWorkflow(wfId,{edges})`——**无独立边端点调用**（:84-100,94,97 ‹CERTAIN›；部分失败仍提示「画布已保存」，INFERRED-2）。
- **TASK-004 属性面板** — 只读展示 Key/类型/位置/配置 JSON/入边/出边（:369-392 ‹CERTAIN›）。

## 3. Pages & Routes

唯一页面 `/application/:id/workflow-manage`（hidden，routes.ts:216-220）；**无侧边栏入口**（仅 URL 直达；权限页选项值 `/application/workflow-manage` 无 :id 段与真实路由不匹配；权限表登记的 `/application/workflow` 路径不存在——三处路径不一致，INFERRED-3）。

## 4. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 画布加载 | 页面 v-loading | 无流 el-empty 引导 / 空画布 el-empty | 静默清空 | :67-80,298,316-366 |
| 列表 | — | 列表弹窗 el-empty | 静默置空数组 | :20,417 |
| CRUD/保存/连线 | — | — | try/catch + toast；**逐节点保存吞错** | :35-100 |
| 拖拽落位 | — | — | 失败静默 `catch(()=>{})` | :276-278 |

## 5. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 业务流列表/详情 | getWorkflows/getWorkflowDetail（edges 字段 JSON.parse）（api:861-877） | TBD — pending API Contract |
| 节点 | getWorkflowNodes/updateWorkflowNode/moveWorkflowNode（api:1802-1814） | TBD |
| 保存 | updateWorkflow `{edges: JSON 字符串}`（api:873） | TBD |
| ~~其余 25 函数~~ | **零消费死 API**（publish/execute/test/monitor/template/migration/debug/optimization + add/deleteNode/Edge + nodeConfig）（INFERRED-1） | — |
| 类型 | ~~types/workflow.ts~~ **死类型**（唯一导入方 mock/workflow.ts 亦死代码；两套 NodeType 枚举不一致：页面 8 种 vs 类型 7 种含 plugin）（INFERRED-4） | — |

## 6. Responsive & Accessibility

手写画布（绝对定位 div + SVG 贝塞尔连线，**无第三方画布库**）；桌面优先 [GAP]。

## 7. Acceptance

- [ ] 选择/创建/删除业务流；无流引导
- [ ] 添加节点防重叠；按类型配置字段；删节点级联删边
- [ ] 两击连线查重/自环拦截；删边两方式；拖拽落位
- [ ] 保存（部分失败静默现状）；属性面板只读展示

## 8. INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。要点：术语裁决——文档统一「工作流」（UI 文案「业务流」变更留 backlog）。以下原清单保留作对照。

### 原清单（已被裁决覆盖）

INFERRED-1 25 个死 API（发布/执行/调试/用例/监控/模板/迁移/优化）——UI 路线图 or 清理？INFERRED-2 逐节点保存吞错（部分失败提示已保存）——改批量接口 or 汇报失败数？INFERRED-3 路由/权限三处路径不一致 + 无侧边栏入口——接线 or 废弃？INFERRED-4 types/workflow 死类型与双 NodeType 枚举——清理统一？INFERRED-5 命名三称（工作流/业务流）统一（CONTEXT 术语裁决）？
