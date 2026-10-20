---
name: App Chat
summary: 应用对话的运行时编排——绑定能力装配、首轮检索策略裁决与引擎驱动收尾。
type: module
id: MOD-APP-CHAT
profile: backend
status: active
updated: 2026-10-20
satisfies: []
decisions: [ADR-PARALLEL-PRE-RETRIEVAL]
declares: [ERR-PRE-RETRIEVE-FAILED, ERR-PRE-RETRIEVE-EMPTY]
hash: "d235b4b0bdef"
summaryHash: "d235b4b0bdef"
---

## 职责 {#responsibility}

应用对话的运行时编排：把应用绑定的能力（知识库、工具、技能、MCP）装配进引擎上下文，裁决首轮检索策略（预检索注入），驱动 AgentEngine 工具循环并收尾落库（回答、思考、引用）。

## 契约 {#contract}
### IF-003 — PreRetrievalMiddleware {#if-003}

**用途：** 首轮模型调用前同步执行绑定知识库预检索，把结果以 [cN] 句柄注入上下文——把「该不该检索」从模型自主决策 / 强制 tool_choice 的拉锯中拿掉，检索成本从一整轮 LLM 往返降为一次亚秒级检索调用。

**签名：**
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
**类型：**
```java
// com.fastrag.module.agent.context
// BaseContext 新增字段（编排层裁决置位；null/false 均不预检索）：
private transient Boolean preRetrieveKb;
```
**错误模式：**
- ERR-PRE-RETRIEVE-FAILED — trigger: KbOperationService 异常或超时；recovery: 不注入、WARN 日志、对话照常
- ERR-PRE-RETRIEVE-EMPTY — trigger: 检索无命中；recovery: 不注入（省 token；模型仍可经 query_kb 二次检索）

**不变量：**
- Pre: 仅首轮生效；context.preRetrieveKb=TRUE；knowledges 非空
- Post: 注入文本截断 ≤ 5000 字符（与 TOOL_OUTPUT_MAX 同口径）；句柄编号与同请求内 query_kb 调用连续共享；预检索任何失败不得阻断对话

**Public vs hidden:** 调用方（引擎中间件链）只看到标准 AgentMiddleware 钩子；检索执行、逐库回落、句柄续号、截断预算、豁免联动、失败容忍全部隐藏。

## 接口 {#interface}
无

## 实体 {#entities}
无（不新增表：检索留痕走既有 kb_retrieval_log，引用来源走 app_conversation_message.references 投影）

## 逻辑 {#logic}

```text
IF-003 PreRetrievalMiddleware.beforeModelCall(context, messages, tools):
  IF context.preRetrieveKb ≠ TRUE OR knowledges 为空: RETURN context
  query ← messages 中最后一条非空 user 消息
  IF query 空: RETURN context
  TRY
    chunks ← 逐绑定库 queryKnowledgeBase(kbId, query, 10) 合并（chunk 补 kb_id）
    IF chunks 空: RETURN context                          // ERR-PRE-RETRIEVE-EMPTY
    FOR chunk IN chunks:
      IF block.length > 5000: BREAK
      handle ← "c" + SEQ.incrementAndGet()                // 与 query_kb 共享连续编号
      handleMap[handle] ← {kbId, fileId, chunkIndex}
      block  ← block + "[handle] source (相关性) | content"
      sources ← sources + 引用投影条目
    首条 system 消息 ← 追加【知识库预检索】块
    runtimeState.preRetrievalSources ← sources
  CATCH: WARN；RETURN context                             // ERR-PRE-RETRIEVE-FAILED（fail-open）
  RETURN context

编排层（AppServiceImpl.executeDialog）:
  绑定知识库非空 → context.preRetrieveKb ← shouldPreRetrieve(query)
  shouldPreRetrieve: NOT(问候语 OR 能力问句 OR 纯算式 OR 空查询)
  收尾回调: sources ← mergePreRetrievalSources(runtimeState 预检索来源,
            extractKbSources(toolCalls, handleMap))      // 句柄序：预检索在前、工具在后
            → sources 事件 → references 落库
```

**模块交互**：编排层（AppServiceImpl）→ AgentMiddlewareChain → PreRetrievalMiddleware → KbOperationService（retrieval 域适配，混合检索内部自含向量/全文/图谱）；PreRetrievalMiddleware ↔ runtimeState（与 KnowledgeToolExecutor 共享句柄表/序号，跨模块契约由 PreRetrievalMiddlewareTest 与 KnowledgeToolExecutorTest 双侧钉住）；编排层收尾 ← runtimeState.preRetrievalSources + extractKbSources。

**Decisions & Deviations**：
- order=13 而非设计稿的 4：4 号位被 SkillsMiddleware 占用；语义不变（KB 提示之后、首轮模型调用之前），属位置微调非机制偏差。
- `LlmService` 8 参 `streamChatWithTools` 重载与 `ChatRequest.toolChoice` Object 类型保留为通用传输能力；ADR 移除的是强制机制（开关/常量/决策器/降级包装），引擎现恒传 auto。
- 预检索来源经 runtimeState（`preRetrievalSources`）交编排层与工具通道来源按句柄序合并——复用既有 sources 事件与 references 投影，前端零改动。

## 边界设计 {#seam}

**预检索触发策略（什么问题值得预检索）**——变化点在编排层：`AppServiceImpl` 以确定式规则（问候语 / 纯算式 / 能力问句豁免，其余预检索）置位 `BaseContext.preRetrieveKb`。未来若规则膨胀，可替换为意图分类器，接口不变——豁免判定住在编排层而非中间件，正是为了让这个替换不触碰引擎。

**检索执行**——`KbOperationService` 适配（retrieval 域唯一实现，混合检索内部自含向量/全文/图谱通道）。预检索与 query_kb 工具共用同一适配，保证两条通道的检索行为一致。

**注入形态**——system 附加块（与 HistoryMiddleware 同型）是当前适配；若前端需要独立呈现「预检索卡片」，可在 runtimeState 增加结构化通道而不改本接口。

**稳定侧**：引擎工具循环与 [cN] 句柄协议。深化机会：直聊（会话优先通道）复用同一开关接入同一中间件，仅需编排层置位。

## 追溯 {#trace}

- [IF-003 PreRetrievalMiddleware 注入/句柄续号/fail-open] → [ADR-PARALLEL-PRE-RETRIEVAL]
- [shouldPreRetrieve 豁免规则（问候/算式/能力问句）] → [ADR-PARALLEL-PRE-RETRIEVAL]
- [R4 强制 tool_choice 机制移除（forceFirstRetrieval/FORCED_QUERY_KB_TOOL_CHOICE/resolveToolChoice/降级包装）] → [ADR-PARALLEL-PRE-RETRIEVAL 取代声明]
- [spec REQ] → [GAP: 检索策略尚无 spec REQ（app-center/agent 未覆盖该横切面），建议 /idd-spec 补立后回填]

## 代码 {#code}

```yaml
- path: fastrag-modules/fastrag-agent/src/main/java/com/fastrag/module/agent/middleware/PreRetrievalMiddleware.java
  description: 并行预检索中间件——首轮模型调用前逐绑定库检索，[cN] 句柄注入 system 块并注册共享句柄表（IF-003）
- path: fastrag-modules/fastrag-agent/src/test/java/com/fastrag/module/agent/middleware/PreRetrievalMiddlewareTest.java
  description: 注入/句柄跨通道连续性/多库/fail-open/空结果/截断预算七类回归
- path: fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/service/impl/AppServiceImpl.java
  description: 应用对话编排——能力装配、预检索开关裁决（shouldPreRetrieve 豁免规则）、sources 合并与对话收尾落库
- path: fastrag-modules/fastrag-application/src/test/java/com/fastrag/module/application/service/impl/AppServiceImplExtractKbSourcesTest.java
  description: 引用投影与预检索豁免判定回归（13 断言）
- path: fastrag-modules/fastrag-agent/src/main/java/com/fastrag/module/agent/executor/AgentEngine.java
  description: 引擎工具循环；本轮移除 R4 强制 tool_choice 机制（常量/决策器/降级包装）
- path: fastrag-modules/fastrag-agent/src/test/java/com/fastrag/module/agent/executor/AgentEngineTest.java
  description: 引擎回归；R4 强制检索用例随机制移除，只读并行等既有用例 21/21
- path: fastrag-modules/fastrag-agent/src/main/java/com/fastrag/module/agent/context/BaseContext.java
  description: 引擎运行时上下文；forceFirstRetrieval → preRetrieveKb 开关替换
```
