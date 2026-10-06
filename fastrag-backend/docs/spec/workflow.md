# 工作流编排（workflow）— 需求规格

## 1. 特性与范围

工作流（Workflow）是应用内的可视化编排图：用户在画布上放置节点（开始/意图/知识库检索/LLM/结束等类型）、连线成图，保存为可发布、可执行、可测试、可监控的自动化业务流。本特性覆盖工作流全生命周期管理：CRUD 与发布、画布节点编排（增删改移 + 维度化节点配置）、执行与调试、测试用例、配置模板、优化建议、迁移记录、监控数据。显式排除：应用中心对工作流的消费（app.type/app_trigger/advancedOptions.workflow 段归 app-center 链）；对话运行的会话管理；智能体（Agent）运行时；BPM 流程引擎（`fastrag-bpm` 已核实为死目录，见 implement/workflow.md §3）。

**现状定性（必须先读）**：本特性的**管理面（CRUD/节点/模板/测试用例/优化/迁移）为真实落库实现**，而**执行引擎与调试/监控面为占位实现**——`execute`/`executeNode` 返回硬编码完成态，不遍历节点、不调用任何模型或检索服务（WorkflowServiceImpl.java:28-29；该占位行为已被 AppModuleValidationTest TC-MOCK-002 固化为断言，:318-324）。本 spec 对两部分分别如实陈述：管理面写行为需求，执行面写「占位契约现状 + 目标语义缺口」。

## 2. Out of Scope

- **工作流执行引擎的真实节点语义**（LLM 节点如何选模型、检索节点如何传参、变量如何在节点间传递、条件分支路由、循环终止）——代码中无任何实现与值域约束，属执行引擎实装范围。**值域事实源（2026-09-24 补证）**：前端画布调色板固定 8 类——start/end/llm/kb_retrieval/intent/selector/function_request/sub_workflow，且每类带配置字段模式（llm=model/temperature/maxTokens/systemPrompt/outputFormat、kb_retrieval=kbId/topK/similarityThreshold/mode 等）‹CERTAIN：fastrag-frontend/src/views/application/workflow-manage.vue:107-116,124-162（2026-09-24 深证）›；后端仍零校验（nodeType 接受任意串，WorkflowServiceImpl.java:20-22）。后端值域枚举校验缺位债务见 design §4 深化项 2；每类节点的输入输出执行契约仍属引擎实装范围（后端无任何消费代码）
- **应用触发工作流**——app_trigger 的 action_type='workflow' 触发时只回写触发记录，不调用工作流引擎（AppConfigServiceImpl.java:80-84）；应用↔工作流的联动语义归 app-center 链
- **发布版本化**——publish 仅翻转状态位，无版本快照/回滚/审核流（区别于知识库发布 publish-review 的版本语义）。〔决议 2026-09-24：取**不做**版本快照/回滚——发布维持状态位语义；理由：最小可行语义（执行引擎占位下版本化无消费方），且 schema 无任何版本表/版本列支撑（workflow 表无版本列、无 wf_version 表，schema.sql:399-406），回滚可走画布 JSON 手工另存覆盖的既有 update 路径；现状差距：无（现状即裁决目标态）（代裁，用户可推翻）〕
- **节点/边的结构约束**（无环、连通、必有起止节点）——后端零校验，画布结构合法性完全由前端保证。〔决议 2026-09-24：结构规则**属后端职责**——由 CanvasStructureValidator 承接（publish 闸门 + execute 前置双复用，见 design §4 深化项 2）；理由：数据完整性与执行终止性不能仅依赖前端；现状差距：后端零校验维持现状，债务已登记于 design §4 深化项 2（代裁，用户可推翻）〕
- **BPM 流程引擎能力**（人工任务、SpEL 表达式、流程实例调度）——`fastrag-bpm` 源码已删空、无 pom、不在构建内
- **工作流级数据权限**——工作流非 KB 资源，无 @KbAuth 归属；仅平台级 RBAC

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：**工作流（Workflow）**——应用内的可视化编排图，由节点（LLM、检索、重排序、模板、代码、条件、开始/结束）和边组成，表 `wf_*`；**应用（App）**——工作流的下游消费方；**统一响应（ApiResponse）**。本链补充狭义用法：「画布」（canvas）指前端编排视图及其序列化形态（workflow.nodes/edges 两个 JSON 字符串列）；「节点维度（dimension）」指节点扩展属性的 API 分段（properties/conditions/loops/delays/resources/permissions/log-level/env-vars/data-policies/backups，WorkflowController.java:71），落库时全部折叠到 wf_node.config 单列。

## 4. 功能需求

### 4.1 工作流 CRUD 与发布

用户创建/维护工作流定义并控制其上线状态。工作流定义含元信息（名称/描述/状态）与画布序列化数据（nodes/edges JSON 字符串），是画布节点行的容器。

- **REQ-A01**：工作流列表返回全量（无分页、无过滤），创建的工作流初始状态恒为 draft、画布恒为空图（nodes/edges="[]"）。〔证据：WorkflowServiceImpl.java:13,15 ‹CERTAIN›〕
- **REQ-A02**：更新按字段选择覆盖——仅 name/nodes/edges 三键在请求中出现时才覆盖对应列，其余字段忽略；更新不存在的 id 静默返回 null 不报错。〔证据：WorkflowServiceImpl.java:16 ‹CERTAIN›〕
- **REQ-A03**：删除为物理删除且**不级联**——删除工作流后其 wf_node 节点行、测试用例等子记录残留为孤儿数据。〔证据：WorkflowServiceImpl.java:17 仅 deleteById，无子表清理 ‹CERTAIN›；孤儿后果为结构推演 ‹CERTAIN›〕
- **REQ-A04**：发布是把 status 从 draft 翻转为 published 的状态位操作——无前置结构校验、无版本快照、无审核环节；重复发布幂等。〔证据：WorkflowServiceImpl.java:18 ‹CERTAIN›〕
- **REQ-A05**：创建/更新/删除/发布操作必须留下审计日志（@Loggable，操作类别 operation、动作 workflow_created/updated/deleted/published）。〔证据：WorkflowController.java:39-50 ‹CERTAIN›〕

### 4.2 画布节点编排

画布节点是工作流图的行式存储（wf_node 表），与画布整图序列化（workflow.nodes/edges）**双轨并存**：节点增删改移走节点行，整图保存走 workflow 主表，两条轨之间无同步约束。

- **REQ-B01**：向工作流添加节点：提供 nodeKey/nodeType/name/坐标；nodeType 接受任意字符串（无值域校验、无起止节点强制）；同工作流内 nodeKey 唯一性由数据库唯一索引兜底（重复 key 落库失败）。新节点默认启用。〔证据：WorkflowServiceImpl.java:20-22、schema.sql:1194 idx_wf_key ‹CERTAIN›〕
- **REQ-B02**：节点支持按 (workflowId, nodeKey) 定位的全量更新、物理删除、位置移动（改坐标）、按创建时间升序列出。〔证据：WorkflowServiceImpl.java:23-26 ‹CERTAIN›〕
- **REQ-B03**：画布整图（nodes/edges JSON 字符串）可整体保存到 workflow 主表——与节点行无一致性校验（节点行存在的 key 未必出现在整图 JSON，反之亦然）。〔证据：WorkflowServiceImpl.java:16（nodes/edges 直存）、:20-26（节点行）双轨并存 ‹CERTAIN›；「无同步约束」为结构观测 ‹CERTAIN›〕
- **REQ-B04**：节点扩展属性按维度 API 读写（properties + conditions/loops/delays/resources/permissions/log-level/env-vars/data-policies/backups 等 dimension 路径），但存储**不区分维度**——任何维度的保存都整体覆盖 wf_node.config 单列，后保存的维度冲掉先保存的维度；dimension 参数仅参与请求路由与回显。〔证据：WorkflowController.java:66-75、WorkflowServiceImpl.java:57-61（dimension 入参未参与存储逻辑）‹CERTAIN›〕
- **REQ-B05**：节点配置的序列化格式为 Java Map.toString()（如 {k=v}），非 JSON——读取时原样返回该字符串，不解析。〔证据：WorkflowServiceImpl.java:59 n.setConfig(config.toString()) ‹CERTAIN›〕
- **REQ-B06**：节点增/改/删/移操作必须留下审计日志（workflow_node_added/updated/deleted/moved）。〔证据：WorkflowController.java:52-65 ‹CERTAIN›〕

### 4.3 执行与调试

执行引擎是把画布图按边拓扑驱动节点产生输出的运行时——**本特性现状为占位实现**，接口面已就位、语义未实装。

- **REQ-C01**：触发工作流执行（传入 inputs）返回统一完成结构（status/output/executionId）——现状为占位：**不读取工作流定义、不遍历节点、不调用 fastrag-ai 任何网关**，恒返回「执行完成」。〔证据：WorkflowServiceImpl.java:28 ‹CERTAIN›；占位行为被 AppModuleValidationTest TC-MOCK-002 断言固化（源码级断言 execute 含硬编码串），:318-324 ‹CERTAIN›〕
- **REQ-C02**：单节点测试（对指定 nodeKey 试运行）返回节点级执行结果——现状同为占位（恒「节点执行完成」），不执行节点语义。〔证据：WorkflowServiceImpl.java:29 ‹CERTAIN›〕
- **REQ-C03**：执行入参 inputs 与执行结果之间无契约约束——任意 Map 可传入，返回结构固定三键。〔证据：WorkflowController.java:80、WorkflowServiceImpl.java:28 ‹CERTAIN›〕
- **REQ-C04**：调试信息查询返回工作流的调试日志列表——现状为硬编码三行假日志（不读 wf_debug_log 表）；调试配置保存仅回显请求内容（附 wfId/updatedAt），**不持久化**。〔证据：WorkflowServiceImpl.java:40-47 ‹CERTAIN›〕
- **REQ-C05**：执行为同步阻塞形态（无异步任务、无 SSE 流、无执行历史表——执行记录不落库）。〔证据：WorkflowServiceImpl.java:28 结构观测、全模块无执行记录写入 ‹CERTAIN›〕
- 〔决议 2026-09-24：执行引擎目标语义按**最小可行语义**代裁——①**错误传播 = fail-fast**：任一节点失败即中断整图，失败节点记 wf_debug_log error 行后终态 failed，不做跳过/降级；②**终止性** = 拓扑序单遍遍历 + 防御性步数上限 = 图节点数（当前 8 类值域无循环节点类型，天然有限步；成环图由 CanvasStructureValidator 在 publish/execute 前硬失败拒绝，不靠执行期截断）；③**超时** = 单节点 30s、整图 300s（保守默认，单节点值与 app_basic_config.timeout_seconds 缺省 30 同源）；④**并发** = 不设限（同步阻塞形态 REQ-C05，由调用方节流）。理由：与 agent 引擎 max-steps 同源的最小语义 + 本仓既有超时常量一致性；现状差距：引擎占位未实装（REQ-C01 债务）；**引擎实装时若取不同语义须回改本契约**（代裁，用户可推翻）〕

### 4.4 测试用例

测试用例是工作流的回归验证资产：给定 query/inputs，记录期望输出与实际输出及匹配结论。

- **REQ-D01**：按工作流列出/创建/删除测试用例；创建时强制归属当前工作流。〔证据：WorkflowServiceImpl.java:31-32 ‹CERTAIN›〕
- **REQ-D02**：删除测试用例不校验归属——任意 tcId 可删除任何工作流下的用例（按主键直删）。〔证据：WorkflowController.java:89、WorkflowServiceImpl.java:33 ‹CERTAIN›〕
- **REQ-D03**：测试用例的执行（跑用例比对 expected/actual/matched）**无入口**——matched 与 actual_output 无后端写入方，仅存储。〔证据：WorkflowServiceImpl.java:31-33 无执行逻辑、全链 grep 无 matched 写入 ‹CERTAIN›〕

### 4.5 模板与迁移

配置模板是可复用的画布预设（内置或用户创建）；迁移记录工作流跨环境搬迁的过程台账。

- **REQ-E01**：模板 CRUD：创建时 is_builtin 缺省 0；更新为按 id 全量覆盖；**内置模板可被删除**（无保护）。〔证据：WorkflowServiceImpl.java:35-38 ‹CERTAIN›〕
- **REQ-E02**：模板仅存画布数据（canvas_data 长文本），**不含节点行实例化**——用模板创建工作流时后端无「模板→工作流+节点行」的展开逻辑。〔证据：WorkflowServiceImpl.java:35-38 + create(:15) 无引用模板路径、schema.sql:1198-1207 ‹CERTAIN›〕
- **REQ-E03**：迁移为纯台账：创建记录（status 缺省 running、progress 缺省 0）+ 按 created_at 倒序列出；**无实际迁移执行逻辑**——source/target 工作流数据不被搬运、status/progress 永不推进（无后续写入口）。〔证据：WorkflowServiceImpl.java:53-54、全链无更新迁移记录的代码 ‹CERTAIN›〕

### 4.6 优化建议与监控

优化建议是工作流调优项的管理台账（建议类型/标题/影响分/状态）；监控提供工作流运行指标视图。

- **REQ-F01**：优化建议按工作流列出/创建（status 缺省 pending）/应用（status→applied）；应用无副作用——不修改工作流定义。〔证据：WorkflowServiceImpl.java:49-51 ‹CERTAIN›〕
- **REQ-F02**：优化建议无自动分析生成入口（区别于 app-center 优化建议的 analyze 端点）——建议全靠手工创建。〔证据：WorkflowController.java:109-115 仅 list/create/apply 三端点 ‹CERTAIN›〕
- **REQ-F03**：监控数据查询返回执行总数/平均延迟/错误率/最近执行时间——现状为硬编码指标（不读 wf_monitor_data 表）。〔证据：WorkflowServiceImpl.java:63 ‹CERTAIN›；被 AppModuleValidationTest TC-MOCK-003 断言固化，:326-333 ‹CERTAIN›〕
- **REQ-F04**：wf_debug_log 与 wf_monitor_data 两张表为**死表**——DDL/实体/Mapper 齐备但全链无业务写入方与查询方。〔证据：WfDebugLogMapper.java:6、WfMonitorDataMapper.java:6 定义后全链 grep 仅测试类引用（AppModuleValidationTest.java:45-57）‹CERTAIN›〕

### 4.7 权限与审计

- **REQ-G01**：全部端点经平台 RBAC 鉴权：读/编辑类 workflow:edit、创建类 workflow:create、删除类 workflow:delete、发布 workflow:publish、执行 workflow:run；**读端点（列表/详情/节点/调试/监控）同样要求 workflow:edit**（无只读角色可用面）。〔证据：WorkflowController.java:35-124（:35-38 读端点即 workflow:edit）‹CERTAIN›；权限种子 data.sql:88-92 ‹CERTAIN›〕
- **REQ-G02**：data.sql 另播种 api:workflow:*（list/detail/create/update/delete/publish/execute/node:manage/testcase:manage）九个 API 级权限键——**与控制器实际使用的 workflow:* 键不同名，无消费方**。〔证据：data.sql:285-293 vs WorkflowController.java:35-124 对照 ‹CERTAIN›〕
- **REQ-G03**：工作流不挂 KB 归属（无 @KbAuth）——可见性与操作面由 RBAC 全局权限决定，无知识库级/组织级隔离。〔证据：WorkflowController.java 类注解仅 @RequestMapping，无 @KbAuth ‹CERTAIN›〕

## 5. 非功能约束

仅列代码/配置可证者：

- 统一返回 ApiResponse、业务错误 = HTTP 200 + body.code（全部端点，GlobalExceptionHandler 语义）。〔证据：WorkflowController.java:2,36-124 ‹CERTAIN›〕
- 关键操作审计：@Loggable 覆盖 create/update/delete/publish、节点四操作、模板三操作、测试用例两操作、优化 apply、调试保存、迁移创建（读操作不记审计）。〔证据：WorkflowController.java:39-121 ‹CERTAIN›〕
- 数据规模与性能阈值：〔决议 2026-09-24：取保守默认——管理面接口 P95 ≤ 500ms、单工作流节点数 ≤ 50、工作流总量 ≤ 200（超出即触发分页补建）；理由：业界惯例 + 本仓 PageResult 分页统一惯例的一致性外推；现状差距：列表无分页（REQ-A01）与阈值约束均无服务端实现，登记债务（阈值内无实际影响）（代裁，用户可推翻）〕。
- 可用性：执行引擎占位（REQ-C01）——工作流「可执行」的产品承诺现状不成立。

## 6. 验收清单

- [ ] 创建工作流后 status=draft、nodes/edges 为空 JSON 数组（REQ-A01）
- [ ] 仅传 name 的 update 不改动 nodes/edges（REQ-A02）
- [ ] 删除工作流后 wf_node 中仍存在其节点行（REQ-A03，孤儿现状断言）
- [ ] 对不含起止节点的空图调用 publish 返回成功且 status=published（REQ-A04 无校验断言）
- [ ] 同工作流添加重复 nodeKey 的第二个节点落库失败（REQ-B01 唯一索引兜底）
- [ ] 先保存节点 conditions 维度再保存 delays 维度后，读取 conditions 返回的是 delays 内容（REQ-B04 覆盖语义断言）
- [ ] saveNodeConfig 后 wf_node.config 的值为 `{a=1}` 形态字符串而非 `{"a":1}`（REQ-B05 序列化断言）
- [ ] execute 对任意 inputs 恒返回 status=completed 且不产生任何模型/检索调用（REQ-C01 占位断言；豁免：占位/债务登记，见 implement 文档）
- [ ] saveDebugConfig 后再次 getDebugInfo 看不到已保存的配置（REQ-C04 不持久化断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 删除 B 工作流的测试用例时传入 A 工作流用例的 tcId 可删除成功（REQ-D02 无归属校验断言）
- [ ] 创建的内置模板（is_builtin=1）可被 DELETE /templates/{id} 删除（REQ-E01 无保护断言）
- [ ] createMigration 后再次列表，该记录 status 仍为 running、progress 仍为 0（REQ-E03 永不推进断言；豁免：占位/债务登记，见 implement 文档）
- [ ] applyOptimization 后工作流定义（nodes/edges）不变（REQ-F01 无副作用断言）
- [ ] monitor 返回的 totalExecutions 恒为 3200 与真实执行无关（REQ-F03 占位断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 无 workflow:edit 权限的用户 GET /api/workflows 被拒（REQ-G01）
- [ ] 创建/更新/删除/发布工作流各留一条对应动作的审计日志（REQ-A05）
- [ ] 按 (workflowId, nodeKey) 全量更新节点、删除节点、移动坐标、按创建时间升序列出均生效（REQ-B02）
- [ ] 保存与节点行无关的整图 JSON 成功，节点行存在的 key 不在整图时亦不报错（REQ-B03 双轨无一致性校验现状断言）
- [ ] 节点增/改/删/移各留一条对应动作的审计日志（REQ-B06）
- [ ] 单节点测试对任意 nodeKey 恒返回「节点执行完成」且不执行任何节点语义（REQ-C02 占位断言；豁免：占位/债务登记，见 implement 文档）
- [ ] execute 对任意 Map 入参均返回固定三键结构（REQ-C03 无契约约束现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 执行为同步阻塞返回，无异步任务、无 SSE 流、无执行历史落库（REQ-C05 占位形态现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 测试用例创建后归属当前工作流，按工作流列出可见（REQ-D01）
- [ ] 测试用例执行无任何入口，matched 与 actual_output 无后端写入方（REQ-D03 债务登记现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 用模板创建工作流不产生任何节点行实例化（REQ-E02 无展开逻辑现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 优化建议无自动分析入口，建议仅能手工创建（REQ-F02 现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] wf_debug_log 与 wf_monitor_data 全链无业务写入方与查询方（REQ-F04 死表现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] data.sql 播种的 api:workflow:* 九键与控制器 workflow:* 键不同名且无消费方（REQ-G02 噪声登记现状断言；豁免：占位/债务登记，见 implement 文档）
- [ ] 无 KB 归属的工作流仅由平台 RBAC 决定操作面，知识库成员关系不影响可见性（REQ-G03）

## 7. 测试策略

经由公共接口（WorkflowController → WorkflowService）测试，优先级：

1. **管理面行为固化**（可自动化，最高价值）：CRUD 字段选择覆盖、发布幂等、节点唯一键冲突、维度覆盖语义（REQ-B04 的「后保存冲掉先保存」是当前实现的真实行为，必须先固化再谈修复）、孤儿删除、模板/用例删除的无保护语义——这些「负向行为断言」防止后续修复时无回归网。
2. **占位契约固化**：execute/executeNode/monitor 的占位行为已有源码级断言（AppModuleValidationTest TC-MOCK-002/003），接口级占位断言补齐；引擎实装时这批用例是**必须翻红的信号灯**（占位断言翻红 = 引擎实装开始，同时替换为真实语义用例）。
3. **执行引擎目标用例族**（引擎实装后）：节点错误传播（fail-fast：LLM 节点异常时整图终止——2026-09-24 已裁，§4.3 决议）、条件分支路由正确性、循环终止性（结构校验拒环 + 防御步数上限——已裁）、检索节点调用 retrieval 面、LLM 节点调用 ModelRegistry 路由——语义已按 2026-09-24 代裁定稿，tdd/workflow.md E 族用例按该裁决填实。
4. **权限矩阵**：五权限键 × 读/写/删/发布/执行端点的允许与拒绝。
5. 测试基建现况：application 模块已有 AppModuleValidationTest（实体-表映射/服务接口面/占位检测 22 表 schema-h2 校验）可复用其 H2 基建；WorkflowController/WorkflowServiceImpl 无专门测试类（缺口）。
