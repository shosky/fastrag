# 工作流编排（workflow）— 架构设计

> 特性现状定性：管理面（CRUD/节点编排/测试资产/模板/迁移/优化）为真实落库实现；执行引擎与调试/监控为**占位实现**（WorkflowServiceImpl.java:28-29,40-47,63；被 AppModuleValidationTest TC-MOCK-002/003 固化）。本设计对既有模块如实测绘，对执行引擎给出「现状 → 目标形态」的深化路径（任务书点名深模块候选：节点执行路由、画布结构校验）。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **WorkflowLifecycleModule** 工作流生命周期 | 工作流定义（元信息+画布序列化）的 CRUD 与发布状态机 | WorkflowServiceImpl.java:13-18、WorkflowMapper.java、entity/Workflow.java | 中模块：吞掉了「字段选择覆盖」「draft→published 状态位」语义；但 delete 不级联、publish 无校验把画布一致性的复杂度**泄漏**给了数据（孤儿节点，见 §4 深化项 1）。|
| **CanvasNodeModule** 画布节点编排 | 节点行 CRUD + 位置 + 节点扩展配置的维度化读写 | WorkflowServiceImpl.java:20-26,56-61、WfNodeMapper.java、entity/WfNode.java、WorkflowController.java:51-77 | 中模块：吞掉了 (workflowId,nodeKey) 定位与 config 单列折叠；但把「9 维度→1 列」「Map.toString() 非 JSON」两个实现细节**暴露**在数据契约里（spec REQ-B04/B05），是浅化的中模块。|
| **WorkflowExecutionEngine** 执行引擎 | 按画布边拓扑驱动节点、路由到节点执行器、产出执行结果 | WorkflowServiceImpl.java:28-29（占位）、WorkflowController.java:77,80 | **深模块候选（目标）**：节点类型差异、错误传播、循环终止、变量传递本应全部藏在 execute 两个方法之后——现状零实现，是全特性最大的深化空间（§4 深化项 3）。|
| **CanvasStructureValidator** 画布结构校验 | 图结构合法性（起止节点/连通/无环/悬挂边）判定 | 无（缺失） | **深模块候选（目标）**：publish 与 execute 的前置闸门；现状 publish 直通（REQ-A04），校验规则无处安放（§4 深化项 2）。|
| **WfAssetModule** 测试资产面（测试用例/模板/优化/迁移）**(S)** | 四类辅助资产的 CRUD 台账 | WorkflowServiceImpl.java:31-54、WfTestCaseMapper/WfTemplateMapper/WfOptimizationMapper/WfMigrationMapper.java | 浅模块：无业务规则（唯一规则是两个缺省值），建议保持扁平、不再为其扩契约；若引擎实装，测试用例执行比对（spec REQ-D03 缺口）可升格为独立模块。|
| **WfObservabilityStub** 调试/监控占位面 **(S)** | 调试日志查看/调试配置、监控指标的占位回显 | WorkflowServiceImpl.java:40-47,63 | 浅模块（占位）：硬编码回显，无状态；引擎实装后应吞入 ExecutionEngine 的日志/指标产出（wf_debug_log/wf_monitor_data 两张死表的激活点）。|

模块命名说明：代码中不存在按上述名字命名的类——模块是对 WorkflowService 单接口 27 个方法（WorkflowService.java:5-36）按职责的分组测绘；单接口上帝面本身是 §4 深化项 4 的对象。

## 2. Interface Contracts

公共接口（HTTP 暴露面，完整清单 WorkflowController.java:35-124，32 端点；契约文件只立深模块接口，见 contract/interfaces.md `## workflow` 节的端点排除说明）：

**WorkflowLifecycleModule**（→ 契约 workflow.WorkflowExecutionEngine 的数据前提，自身不立契约）
- `list() / get(id)` — 全量列表 / 详情
- `create(form{name,description})` — 建 draft 空图工作流
- `update(id, form{name?,nodes?,edges?})` — 字段选择覆盖画布与元信息
- `delete(id)` — 物理删（无级联）
- `publish(id)` — draft→published 状态位翻转
- Hidden：状态值域（draft/published 二值）、ASSIGN_ID 主键生成

**CanvasNodeModule**（→ contract `workflow.CanvasNodeStore`）
- `addNode(wfId,nodeKey,nodeType,name,x,y)` / `updateNode(wfId,nodeKey,WfNode)` / `deleteNode(wfId,nodeKey)` / `moveNode(wfId,nodeKey,x,y)` / `listNodes(wfId)`
- `getNodeConfig(wfId,nodeKey)` / `saveNodeConfig(wfId,nodeKey,dimension,config)` — 维度化配置读写（单列折叠）
- Hidden：(workflowId,nodeKey) 复合定位、config 的 Map.toString() 序列化、dimension 不参与存储、DB 唯一索引兜底 key 冲突

**WorkflowExecutionEngine**（→ contract `workflow.WorkflowExecutionEngine`，现状占位契约）
- `execute(wfId, inputs)` — 整图执行
- `executeNode(wfId,nodeKey,inputs)` — 单节点试运行
- Hidden（目标形态）：节点执行器路由、上下文/变量传递、错误传播策略、执行记账
- 现状 Hidden 内容为空——两个方法各自一行的硬编码返回

**WfAssetModule / WfObservabilityStub**（不立契约，端点排除）
- listTestCases/createTestCase/deleteTestCase；templates 四操作；optimizations 三操作；migrations 两操作；getDebugInfo/saveDebugConfig；getMonitorData
- Depth rationale：简单 CRUD/透传包装，删除实现不改变产品能力边界（占位面在引擎实装前本就无能力）

## 3. Seam Diagram

**缝 1：节点执行器路由（NodeExecutor Router）— 规划缝（当前不成立）**
- What varies：节点类型 → 执行语义（llm/kb_retrieval/rerank/template/code/condition/start/end…）
- Adapters：**0 个**——引擎未实装，无任何执行器适配器；按「2+ 适配器才立真缝」判据，这是**假设缝**：种子数据已出现 start/intent/kb_retrieval/llm/end 五类（data.sql:567-573）、CONTEXT.md 术语表列出七类节点，类型可变是明确预期，但当前无第二个实例证明缝的接口形状
- Stable side：WorkflowExecutionEngine.execute 的统一执行结果结构
- Placement：引擎内部注册表（类型→执行器 Map），不放控制器层

**缝 2：模型网关（fastrag-ai）— stable side 已就绪，消费侧缺失**
- What varies：LLM/Embedding/Rerank 的具体模型与供应商
- Adapters：fastrag-ai 网关族（Ollama 兼容 WebClient，全仓多消费链共用）；工作流侧适配器 = 目标 LLM 节点执行器（缺）
- Stable side：`## platform-config` ModelRegistry 契约的 code+status='online' 路由口径（interfaces.md:1070-1096）
- Placement：LLM 节点执行器内解析模型记录，模型地址/密钥不进 workflow 模块

**缝 3：检索调用面（retrieval）— stable side 已就绪，消费侧缺失**
- What varies：kb_retrieval 节点的检索配置（模式/通道/重排…）
- Adapters：`## retrieval` RetrievalSearchEngine（search(request)→items，interfaces.md:740-762）；工作流侧适配器 = 目标检索节点执行器（缺）
- Stable side：RetrievalRequest/RetrievalConfig 全字段契约
- Placement：检索节点执行器内组装 RetrievalRequest，检索配置存节点 config

**缝 4：应用触发（app-center → workflow）— 登记缝（消费方未接线）**
- What varies：触发来源（应用触发器/对话/手动 execute）
- Adapters：app_trigger 的 action_type='workflow' 登记（data.sql:557）——**触发时不调用 WorkflowService.execute**（AppConfigServiceImpl.java:80-84 只回写触发台账）；app_basic_config.advancedOptions.workflow 段读写（AppConfigServiceImpl.java:254-270）
- Stable side：WorkflowExecutionEngine.execute 签名
- Placement：消费接线归 app-center 链，本链只保证 execute 的可调用性

## 4. Deepening Opportunities

1. **WorkflowLifecycleModule 吞掉子资源一致性**：delete 级联清理 wf_node/测试用例（消除孤儿数据）；publish 前置结构校验调用 CanvasStructureValidator。当前把一致性责任泄漏给了数据库脏数据。
2. **CanvasStructureValidator（新深模块）**：吞掉「图合法性」全部规则——必备 start/end、可达性、无环、悬挂边、边引用的节点存在；一次实现，publish 闸门与 execute 前置检查复用。〔决议 2026-09-24：校验强度取**双闸门均硬失败**（publish 拒绝 + execute 前置拒绝）——与错误传播 fail-fast（spec §4.3 决议）同源，警告模式会产出半成品发布、无消费价值；规则清单 = 上述五项全数硬失败。现状差距：Validator 未实现（本深化项即债务登记）（代裁，用户可推翻）〕
3. **WorkflowExecutionEngine（最大深化空间）**：把「节点遍历拓扑、类型→执行器路由、上下文变量传递、错误传播策略、循环/条件终止性保障、执行日志/指标落 wf_debug_log/wf_monitor_data（激活两张死表）」全部藏进 execute 之后；同时消灭占位回显（debug/monitor 硬编码）。引擎接口形状已被占位契约锁定（execute/executeNode 两方法），深化不破坏调用方。
4. **拆分上帝接口 WorkflowService**：27 个方法单接口/单实现 64 行密排（WorkflowServiceImpl.java）——按 Module Map 五模块拆分接口；管理面与执行面分离后，占位实现可被替换而不触碰 CRUD。
5. **CanvasNodeModule 收敛配置序列化**：Map.toString() → JSON（结构化可解析）；dimension 分维存储（同列 JSON 分键或独立列）——把「维度」从 API 形状变成真实存储语义（修复 REQ-B04 覆盖冲掉问题）。
6. 无其余深化项——WfAssetModule 保持浅即可，台账类复杂度不值得隐藏。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| WorkflowLifecycleModule | REQ-A01~A05、REQ-G01（权限面） |
| CanvasNodeModule | REQ-B01~B06 |
| WorkflowExecutionEngine（含 WfObservabilityStub 占位面） | REQ-C01~C05、REQ-F03、REQ-F04 |
| CanvasStructureValidator（缺失/候选） | §2 Out of Scope 结构约束项（现状无对应 REQ，实装时补） |
| WfAssetModule | REQ-D01~D03、REQ-E01~E03、REQ-F01~F02 |
| 缝 2（模型网关） | platform-config `## ModelRegistry`（消费登记，实装时挂 REQ-C01 的 LLM 节点） |
| 缝 3（检索面） | retrieval `## RetrievalSearchEngine`（消费登记，实装时挂 REQ-C01 的检索节点） |
| 缝 4（应用触发） | app-center 消费点（登记，Out of Scope） |
| CanvasNodeModule | REQ-B06（2026-09-24 review 补锚：节点增/改/删/移各留一条 workflow_node_added/updated/deleted/moved 审计日志） |

**ADR 要点核对**：ADR-0001（解析策略绑定变更必须触发重新分片）——不适用（工作流无绑定-产物一致性语义）；ADR-0002（图存储收敛 Neo4j）——不适用（工作流「图」是画布元数据存 MySQL JSON 列，非知识图谱）。无重大新取舍需立 ADR：双轨存储（workflow.nodes/edges JSON ↔ wf_node 行）是现状事实而非显式权衡记录，其收敛方向记于 §4 深化项 1/5，不另立 ADR。〔决议 2026-09-24：按现状为准——双轨并存且无同步约束是既成事实（意图不可考，无注释/测试佐证任何一方）；语义代裁：workflow.nodes/edges JSON = 画布整图快照（前端序列化形态），wf_node 行 = 结构化权威源（可查询、唯一索引约束）；两轨互不同步属债务，收敛方向以深化项 1/5 为准（代裁，用户可推翻）〕

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
