---
name: Contract Overview
summary: 跨模块契约聚合视图——手写 Modules 分解图（design 拥有）+ 工具投影的 IF-* 定义单元索引。
type: contract
id: DOC-CONTRACT
status: active
updated: 2026-11-03
---

# Contract Overview

> 本文件由 idd 工具维护聚合视图：`## Modules` 为设计阶段手写的分解图（design 拥有），`## IF-*` 节由 `idd index --write` 从各模块 `## Contract` 定义单元投影生成。
> 布局说明：本仓 20 个存量特性的设计文档位于旧布局 `docs/design/`（未被 5.4.0 工具发现层收录）；`docs/modules/` 为新链文档目录，随特性逐步迁入。

## Modules

系统按「能力域」拆分模块：每个模块隐藏一个变化源（存储协议、执行机制、治理规则），调用方只依赖其定义单元。当前新链对话审计、应用对话编排、首页门户前端、平台导航壳前端、解析策略与 Widget（后端+前端）七个模块落 `modules/`，其余特性待后续链推进时迁入。

**conversation-trace** — 对话执行的内容级步骤留痕与回看：引擎观察点采集（IF-001，隐藏增量协议/截断/批量写/失败容忍）与查询侧重放组装（IF-002，隐藏增量重放算法），附步骤表实体与内联留存清理 → modules/conversation-trace.md

**app-chat** — 应用对话的运行时编排：绑定能力装配与引擎驱动收尾，首轮检索策略裁决（IF-003，隐藏预检索执行/逐库回落/句柄续号/失败容忍，编排层只置开关） → modules/app-chat.md

**portal-frontend** — 首页门户层：PortalLayout 顶部导航壳承载门户首页/操作手册/产品动态三页，菜单数组驱动、内容全部构建期静态化，与平台壳平级并存（ADR-PORTAL-INDEPENDENT-SHELL；前端 profile，契约不投影 IF-*） → modules/portal-frontend.md

**nav-shell-frontend** — 平台壳导航层：单列宽侧栏承载对话/构建/资源/管理四段菜单与用户区，顶栏铃铛接线既有通知中心（唯一新路由 /notification）；导航展示树与权限树解耦、`menu:*` 键集冻结，既有路由与页面文件零改动（ADR-NAV-PERM-TREE-DECOUPLING；前端 profile，契约不投影 IF-*） → modules/nav-shell-frontend.md

**widget** — Widget 资产域与对话协议缝：三件套资产存储/三档校验/官方模板播种/卡片渲染快照解析（IF-004，隐藏白名单/属主过滤/快照组装）与回传负载推进（IF-005，隐藏尺寸护栏/消息合成），经独立 type 工具执行器与清单注入中间件两个既有缝接入对话循环（ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL / ADR-0011-WIDGET-TEMPLATE-DSL） → modules/widget.md

**widget-frontend** — Widget 前端组件族：WidgetCardRenderer 唯一渲染入口（递归渲染/降级占位/交互窗口/回传）三处消费（会话流/调试流/工作台预览），toolDisplay 增 `widget` 型路由 + 组件注册表与后端白名单测试钉一致，工作台三页与绑定面板（ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL；前端 profile，契约不投影 IF-*） → modules/widget-frontend.md

**parse-strategy** — 策略配置面唯一权威：CRUD 与绑定-重切一致性守卫、三层配置合并（IF-006~008），Excel 按行切片在解析期装配为多 TABLE 节点、复用原子分片路径并豁免溢出二次拆分（ADR-0012-EXCEL-ROW-SLICE-AT-PARSE） → modules/parse-strategy.md

## IF-001 — ConversationTraceRecorder {#if-001}

**Definition:** [`modules/conversation-trace.md#if-001`](modules/conversation-trace.md#if-001)

**Purpose:** 引擎侧唯一采集入口——一次运行的逐步观察进、traceId 出；调用方（AgentEngine 双执行入口）对增量存储、截断、缓冲、批量写与失败容忍完全无感。

**Signature:**
```java
// com.fastrag.module.agent.trace
public interface ConversationTraceRecorder {
    /** 引擎在 executeStream/executeSync 入口调用；一次运行恰 begin 一次 */
    TraceRun begin(TraceRunContext ctx);
}

// com.fastrag.module.agent.trace
public interface TraceRun {
    /** 每次模型调用完成后调用一次（引擎已持有请求消息与聚合后的响应） */
    void onLlmCall(LlmCallObservation obs);
    /** 每次工具执行完成后调用一次；rawOutput 为回传模型语义的截断前原始输出 */
    void onToolCall(ToolCallObservation obs);
    /** 运行终态调用（finally 位）：批量落表并返回本运行 traceId；落表失败吞错返回 null */
    String complete();
}
```

## IF-002 — TraceTimelineAssembler {#if-002}

**Definition:** [`modules/conversation-trace.md#if-002`](modules/conversation-trace.md#if-002)

**Purpose:** 查询侧唯一读取入口——把增量存储的步骤行重放组装为每步完整请求的时间线；增量协议不出此接口、不对前端暴露（REQ-CT-B04）。

**Signature:**
```java
// com.fastrag.module.agent.trace
public interface TraceTimelineAssembler {
    /** 按 step_seq 升序返回解析后的步骤；traceId 不存在或已被清理返回空列表 */
    List<ResolvedStep> assemble(String traceId);
}
```

## IF-003 — PreRetrievalMiddleware {#if-003}

**Definition:** [`modules/app-chat.md#if-003`](modules/app-chat.md#if-003)

**Purpose:** 首轮模型调用前同步执行绑定知识库预检索，把结果以 [cN] 句柄注入上下文——把「该不该检索」从模型自主决策 / 强制 tool_choice 的拉锯中拿掉，检索成本从一整轮 LLM 往返降为一次亚秒级检索调用。

**Signature:**
```java
// com.fastrag.module.agent.middleware
// AgentMiddleware 适配器，order = 4（KnowledgeBaseMiddleware 之后、SummarizationMiddleware 之前）
@Component
public class PreRetrievalMiddleware implements AgentMiddleware {
    /**
     * 仅在首轮 beforeModelCall 且 context.preRetrieveKb=TRUE 且 knowledges 非空时生效：
     * 以当轮用户查询调 KbOperationService.queryKnowledgeBase(kbId, query, 10)（逐绑定库），
     * 结果以 [cN] 句柄渲染为附加 system 块追加到首条 system 消息，句柄注册进共享
     * KB_HANDLE_MAP（与 query_kb 工具通道共享 KB_HANDLE_SEQ 连续编号）。
     * 检索失败或空结果：不注入、WARN、对话照常（fail-open）。
     */
    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                       List<ChatMessage> messages,
                                       List<ToolDefinition> tools);
}
```

## IF-004 — WidgetAssetService {#if-004}

**Definition:** [`modules/widget.md#if-004`](modules/widget.md#if-004)

**Purpose:** 资产域唯一深接口——快照解析（绑定集合 fail-closed 校验 + 数据结构校验 + 定格快照）、注入清单组装与三档保存校验；工具执行器、清单注入、工作台保存三条调用链共用。

**Signature:**
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

## IF-005 — WidgetActionProcessor {#if-005}

**Definition:** [`modules/widget.md#if-005`](modules/widget.md#if-005)

**Purpose:** 把用户卡片回传负载推进为一轮对话输入——尺寸护栏、可见用户消息文本合成、结构化标注块生成；编排层一行调用。

**Signature:**
```java
// com.fastrag.module.application.widget
public interface WidgetActionProcessor {
    /** 校验 payload ≤16KB 并合成推进计划；越限抛业务异常，对话不推进 */
    WidgetTurnPlan prepare(WidgetActionPayload action);
}
```

## IF-006 — StrategyConfigResolver {#if-strategyconfigresolver}

**Definition:** [`modules/parse-strategy.md#if-strategyconfigresolver`](modules/parse-strategy.md#if-strategyconfigresolver)

**Purpose:** 全管线唯一的策略配置读取口——把「代码默认 → 系统配置 → 策略 advanced」三层按固定优先级合并为类型化 `ParseStrategyConfig`；解析/分片/索引/AI分片/图谱/预览六类消费方不见 JSON 与双格式历史包袱。

**Signature:**
```java
// com.fastrag.module.knowledge.config
public class StrategyConfigResolver {
    /** 管线主入口：按策略 id 合并配置；id 为 null 仅系统默认层 */
    public ParseStrategyConfig resolve(String strategyId) { ... }
    /** 预览/预设场景：对未落库的临时策略对象合并配置 */
    public ParseStrategyConfig resolve(KbParseStrategy strategy) { ... }
}
```

## IF-007 — ParseMethodRegistry {#if-parsemethodregistry}

**Definition:** [`modules/parse-strategy.md#if-parsemethodregistry`](modules/parse-strategy.md#if-parsemethodregistry)

**Purpose:** 「文档类型 ↔ 扩展名 ↔ 解析方法」的单一事实源映射——meta 端点、保存校验、扩展名推断三方共享同一口径。

**Signature:**
```java
// com.fastrag.module.knowledge.parser
public enum ParseMethodRegistry {
    of(String code),                    // 方法代码查枚举
    resolveByExtension(String ext),     // 扩展名推断方法，未识别回退 default
    isSupported(String ext),            // 扩展名是否受系统支持
    isCompatible(String methodCode, String ext), // 保存校验双闸之一
    supportedExtensions()               // meta 端点数据源
}
```

## IF-008 — ParseStrategyBindingGuard {#if-parsestrategybindingguard}

**Definition:** [`modules/parse-strategy.md#if-parsestrategybindingguard`](modules/parse-strategy.md#if-parsestrategybindingguard)

**Purpose:** ADR-0001 的守卫面——绑定合法性、换绑三态、重切真源、元数据改绑拦截，维护「绑定 ⟺ 分片结果一致」不变式。

**Signature:**
```java
// 语义级契约；实现为 FileServiceImpl 私有方法（尚未成类，深化候选）
resolveRequestedStrategy(kbId, fileId, extension, requestedId) -> strategyId|null  // :207-220
reChunkFile 换绑段（三态校验与绑定写回）                                           // :405-419
rebuildProcessRequest(file)（重切真源覆盖快照）                                    // :226-247
update patch 守卫（拒绝元数据接口改绑）                                            // :642-649
```
