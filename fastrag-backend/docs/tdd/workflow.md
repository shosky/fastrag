# 工作流编排（workflow）— 测试设计

> onboard 模式：本文档是测试**设计**，不写测试代码、红绿循环未执行（执行待用户指令，从 Tracer Bullet 起步）。
> 特性现状定性决定切片结构：管理面测**真实行为**（含负向行为固化——先固化缺陷现状再谈修复）；执行引擎测**占位契约**（断言"恒成功回显"），并附引擎实装后的**目标用例族**（节点错误传播 / 循环与条件分支终止性——任务书重点，语义未定故以骨架占位，标注 E-ENGINE 前缀，实装门禁见 contract `workflow.WorkflowExecutionEngine` Invariants）。
> 现有测试基建：AppModuleValidationTest（实体-表映射/服务接口面/占位源码断言 TC-MOCK-002/003）已覆盖部分固化点；WorkflowController/WorkflowServiceImpl 无专门测试类（缺口，本设计补齐对象）。

## 1. 切片规划

| 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 引擎语义裁决 | workflow.WorkflowExecutionEngine | **HITL** | 引擎目标语义（错误传播策略/循环终止规则/节点类型值域）是产品决策，先于 S3 目标用例族定稿。〔决议 2026-09-24：S0 语义已按「最小可行语义」代裁定稿——错误传播=fail-fast 单节点、终止性=拓扑序遍历+防御步数上限（=节点数）+结构校验拒环、超时=单节点 30s/整图 300s、值域=前端 8 类（start/end/llm/kb_retrieval/intent/selector/function_request/sub_workflow）；裁决全文见 spec §4.3 决议与 contract workflow.WorkflowExecutionEngine。E1~E5 可依此填实 Given/Then；引擎实装时若取不同语义须回改本契约（代裁，用户可推翻；原 HITL 门槛经授权代裁解除）〕 |
| S1 生命周期与发布 | （端点排除面，HTTP 层行为） | AFK | CRUD 字段覆盖/发布幂等/删除孤儿/审计注解 |
| S2 画布节点存储 | workflow.CanvasNodeStore | AFK | 节点 CRUD/复合键/唯一键冲突/维度覆盖/序列化形态 |
| S3 执行引擎（占位固化 + 目标骨架） | workflow.WorkflowExecutionEngine | AFK（占位）/ HITL 依赖 S0（目标） | 占位断言固化；E-ENGINE 目标用例族骨架 |
| S4 测试资产与台账面 | （端点排除面） | AFK | 测试用例/模板/优化/迁移 CRUD 与缺省值 |
| S5 权限与审计矩阵 | （跨切面） | AFK | 五权限键 × 端点；@Loggable 覆盖面 |

mock 边界：仅允许 mock 系统边界——fastrag-ai 网关（S3 目标用例：LLM 节点超时/异常）、retrieval RetrievalSearchEngine（S3 目标用例：检索节点失败）、时钟（executionId 随机段断言改注入时钟）。**禁 mock** WorkflowService 内部模块。

## 2. 测试用例

### S1 WorkflowLifecycleModule（contract: 端点排除面 → WorkflowService 基础方法）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建工作流] 未传状态时 [产出 draft 空图] | 合法 form{name,description} | POST /api/workflows | body.code 成功；返回 status=draft、nodes="[]"、edges="[]"；DB 新增一行 | REQ-A01 | 无 |
| [更新工作流] 仅含 name 键时 [画布列不被触碰] | 既有工作流（nodes 含数据） | PUT：body 只带 name | 响应 name 已改；DB nodes/edges 列与更新前逐字节一致 | REQ-A02 | 无 |
| [更新工作流] id 不存在时 [静默返回空体成功] | 不存在 id | PUT | HTTP 200；data=null；无异常 | REQ-A02 | 无 |
| [删除工作流] 已有节点行时 [子节点行残留为孤儿] | 工作流 + 2 个 wf_node | DELETE /{id} | workflow 行消失；wf_node 中 2 行仍存在且 workflow_id 悬空 | REQ-A03 | 无 |
| [发布工作流] 空图无起止节点时 [发布仍成功] | draft 空图工作流 | POST /{id}/publish | 成功；status=published；重复 publish 幂等 | REQ-A04 | 无 |
| [创建工作流] 操作发生时 [审计日志记账] | 任一管理员会话 | POST create | @Loggable 切面产生 operation/workflow_created 记录 | REQ-A05 | 无 |

### S2 CanvasNodeStore（contract: workflow.CanvasNodeStore）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [添加节点] nodeType 为任意串时 [照常落库默认启用] | 工作流 id | POST nodes：type="not_a_type" | 成功；DB 行 enabled=1、坐标 0 | REQ-B01 | 无 |
| [添加节点] nodeKey 与既有重复时 [落库失败无业务码] | 工作流已有 key="n1" | POST nodes：key="n1" | 业务错误（兜底「服务异常」形态）；DB 仍 1 行 | REQ-B01（E_NODE_KEY_DUPLICATE） | 无 |
| [保存节点配置] 先 conditions 后 delays 时 [后者整体冲掉前者] | 节点已存 conditions 配置 | PUT nodes/{key}/delays | GET properties 与 GET conditions 均**返回 delays 内容**——dimension 仅 API 形状 | REQ-B04 | 无 |
| [保存节点配置] 任意维度保存后 [config 为 toString 形态非 JSON] | 节点存在 | PUT properties：{"a":1} | DB config 列值为 `{a=1}`；GET 返回该原串 | REQ-B05 | 无 |
| [保存节点配置] 节点不存在时 [仍报 saved:true 假成功] | 不存在的 nodeKey | PUT properties | 响应 saved=true；DB 无写入 | REQ-B04（假成功债务） | 无 |
| [移动节点] 坐标缺省时 [归零不抛错] | 既有节点 | PUT position：body {} | 成功；x=y=0 | REQ-B02 | 无 |
| [列出节点] 多节点时 [按创建序升序返回] | 3 个节点 | GET nodes | 顺序与 createdAt 升序一致 | REQ-B02 | 无 |

### S3 WorkflowExecutionEngine（contract: workflow.WorkflowExecutionEngine）

**占位固化用例（现状真实行为，引擎实装时必须翻红）：**

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [执行工作流] 不存在的工作流 id 时 [仍返回完成态] | id="ghost" | POST execute | status=completed、output=执行完成、executionId 前缀 exe_；无异常 | REQ-C01/C03 | 无 |
| [执行工作流] 任意执行时 [不产生模型与检索调用、无持久化痕迹] | 任意已发布工作流 | POST execute | fastrag-ai/retrieval 零交互（依赖注入探针）；wf_debug_log/wf_monitor_data 零写入 | REQ-C01/C05 | 依赖探针 |
| [试运行节点] 不存在的 nodeKey 时 [原样回显完成态] | nodeKey="ghost_node" | POST nodes/{key}/test | status=completed；nodeKey 回显 | REQ-C02 | 无 |
| [保存调试配置] 保存后 [再次读取不可见] | 任意工作流 | POST debug 后 GET debug | GET 返回不含已保存配置（硬编码三行日志）；无 DB 写入 | REQ-C04 | 无 |
| [查询监控] 任意工作流时 [指标恒为硬编码值] | 任意 id | GET monitor | totalExecutions=3200、avgLatency=1200、errorRate=0.02 | REQ-F03 | 无 |

**E-ENGINE 目标用例族（骨架——语义裁决 S0 后填 Given/Then，当前为实装门禁占位）：**

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| E1 [执行工作流] LLM 节点抛出异常时 [按 S0 裁决的传播策略产出终态] | 含 llm 节点的图 | POST execute | （已裁 2026-09-24：fail-fast——整图 failed + 失败节点记账）；wf_debug_log 必须有该节点 error 行 | REQ-C01（已裁语义） | fastrag-ai 网关抛异常 |
| E2 [执行工作流] 条件节点两分支路由时 [仅命中分支被执行] | 含 condition 节点双分支图 | POST execute（inputs 命中 A 支） | A 支节点有执行记账、B 支零记账；终态 completed | REQ-C01（GAP 项） | 无 |
| E3 [执行工作流] 图含回边成环时 [按 S0 裁决的终止规则收敛] | 成环图 | POST execute | （已裁 2026-09-24：结构校验期拒绝——CanvasStructureValidator 对 publish/execute 前置硬失败；执行期防御步数上限=节点数兜底）；断言**有限步内返回终态、不死循环** | REQ-C01（已裁语义） | 无 |
| E4 [执行工作流] 检索节点失败时 [按降级策略续跑或传播] | kb_retrieval 节点 + 下游 llm | POST execute | （已裁 2026-09-24：检索通道降级不是节点失败——与 `## retrieval` 契约「任何通道失败不是错误模式」对齐，返回空/部分结果节点正常完成；检索面**抛异常**（基础设施故障）才触发 fail-fast 中断并记账） | REQ-C01（已裁语义） | RetrievalSearchEngine 抛异常 |
| E5 [执行工作流] LLM 节点执行时 [按 ModelRegistry 口径路由模型] | llm 节点 config 指定 code | POST execute | 解析 `code=? AND status='online' LIMIT 1`；（已裁 2026-09-24）offline/无模型视为节点失败，按 fail-fast 传播 | REQ-C01（缝 2 登记） | fastrag-ai 网关 |

### S4 测试资产与台账面（contract: 端点排除面）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建测试用例] 未传归属时 [强制绑当前工作流] | 工作流 id | POST test-cases | DB 行 workflow_id=路径 id | REQ-D01 | 无 |
| [删除测试用例] 跨工作流 tcId 时 [删除成功] | A 工作流用例 tcId | DELETE /workflows/B/test-cases/{tcId} | 成功且 A 的用例被删（无归属校验现状固化） | REQ-D02 | 无 |
| [创建模板] 未传 is_builtin 时 [缺省 0] | 合法模板 | POST templates | DB is_builtin=0 | REQ-E01 | 无 |
| [删除模板] is_builtin=1 时 [删除成功无保护] | 内置模板 | DELETE templates/{id} | 成功且行消失 | REQ-E01 | 无 |
| [创建优化建议] 未传 status 时 [缺省 pending；apply 后翻 applied 且不改工作流] | 工作流 + 建议 | POST optimizations + POST apply | status→applied；workflow.nodes/edges 不变 | REQ-F01 | 无 |
| [创建迁移] 未传 status/progress 时 [缺省 running/0 且永不推进] | 迁移记录 | POST migrations 后再 GET | status 仍 running、progress 仍 0 | REQ-E03 | 无 |

### S5 权限与审计矩阵（contract: 跨切面）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [访问工作流] 无 workflow:edit 时 [读端点被拒] | 只读用户 | GET /api/workflows | 403 语义（读端点要求 edit 权限的现状固化） | REQ-G01 | 无 |
| [执行工作流] 无 workflow:run 时 [执行被拒] | 缺 run 权限用户 | POST execute | 403 语义 | REQ-G01 | 无 |
| [访问工作流] 无 KB 归属校验时 [跨知识库用户不受 KB ACL 影响] | 无任何 kb_acl 的用户（有 workflow:edit） | GET /api/workflows | 正常访问（无 @KbAuth 现状固化） | REQ-G03 | 无 |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| S2/维度冲掉 | workflow.CanvasNodeStore.saveNodeConfig | CanvasNodeModule | REQ-B04 |
| S2/toString 形态 | workflow.CanvasNodeStore.saveNodeConfig | CanvasNodeModule | REQ-B05 |
| S3/占位固化 | workflow.WorkflowExecutionEngine.execute | WorkflowExecutionEngine | REQ-C01、REQ-C05 |
| E3/成环终止 | workflow.WorkflowExecutionEngine.execute | WorkflowExecutionEngine + 缝 1 | REQ-C01（GAP） |
| S1/删除孤儿 | （端点排除面）delete | WorkflowLifecycleModule | REQ-A03 |
| S4/内置模板可删 | （端点排除面）deleteTemplate | WfAssetModule | REQ-E01 |
| S5/读需 edit | （跨切面）@PreAuthorize | WorkflowLifecycleModule | REQ-G01 |

## 4. 待实施说明
- 孤儿 REQ 豁免：REQ-C03（execute 入参与结果无契约约束——任意 Map 传入、固定三键返回的现状断言，占位/债务登记，无下游承接为有意；见 implement 文档）
- 孤儿 REQ 豁免：REQ-E02（模板仅存画布数据，无「模板→工作流+节点行」展开逻辑的现状断言，占位/债务登记，无下游承接为有意；见 implement 文档）
- 孤儿 REQ 豁免：REQ-F02（优化建议无自动分析生成入口、仅能手工创建的现状断言，占位/债务登记，无下游承接为有意；见 implement 文档）
- 孤儿 REQ 豁免：REQ-G02（data.sql 播种 api:workflow:* 九键与控制器 workflow:* 键不同名且无消费方——噪声登记，占位/债务登记，无下游承接为有意；见 implement 文档）

- 红绿循环**未执行**；实施时从 Tracer Bullet 起步：建议首条 = S2/[保存节点配置] 先 conditions 后 delays 冲掉用例（最高信息密度：一次验证路由/持久化/序列化三件事）。
- S3 占位固化用例与现有 AppModuleValidationTest TC-MOCK-002/003（源码级断言）形成双层防线；引擎实装时：源码断言删除、接口断言翻红替换为 E1~E5。
- AppModuleValidationTest 的实体-表/schema-h2 断言继续作为数据面回归网（data.md `## workflow` 漂移登记修复时同步修 TC-SCHEMA 断言）。
- E1~E5 的 Given/Then 依赖 S0 裁决（错误传播策略/终止规则/节点值域）——S0 已于 2026-09-24 代裁定稿（见 S0 行决议与 spec §4.3），E 族可填实实施；引擎实装取不同语义时回改本契约。
