---
name: Widget
summary: Widget 资产域与对话协议缝——三件套资产存储/校验/播种/快照解析（IF-004）与回传推进（IF-005），经两个既有缝接入对话循环。
type: module
id: MOD-WIDGET
profile: backend
status: draft
updated: 2026-10-31
satisfies: [SPEC-WIDGET]
decisions: [ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL, ADR-0011-WIDGET-TEMPLATE-DSL]
declares: []  # ERR-WG-* 全部由 docs/interfaces.md 声明（线上/跨端码所有权规则），本档仅引用
hash: "04be5e43bbaa"
summaryHash: "04be5e43bbaa"
---

## 职责 {#responsibility}

Widget 资产域与对话协议缝：三件套（模板/数据结构/默认值）资产的存储、三档结构校验、官方模板播种与复制、卡片渲染快照解析（IF-004），以及用户卡片操作向对话输入的推进（IF-005）；对外经 render_widget 工具执行器与清单注入中间件两个既有缝接入对话循环，自身不引引擎改动。

**Files:**
- fastrag-modules/fastrag-application/`com.fastrag.module.application.widget` — 资产域主包（实体/Mapper/Service/执行器/回传处理器/播种器），隐藏校验器、白名单、属主过滤、快照组装与摘要合成
- fastrag-modules/fastrag-agent/`com.fastrag.module.agent.middleware.WidgetMiddleware` — 清单注入中间件（agent 链成员，order=14，只读 runtimeState 清单通道，不依赖 application）

## 契约 {#contract}
### IF-004 — WidgetAssetService {#if-004}

**用途：** 资产域唯一深接口——快照解析（绑定集合 fail-closed 校验 + 数据结构校验 + 定格快照）、注入清单组装与三档保存校验；工具执行器、清单注入、工作台保存三条调用链共用。
**签名：**
```java
// com.fastrag.module.application.widget
public interface WidgetAssetService {
    /**
     * 卡片渲染快照解析：widgetId 必须 ∈ allowedWidgetIds（本次启用绑定集合，fail-closed），
     * data 按资产数据结构校验（≤32KB），通过则返回生成时刻定格的快照五要素；
     * 越界/校验失败抛对应业务异常（工具路径由执行器转 Error 文本回灌 LLM）。
     * widgetKey 由调用方（工具实参）透传——实现期修订：设计稿签名遗漏该形参，见逻辑节 Deviations。
     */
    WidgetSnapshot resolveSnapshot(String widgetId, String widgetKey, Map<String, Object> data, Set<String> allowedWidgetIds);

    /** 组装对话注入清单（id/名称/用途/数据结构/默认值示例）；已删除/不可见资产跳过并告警（清单降级不报错）；
     *  文本总量超 5000 字符按序截断（调用方渲染 system 块） */
    List<WidgetCatalogEntry> buildCatalog(List<String> widgetIds);

    /** 保存三档校验：JSON 合法 / 组件名∈白名单且必填属性齐全 / 变量与模板引用一致且 default 可按 schema 反序列化；
     *  单件 JSON ≤ 64KB（实体 TEXT 列上界）、name 非空白；失败抛逐项行级错误 */
    void validateDraft(String name, String template, String schemaJson, String defaultJson);
}
```
**类型：**
```java
// com.fastrag.module.application.widget
record WidgetSnapshot(String widgetId, String widgetKey, String name, String template, Map<String, Object> data) {}
// widgetKey: LLM 自拟卡片替换键，同键到达即前端替换旧卡；template: emit 时刻定格的组件树 JSON 字符串

record WidgetCatalogEntry(String id, String name, String description, String schemaJson, String defaultJson) {}

record WidgetDraft(String templateJson, String schemaJson, String defaultJson) {}
```
**错误模式：**
- ERR-WG-SCHEMA-INVALID — trigger: 三档校验任一失败（保存与工具校验同族）；recovery: 保存路径返回逐项行级业务错误，工具路径转 Error 文本回灌 LLM 自纠
- ERR-WG-NOT-IN-BINDING — trigger: widgetId ∉ allowedWidgetIds，或资产在绑定装配后被删除/解绑（集合有效性以工具执行时刻为准，fail-closed）；recovery: Error 文本回灌 LLM
- ERR-WG-BOUND-DELETE — trigger: 删除被任一应用绑定的资产；recovery: 业务错误提示先解绑
- ERR-WG-OVERSIZE — trigger: 提交数据序列化 > 32KB；recovery: 同 ERR-WG-SCHEMA-INVALID 的工具失败路径
**不变量：**
- Pre: allowedWidgetIds ⊆ 当轮启用绑定集合，由调用方（执行器）从绑定装配传入；data 为 null/缺字段时按数据结构 required 校验处理（走 ERR-WG-SCHEMA-INVALID）
- resolveSnapshot 只读无副作用、无锁需求（资产读取不互斥；并发工具调用各得各自快照）；快照不含 icon/description（渲染不需要）
- Post: 快照五要素在生成时刻定格；已落库消息的卡片渲染不随后续资产修改变化
- 保存校验接受的组件名集合与前端渲染注册表一致（双份常量，守护测试钉住，对齐 SkillDdlParityTest 先例）
- 官方模板行（is_builtin=1）不可修改删除、不进绑定候选；复制 = 新建非内置行拷贝三件套、属主当前用户
**Public vs hidden:** 调用方只见三方法；白名单、组件属性校验、属主过滤、播种幂等、复制语义、快照组装全部隐藏。

### IF-005 — WidgetActionProcessor {#if-005}

**用途：** 把用户卡片回传负载推进为一轮对话输入——尺寸护栏、可见用户消息文本合成、结构化标注块生成；编排层一行调用。
**签名：**
```java
// com.fastrag.module.application.widget
public interface WidgetActionProcessor {
    /** 校验 payload ≤16KB 并合成推进计划；越限抛业务异常，对话不推进 */
    WidgetTurnPlan prepare(WidgetActionPayload action);
}
```
**类型：**
```java
// com.fastrag.module.application.widget
record WidgetActionPayload(String widgetId, String widgetKey, String action, Map<String, Object> payload) {}
// action ∈ sys.go_to_url | sys.download | sys.submit；仅 sys.submit（回传智能体）进入本单元

record WidgetTurnPlan(String visibleMessageText, String contextBlock) {}
// visibleMessageText: 人类可读摘要，随当轮用户消息落库展示；contextBlock: [WIDGET_ACTION] 标注块，仅注入本轮引擎上下文
```
**错误模式：**
- ERR-WG-ACTION-OVERSIZE — trigger: payload 序列化 > 16KB；recovery: 业务错误拒绝，消息序未推进，前端卡片保持可交互
**不变量：**
- Post: visibleMessageText 作为当轮用户消息落库（历史回放可见）；contextBlock 仅进本轮引擎输入，不落消息列
- action ≠ 'sys.submit' 时拒绝（服务端防御；跳转/下载类动作由前端本地处理，正常不进入本单元）
- widgetId 不做绑定集合校验——回传是用户输入通道（用户本可发送任意消息），fail-closed 防线只设在 LLM 工具通道（IF-004），两者威胁模型不同
- 回传沿用所属对话通道既有鉴权，本单元不引入新鉴权面
**Public vs hidden:** 编排层只见 prepare 一方法；负载解析、摘要文案、块格式、护栏全部隐藏。
**Implemented by:** widget 包内自有实现（WidgetActionProcessorImpl）。编排层调用点在 AppServiceImpl.executeDialog——该文件所有权归 modules/app-chat.md#code（app-chat 域），本单元自身实现文件（widget 包内）归本模块。

## 接口 {#interface}
### WidgetController（资产 REST 面）
**Purpose:** 工作台 CRUD + 复制 + 官方模板视图的 HTTP 面；统一 ApiResponse/PageResult；校验失败走 IF-004 行级业务错误。
**Signature:** `/api/widgets` 家族——list（含 is_builtin 过滤视图）/ get / create / update / delete（含 ERR-WG-BOUND-DELETE）/ copy；DTO 形状归契约层 interfaces.md。
### 绑定端点（第六类，挂 AppConfigController）
**Purpose:** `/{appId}/widgets` 四动词 + 候选端点；服务层校验绑定上限 10（ERR-WG-BINDING-LIMIT 业务错误）与候选口径（仅自建 is_builtin=0 资产）；AppConfigController 为既有共享控制器，实现期实现文件登记于本模块 `## Code`（首个 owner 声明）。
**Signature:** `/{appId}/widgets` GET / POST / PUT `/{bindingId}` / DELETE `/{bindingId}` + GET `/{appId}/widgets/candidates`（挂 `/api/apps` 前缀；GET=`app:use`、写三动词=`app:edit`，照五兄弟惯例）。

## 实体 {#entities}
```sql
CREATE TABLE widget (
    id           VARCHAR(32) PRIMARY KEY,          -- String ASSIGN_ID（对齐仓内 32 位惯例）
    name         VARCHAR(128) NOT NULL,            -- 展示名
    description  VARCHAR(512),                     -- 用途说明（进注入清单）
    icon         VARCHAR(255),                     -- 图标（可空，前端回退）
    template     TEXT NOT NULL,                    -- 组件树 JSON（{type,props,children[]},{{var}} 插值）
    schema_json  TEXT NOT NULL,                    -- 变量声明 JSON（列名避开保留字 schema）
    default_json TEXT,                             -- 占位数据 JSON
    is_builtin   TINYINT NOT NULL DEFAULT 0,       -- 1=官方模板行（只读，仅复制）
    creator      VARCHAR(32) NOT NULL,             -- 属主 userId；system=官方播种
    created_at   DATETIME NOT NULL,
    updated_at   DATETIME NOT NULL
);

CREATE TABLE app_widget_binding (
    id         VARCHAR(32) PRIMARY KEY,            -- String ASSIGN_ID
    app_id     VARCHAR(32) NOT NULL,
    widget_id  VARCHAR(32) NOT NULL,
    enabled    TINYINT NOT NULL DEFAULT 1,         -- 0=禁用：不注入不渲染
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_app_widget (app_id, widget_id)
);
```
- 关系：widget 1—N app_widget_binding N—1 app；绑定上限 10 为服务层校验非 DDL 约束。
- 注意：DDL 双处同步（SchemaInitializer initSchema 一表一 try 块 + schema.sql 资源段，WidgetSchemaSyncTest 守护）；生效语义为保存即读，无版本列。

## 逻辑 {#logic}

```text
IF-004 WidgetAssetService（WidgetAssetServiceImpl）:
  resolveSnapshot(widgetId, widgetKey, data, allowedIds):
    IF widgetId 空 OR allowedIds 不含 widgetId: THROW 404（未在绑定集合，fail-closed）
    widget ← selectById(widgetId); IF null: THROW 404（不存在或已删除——执行时刻有效性）
    IF data 序列化 > 32KB: THROW 400（超 32KB）
    errors ← validator.validateData(widget.schemaJson, data)
    IF errors 非空: THROW 400（行级 \n 聚合）
    RETURN Snapshot{widget.id, widgetKey, widget.name, widget.template, data}   // emit 时刻定格
  buildCatalog(widgetIds):
    rows ← selectBatchIds; FOR id ∈ widgetIds（保序）:
      rows 缺 id → WARN + 跳过（清单降级不报错）；否则收录 CatalogEntry
  validateDraft: errors ← validator.validate(name, 三件套); 非空 → THROW 400（行级 \n 聚合）
  create/update/delete/copy: 属主·官方行·绑定保护守卫（403/409）+ 三档校验 + 落库

IF-005 WidgetActionProcessor（WidgetActionProcessorImpl）:
  prepare(payload):
    IF action ≠ sys.submit OR widgetId/widgetKey 空: THROW 400
    IF payload 序列化 > 16KB: THROW 400（超 16KB）
    visible ← "（来自 Widget 卡片 {widgetKey}）提交操作：{k=v 摘要 ≤200}"
    block ← "[WIDGET_ACTION] widgetId=… widgetKey=… action=sys.submit + payload JSON + 续任务指引"
    RETURN TurnPlan{visible, block}

对话链装配（AppServiceImpl.executeDialog 扩展）:
  widgetAction 非空 → prepare → effectiveQuery=visible；widgetContextBlock=block
  敏感词拦截与用户消息落库一律用 effectiveQuery（消息序正常推进）
  异步块：bindings.widgets 非空 → buildCatalog → 转 List<Map> 放 runtimeState[WIDGET_CATALOG_KEY]
  引擎输入 query = block 非空 ? block + 原query : query（标注块不落消息列）

WidgetMiddleware.beforeModelCall（order=14）:
  runtimeState 清单键非空 List → 渲染清单块（≤5000 截断）追加首条 system + 注册 render_widget 工具
RenderWidgetToolExecutor.execute（type="widget"）:
  allowed ← 清单通道 Map 的 id 集；空 → Error 文本
  resolveSnapshot(widgetId, widgetKey, data, allowed)
  成功 → ToolResult.success + structured=snapshot.toMap()；BusinessException → ToolResult.error 原文回灌

WidgetBuiltinSeeder.run: FOR spec ∈ 内置常量集: IF NOT exists(creator=system, name=spec.name) → 插入（is_builtin=1）
```

**Decisions & Deviations**：
- **IF-004 签名修订（实现期）**：resolveSnapshot 增加 `widgetKey` 形参——快照五要素中 widgetKey 由工具实参提供，设计稿签名遗漏（TDD 红灯发现）；本档 `## Contract` 签名已同步更新。
- **清单通道松类型化**：WidgetMiddleware（agent 模块）不得 import application 类（依赖方向仅 application→agent），通道载荷定为 `List<Map<String,Object>>`（键集 id/name/description/schemaJson/defaultJson，WidgetCatalogEntry.toMap 产出），与 HistoryMiddleware 的 runtimeState 松类型通道同型。
- **DDL 主键 VARCHAR(32)**：entities 原记 VARCHAR(64)，实现对齐全仓 id 惯例（skill/sandbox_config 同 32），实体 DDL 已同步改 32；双处一致由 WidgetSchemaSyncTest 守护。
- **绑定候选独立端点**：候选口径实现为 `GET /{appId}/widgets/candidates`（interfaces.md API-007 Invariants 的补口，前端 WidgetConfig 消费）。
- **MapperScan 增补（联调启动发现）**：`@MapperScan("com.fastrag.module.*.mapper")` 单段通配不覆盖 `application.widget.mapper`（多一层包段）——bootstrap 扫描表追加该包（FastRagApplication.java）；widget.mapper 包位置保持自包含不动。
- **S5/S7 编排层接线**（清单 put、widgetAction 合成落库与块注入）为 executeDialog 内直改，未设专项单测（AppServiceImpl 全链构造 25+ 依赖，成本高于收益）；以实现审查 + IF-005 processor 单测 + 前端 T4/S8 路径联合覆盖；真实联调（后端 8081 + 模型 + 绑定资产）待执行。

## 边界设计 {#seam}

**工具执行器型缝** — render_widget 以独立 `type="widget"` 注册为第 8 个执行器：factory 按 type `findFirst`，`builtin` 型已被 InstallSkillToolExecutor 占用（单类内分发），撞型会导致路由不确定——独立型是唯一安全姿势。Adapter = RenderWidgetToolExecutor（widget 包）：校验转快照的一层壳，深逻辑在 IF-004。稳定侧：ToolExecutor 接口 + 工厂注入（tools 模块）。

**中间件链缝** — WidgetMiddleware（agent 链，order=14，0-13 全满尾插）在 beforeModelCall 消费 runtimeState 清单通道：非空则注入 system 清单块 + 注册 render_widget 工具定义（query_kb 经中间件注册同型）。稳定侧：AgentMiddleware 钩子与 runtimeState 传递通道（PreRetrievalMiddleware 先例）；中间件不反向依赖 application——清单由编排层（持有 Bindings）经 IF-004.buildCatalog 组装后放入 runtimeState（松类型 List<Map>，见 Deviations）。

**清单注入通道** — 变化点在编排层：绑定装配（DialogPresetResolver.Bindings 增 widgets 字段，enabled=1 才入）与清单组装都住 application；未来若改为输出侧后处理或预置目录推送，只动编排层与通道键，不触工具定义与执行器。

**组件白名单双端缝** — 后端校验白名单（widget 包常量）↔ 前端渲染注册表，守护测试双向钉住（ADR-0011 的安全底线支撑）。稳定侧：白名单常量本身；扩展 = 两端各加一项 + 测试各加一条。

**深化机会** — 一次调用产多卡（快照列表化）、schema 校验器升级 JSON Schema 全量、内置模板增量更新语义（McpBuiltinSeeder 式），均可在 IF-004 签名不变下吸收。

## 追溯 {#trace}

- [IF-004.resolveSnapshot fail-closed/快照定格] → [REQ-WG-D02/D03] → [RenderWidgetToolExecutorTest]
- [IF-004.validateDraft 三档行级] → [REQ-WG-A02] → [WidgetTemplateValidatorTest 12 断言]
- [生命周期/属主/官方行只读/复制] → [REQ-WG-A01/A03~A05/B02/B03] → [WidgetAssetServiceTest 11 断言]
- [播种幂等三分支] → [REQ-WG-B01] → [WidgetBuiltinSeederTest]
- [清单注入/截断/三必填 schema] → [REQ-WG-D01/D05] → [WidgetMiddlewareTest 5 断言]
- [体积护栏 32KB/16KB/5000] → [REQ-WG-D05/E03] → [ValidatorTest/ProcessorTest/SchemaSyncTest]
- [回传合成/防御/护栏] → [REQ-WG-E01~E03] → [WidgetActionProcessorTest 5 断言]
- [DDL 双处 + 白名单双端] → [REQ-WG-A06 + 约束] → [WidgetSchemaSyncTest 3 断言]
- [绑定上限/唯一对/候选] → [REQ-WG-C01~C04] → [AppConfigServiceTest + 前端 WidgetConfig.test.ts T6.3]
- 质量门：fastrag-application + fastrag-agent 模块 `Widget*,AppConfig*,ConversationStream*,AppServiceImpl*` 73/73 绿（含存量回归零失败）

## 代码 {#code}

```yaml
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetAssetService.java
  description: IF-004 深接口定义（快照解析/清单组装/三档校验 + 生命周期方法）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetAssetServiceImpl.java
  description: 资产域实现——属主·官方行·绑定保护守卫与快照组装的全部隐藏逻辑
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetTemplateValidator.java
  description: 三档结构校验 + 运行时数据校验（行级错误产出，S1 被测对象）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetComponentWhitelist.java
  description: 一期 19 组件白名单 + 必填属性表 + 体积/数量护栏常量（双端守护的后端半边）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/BuiltinWidgetTemplates.java
  description: 官方模板内置常量 12 条（对标 ADP 模板库四场景：确认/进度/澄清/展示，播种内容唯一事实源）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetBuiltinSeeder.java
  description: 启动幂等播种器（creator=system+name 存在即跳过，SkillDataInitializer 口径）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/RenderWidgetToolExecutor.java
  description: render_widget 执行器（独立 type="widget"）——校验转快照的缝适配器
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetActionProcessor.java
  description: IF-005 回传推进接口定义
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetActionProcessorImpl.java
  description: 回传合成实现——16KB 护栏/可见摘要/标注块（纯函数零 DB）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/WidgetController.java
  description: 资产 REST 面（API-001~006），登录即可 + 属主隔离在服务层
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/entity/Widget.java
  description: widget 表实体（三件套 JSON 列，schema_json/default_json 避保留字）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/entity/AppWidgetBinding.java
  description: app_widget_binding 表实体（第六类绑定，enabled 语义）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/mapper/WidgetMapper.java
  description: Widget BaseMapper
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/mapper/AppWidgetBindingMapper.java
  description: 绑定 BaseMapper
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/dto/WidgetSnapshot.java
  description: 快照五要素 record（structured 载荷与历史落库同构源）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/dto/WidgetCatalogEntry.java
  description: 清单条目 record（toMap 产出通道载荷）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/dto/WidgetDraft.java
  description: 三件套草稿 record（validateDraft 入参）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/dto/WidgetActionPayload.java
  description: 回传负载（请求体 widgetAction 反序列化目标）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/dto/WidgetTurnPlan.java
  description: 推进计划 record（可见消息文本 + 标注块）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/widget/dto/WidgetSaveRequest.java
  description: 保存请求体（API-003/004）
- path: fastrag-modules/fastrag-agent/src/main/java/com/fastrag/module/agent/middleware/WidgetMiddleware.java
  description: 清单注入中间件（order=14，runtimeState 松类型通道消费 + render_widget 注册）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/impl/AppServiceImpl.java
  description: 编排层扩展（app-chat 域文件；本特性改动 widgetAction 合成/清单通道 put/引擎块注入；所有权归 modules/app-chat.md#code）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/preset/DialogPresetResolver.java
  description: Bindings.widgets 装配（enabled=1 查询，双通道同源）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/preset/EffectiveDialogConfig.java
  description: Bindings 增 widgets 字段
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/AppService.java
  description: 双通道签名加 widgetAction 参数
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/AppChatController.java
  description: 旧通道请求透传 widgetAction（API-011）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/SessionCenterController.java
  description: 新通道请求透传 widgetAction（API-011）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/dto/AppChatRequest.java
  description: 请求体加 widgetAction 字段
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/controller/AppConfigController.java
  description: 第六类绑定四动词 + 候选端点（API-007~010）
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/AppConfigService.java
  description: 绑定服务接口四方法 + 候选
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/impl/AppConfigServiceImpl.java
  description: 绑定实现——上限 10/唯一对/候选口径（is_builtin=0 + 属主可见）
- path: init-scripts/schema.sql
  description: widget + app_widget_binding 全量 DDL（双处之一）
- path: fastrag-bootstrap/src/main/java/com/fastrag/config/SchemaInitializer.java
  description: 幂等增补 DDL（双处之二，initSchema 内独立 try 块）
- path: fastrag-modules/fastrag-application/src/test/java/com/fastrag/module/application/widget/WidgetTemplateValidatorTest.java
  description: S1 三档校验 12 断言
- path: fastrag-modules/fastrag-application/src/test/java/com/fastrag/module/application/widget/WidgetAssetServiceTest.java
  description: S2 生命周期 + S6 快照解析 11 断言
- path: fastrag-modules/fastrag-application/src/test/java/com/fastrag/module/application/widget/WidgetBuiltinSeederTest.java
  description: S3 播种幂等 3 断言
- path: fastrag-modules/fastrag-application/src/test/java/com/fastrag/module/application/widget/RenderWidgetToolExecutorTest.java
  description: S6 执行器真缝 7 断言
- path: fastrag-modules/fastrag-application/src/test/java/com/fastrag/module/application/widget/WidgetActionProcessorTest.java
  description: S7 回传推进 5 断言
- path: fastrag-modules/fastrag-agent/src/test/java/com/fastrag/module/agent/middleware/WidgetMiddlewareTest.java
  description: S5 装配 5 断言
- path: fastrag-modules/fastrag-application/src/test/java/com/fastrag/module/application/widget/WidgetSchemaSyncTest.java
  description: S9 DDL 双处 + 白名单双端守护 3 断言
```
