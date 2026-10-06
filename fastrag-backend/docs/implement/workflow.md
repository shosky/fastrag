# 工作流编排（workflow）— 实现文档

> 记录**既有代码**如何实现契约（contract/interfaces.md `## workflow` 节）。执行引擎为占位实现——本文档对占位与真实逻辑分别如实记录。

## 1. Code File Map

```
fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/
├── controller/
│   └── WorkflowController.java        32 端点全部 HTTP 面：路由/@PreAuthorize 五权限键/@Loggable 审计；
│                                      请求解包（Map 手取字段或实体直绑），业务全数转调 svc
├── service/
│   ├── WorkflowService.java           27 方法单接口（五个 design 模块共用一个接口面——design §4 深化项 4）
│   └── impl/
│       └── WorkflowServiceImpl.java   64 行密排单实现：生命周期/画布节点/引擎占位/资产台账/观测占位
├── entity/                             8 实体（ASSIGN_ID；WfDebugLog 用 AUTO 自增）
│   ├── Workflow.java                  主表：name/description/status/nodes/edges（画布双轨之整图轨）
│   ├── WfNode.java                    节点行：nodeKey/nodeType/config/坐标/nextNodes(无写入方)
│   ├── WfTestCase.java / WfTemplate.java / WfMigration.java / WfOptimization.java
│   └── WfDebugLog.java / WfMonitorData.java   死表实体（Mapper 齐备零业务引用）
└── mapper/                             8 个 BaseMapper 接口（零自定义 SQL）
fastrag-modules/fastrag-application/src/test/java/.../
└── AppModuleValidationTest.java        22 实体-Mapper 映射 / WorkflowService 方法面（TC-SERVICE-004）/
                                       占位源码断言（TC-MOCK-002 execute、TC-MOCK-003 monitor）
fastrag-bootstrap/src/main/resources/schema-h2.sql   测试基线（workflow 表多 category/version 两列、
                                       wf_monitor_data 有 dimension——与 schema.sql 漂移，见 data.md 登记）
init-scripts/schema.sql:399-410,1181-1265          8 张表全量 DDL
init-scripts/data.sql:88-92,285-293,558-584        权限种子 + wf_refund_demo 业务种子（:581 列错位缺陷）
```

链外相关：`fastrag-modules/fastrag-bpm/`——**死目录核实**（见 §3 偏离 10）；app 侧消费点 AppConfigServiceImpl.java:254-270（advancedOptions.workflow 段）、:80-84（runTrigger 不调引擎）。

## 2. Interface Implementations

### CanvasNodeStore（from contract: workflow.CanvasNodeStore）

**Logic Flow:**
- `addNode`：1. 控制器从 body Map 手取 nodeKey/type/name；x/y 用 `instanceof Number` 判型，非数值或缺省 → 0（WorkflowController.java:54）2. new WfNode 填六字段，enabled 恒 1 3. **无** nodeKey 查重、**无** nodeType 值域校验 4. insert——重复 key 由 DB 唯一索引 idx_wf_key 拒绝，DuplicateKey 异常上抛 5. RETURN 新实体（含生成 id）
- `updateNode`：1. 实体直绑请求体 2. 强制覆写 path 上的 workflowId/nodeKey（防串改）3. `update(entity, wrapper(workflowId=?, nodeKey=?))` 按 MyBatis-Plus 非空字段 SET 4. 回查 selectOne 5. RETURN 命中实体或 null（查无静默）
- `deleteNode`：1. 按 (workflowId,nodeKey) wrapper delete 2. 查无零行删除不报错 3. RETURN void
- `moveNode`：1. selectOne 复合键 2. IF 命中 THEN 改 x/y → updateById ELSE 静默 3. RETURN 实体或 null
- `listNodes`：wrapper eq workflowId + orderByAsc(createdAt) → RETURN 列表
- `getNodeConfig`：1. selectOne 复合键 2. IF 命中且 config≠null THEN r.config=原串 ELSE r.config="{}" 3. RETURN 单键 Map（config 恒为字符串——**不解析**）
- `saveNodeConfig`：1. selectOne 复合键 2. IF 命中 THEN `n.setConfig(config.toString())` → updateById（**Map.toString() 产出 `{k=v}` 形态，非 JSON**）ELSE 静默不写 3. RETURN {nodeKey, dimension, saved:true}——**dimension 入参未参与存储逻辑，节点不存在仍报 saved:true**

**Module Interactions:**
- Calls：WfNodeMapper（唯一依赖）；无 fastrag-ai/retrieval/infra 交互
- Called by：WorkflowController（nodes 五端点 + properties/dimension 四端点）
- Shared state：wf_node 表；与 workflow.nodes/edges 画布 JSON 无读写联动（双轨并存）

**Decisions & Deviations:** 与契约无偏离（契约按现状书写）。实现内关键取舍：复合键唯一性交给 DB（应用层不查重）→ 冲突以 500 语义兜底形态出现，无业务错误码（登记债务）；维度折叠单列 + toString 序列化（登记债务，tdd S2 已固化）。

**Parent Trace:** WorkflowServiceImpl 画布节点段 → workflow.CanvasNodeStore → CanvasNodeModule → REQ-B01~B06

### WorkflowExecutionEngine（from contract: workflow.WorkflowExecutionEngine）

**Logic Flow:**
- `execute`：1. new LinkedHashMap 2. put status="completed"、output="执行完成"、executionId="exe_"+UUID 前 8 位 3. **不** selectById 工作流、**不**读 nodes/edges、**不**遍历节点、**不**调用任何外部服务、**不**落任何表 4. RETURN 三键 Map（wfId 不存在同结果；inputs 全程未读）
- `executeNode`：1. new LinkedHashMap 2. put status="completed"、nodeKey=入参回显、output="节点执行完成" 3. 同上零校验零副作用 4. RETURN 三键 Map
- 错误路径：无——两方法无任何可抛异常语句（占位即无错误模式，契约 Error modes 一致）

**Module Interactions:**
- Calls：无（连自身 Mapper 都不注入执行路径）
- Called by：WorkflowController（execute、testNode 两端点）；**app 侧未接线**——AppConfigServiceImpl.runTrigger 对 action_type='workflow' 的触发只回写触发台账，不调本方法（AppConfigServiceImpl.java:80-84）
- Shared state：无（无表读写；wf_debug_log/wf_monitor_data 死表即本应被本引擎激活的表）

**Decisions & Deviations:** 占位实现是有意登记的现状（AppModuleValidationTest TC-MOCK-002/003 以源码断言**固化**占位行为，防"无意实装"与"无意破坏"两端）——占位断言翻红是引擎实装的启动信号（tdd §4 门禁）。目标形态（缝 1 执行器路由、缝 2 ModelRegistry 模型路由、缝 3 RetrievalSearchEngine 检索面）见 design §3/§4，语义裁决为 HITL 项。

**Parent Trace:** WorkflowServiceImpl:28-29,40-47,63 → workflow.WorkflowExecutionEngine → WorkflowExecutionEngine + WfObservabilityStub → REQ-C01~C05、REQ-F03~F04

### 端点排除面实现概述（不立契约，镜像 design §2）

- **生命周期**：create 固定 draft/空图；update 以 `containsKey` 三键选择覆盖；publish 仅 status 置 published（均 WorkflowServiceImpl.java:13-18）；delete 单表 deleteById 无级联。审计由 @Loggable 声明式完成（AOP 切面，common 模块）。
- **资产台账**：测试用例/模板/优化/迁移四组均为「insert 前补一两个缺省值 + wrapper 查询」的直通 CRUD（:31-54）；apply 优化仅 status→applied。
- **观测占位**：getDebugInfo 返回硬编码三行字符串列表；saveDebugConfig 把请求 Map 复制后补 wfId/updatedAt 回显（不落库）；getMonitorData 硬编码四指标（:40-47,63）。

## 3. Testing（引用 tdd/workflow.md）

- **现有覆盖**：AppModuleValidationTest——实体↔表↔Mapper 完整性（TC-ENTITY-MAPPER-001/002）、WfDebugLog/WfMonitorData/WfOptimization 字段与 schema 一致性（TC-ENTITY-FIELD-003/004/005）、WorkflowService 方法面（TC-SERVICE-004）、占位源码断言（TC-MOCK-002/003）、schema-h2 22 表在位（TC-SCHEMA-001）。对应 tdd S3 占位固化的**源码层**部分。
- **缺口（tdd 全部切片待实施）**：S1 生命周期行为、S2 节点存储（维度覆盖/toString 形态/唯一键冲突）、S3 接口层占位断言、S4 台账缺省值与无保护语义、S5 权限矩阵——均无测试；WorkflowController/WorkflowServiceImpl 无专门测试类。
- **修复时的回归警示**：S2/S4 的负向行为用例（维度冲掉、孤儿删除、内置模板可删、跨工作流删用例）固化的是**缺陷现状**——修复这些缺陷时用例应显式翻红再改写，不可静默通过。

## 4. Decisions & Deviations（链级登记汇总）

1. **执行引擎占位**（§2 已详）——「可执行」产品承诺现状不成立（REQ-C01）。
2. **debug/monitor 硬编码、debug 配置不落库**（WorkflowServiceImpl.java:40-47,63）。
3. **节点配置 toString 序列化 + 维度单列折叠 + 节点不存在仍 saved:true**（:56-61）。
4. **删除不级联**：workflow 删除遗留 wf_node/wf_test_case 等孤儿行（:17）。
5. **无归属/无保护删除**：deleteTestCase 跨工作流可删（:33 + WorkflowController.java:89）；内置模板可删（:38）。
6. **双轨存储**：workflow.nodes/edges 画布 JSON 与 wf_node 行互不同步；边仅存在于 JSON 列（无 wf_edge 表）。
7. **DDL 漂移三项**：wf_monitor_data.dimension（实体+h2 有、schema.sql 无）、schema-h2 workflow 多 category/version 两列、data.sql:581 wf_optimization 种子列错位（MySQL 下种子 INSERT 必失败）——详见 data.md `## workflow` 登记。
8. **权限键两套并存**：控制器用 workflow:*（data.sql:88-92），data.sql:285-293 的 api:workflow:* 九键无消费方；读端点要求 workflow:edit（无只读面）。
9. **无写入方字段群**：workflow.created_by、wf_node.next_nodes、wf_test_case.actual_output/matched/created_by、wf_template.created_by、wf_migration.validate_result/operator、wf_optimization.before_metric/after_metric——存储在位、代码零写入（grep 实测）。
10. **fastrag-bpm 死目录核实（修正 feature-map「无 pom 空目录」表述）**：`fastrag-modules/fastrag-bpm/` ①不在根 pom `<modules>`（pom.xml:32-48）；②src/main/java 下 **0 个 .java 文件**，仅残留 expression/model/validator 三个空包目录；③target/ 残留 **137 个陈旧 .class**（含 CanvasController/EdgeController/FlowDefController/InstanceController/LegacyWorkflowCompatController、executor/impl、SpEL expression、validator 等）与 surefire 报告——即：bpm 模块曾完整编译运行过，**源码已删除、构建产物与空目录残留**。上位 bpm-design.md 的 9 表设计与现存 wf_ 8 表（含主表）对不上的仲裁结论：**bpm 设计未落地、已放弃，wf_* 为唯一现行实现**；本链不追溯 bpm 遗产，target 残留产物建议物理清理（不改源码纪律内，仅登记）。
11. **app 侧消费未接线**：app_trigger.action_type='workflow' 不调引擎（AppConfigServiceImpl.java:80-84）；advancedOptions.workflow 段仅存取（:254-270）；App javadoc 提及 type='workflow'（App.java:14）。消费归 app-center 链。

**Parent Trace 汇总**：实现单元（WorkflowController/WorkflowServiceImpl）→ 契约（CanvasNodeStore、WorkflowExecutionEngine）→ Design（CanvasNodeModule、WorkflowExecutionEngine、WorkflowLifecycleModule、WfAssetModule、WfObservabilityStub）→ Spec REQ-A01~A05/B01~B06/C01~C05/D01~D03/E01~E03/F01~F04/G01~G03。
