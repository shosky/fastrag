---
name: 对话审计
summary: 对话执行的内容级步骤留痕与回看：引擎观察点采集每次 LLM/工具调用为步骤行，终态批量落表；查询侧按追踪标识重放组装思考步骤时间线。
type: module
id: MOD-CONVERSATION-TRACE
status: draft
updated: 2026-10-15
satisfies: [REQ-CT-A01, REQ-CT-A02, REQ-CT-A03, REQ-CT-A04, REQ-CT-A05, REQ-CT-B01, REQ-CT-B02, REQ-CT-B03, REQ-CT-B04, REQ-CT-B05, REQ-CT-B06, REQ-CT-C01, REQ-CT-C02, REQ-CT-C03, REQ-CT-D01, REQ-CT-D02, REQ-CT-D03, REQ-CT-D04, REQ-CT-D05, REQ-CT-E01, REQ-CT-E02]
decisions: [ADR-0008-TRACE-ENGINE-CAPTURE-BATCH-PERSIST, ADR-0009-TRACE-INCREMENTAL-STEP-PROTOCOL]
profile: backend
hash: "e0ae8e5a992a"
summaryHash: "e0ae8e5a992a"
---

## 职责 {#responsibility}

对话审计的数据面全部职责：在引擎观察点把一次对话运行折叠为步骤行（增量协议、截断护栏、批量落表、失败吞错全部隐藏在采集接口之后），并在查询侧把步骤行重放组装为可回看的思考步骤时间线。

## 契约 {#contract}

### IF-001 — ConversationTraceRecorder {#if-001}

**用途：** 引擎侧唯一采集入口——一次运行的逐步观察进、traceId 出；调用方（AgentEngine 双执行入口）对增量存储、截断、缓冲、批量写与失败容忍完全无感。
**签名：**
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
**类型：**
```java
// com.fastrag.module.agent.trace
record TraceRunContext(
    String conversationId,   // 可空：独立智能体运行无会话（REQ-CT-A04）
    Long userId,
    String runId,            // 引擎运行标识；独立运行对应 agent_run
    String model) {}

// com.fastrag.ai.llm —— 复用既有消息类型，不另造协议
record LlmCallObservation(
    List<ChatMessage> requestMessages,  // 本步实际发送的消息（中间件改写后）
    String content,                     // 聚合后的返回文本
    String thinking,                    // 思考内容，可空
    List<ToolCallRequest> toolCalls,    // 模型请求的工具调用，可空
    Usage usage,                        // token 用量，可空
    String finishReason,
    Long durationMs,
    String error) {}                    // 非空 = 本次调用失败（success 隐含）

record ToolCallObservation(
    String toolName,
    String argumentsJson,
    String rawOutput,        // 截断前原始输出；护栏 32KB（REQ-CT-B05）
    Boolean success,
    String error,
    Long durationMs) {}
```
**错误模式：**
- **ERR-TRACE-PERSIST-FAILED** — trigger: 终态批量落表任何异常；recovery: 告警日志 + 丢弃本批，绝不向调用方抛出（REQ-CT-C02，对齐 agent NFR02）
**不变量：**
- Pre: TraceRun 生命周期严格嵌套于一次引擎运行——begin/complete 各恰一次，观察只发生在两者之间
- Post: complete 返回非空时，本运行全部步骤恰好一次落表且 step_seq 自 1 连续递增、与观察到达顺序一致（REQ-CT-A05）；运行期间对该表零写操作（REQ-CT-C01）
- Post: complete 返回 null 时数据库无本运行的任何残留半批数据
**Public vs hidden:** 调用方只见三个观察方法；增量 diff、截断护栏、内存缓冲、批量 INSERT、协议编码全部隐藏。

### IF-002 — TraceTimelineAssembler {#if-002}

**用途：** 查询侧唯一读取入口——把增量存储的步骤行重放组装为每步完整请求的时间线；增量协议不出此接口、不对前端暴露（REQ-CT-B04）。
**签名：**
```java
// com.fastrag.module.agent.trace
public interface TraceTimelineAssembler {
    /** 按 step_seq 升序返回解析后的步骤；traceId 不存在或已被清理返回空列表 */
    List<ResolvedStep> assemble(String traceId);
}
```
**类型：**
```java
// com.fastrag.module.agent.trace
record ResolvedStep(
    StepRow row,                          // 原始行投影：序号/类型/工具内容/元数据/截断标记（StepRow 为表行模型，见 entities）
    List<ChatMessage> resolvedRequest) {} // 仅 llm_call：重放组装后的完整请求；tool_call 为 null
```
**错误模式：**
- **ERR-TRACE-GAP** — trigger: 增量链断裂（基线行缺失或已被留存清理删除）；recovery: 该步 resolvedRequest 置 null 并置 gap 标记，不抛错
**不变量：**
- Post: 对完整链路，resolvedRequest 与引擎当次实际发送的消息逐字段相等（REQ-CT-B04 验收口径）
**Implemented by:** 本单元实现与 IF-001 编码侧同包共生（trace 包内 codec），协议写读两侧唯一配对，无跨模块所有权问题。

## 接口 {#interface}

### 审计查询端点（operation 域，普通接口）

**用途：** 管理端三级视图的 REST 面（REQ-CT-D01..D05），只读消费步骤表；请求/响应签名归 interfaces.md（契约阶段登记）。
**Signature:**
```java
// com.fastrag.module.operation —— 端点形状预览；返回统一 ApiResponse/PageResult
// 会话审计列表：时间/用户/关键词/模型/来源筛选，独立运行以输入摘要呈现来源（REQ-CT-D01/D04）
// 会话多轮明细：多轮消息 + 各轮追踪标识（REQ-CT-D02）
// 步骤时间线：注入 IF-002 组装，服务端返回完整请求视图（REQ-CT-D03）
```

## 实体 {#entities}

```sql
-- 步骤日志表（agent 域，写入方本模块；DDL 双处同步：init-scripts/schema.sql + SchemaInitializer）
CREATE TABLE agent_step_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)  NOT NULL,   -- 追踪标识，每运行一枚（REQ-CT-B01）
    run_id          VARCHAR(64)  NULL,       -- 引擎运行标识；独立运行对应 agent_run
    conversation_id VARCHAR(64)  NULL,       -- 关联合会话；独立运行无会话为空（REQ-CT-A04/D04）
    user_id         BIGINT       NULL,
    step_seq        INT          NOT NULL,   -- 运行内序号，自 1 连续递增
    step_type       VARCHAR(16)  NOT NULL,   -- llm_call | tool_call（字符串列，为 auxiliary 预留位）
    model           VARCHAR(128) NULL,       -- llm_call：模型标识
    tool_name       VARCHAR(128) NULL,       -- tool_call：工具名
    arguments_json  MEDIUMTEXT   NULL,       -- tool_call：调用参数 JSON
    request_increment_json MEDIUMTEXT NULL, -- llm_call：本步消息增量；首步=完整基线（ADR-0009-TRACE-INCREMENTAL-STEP-PROTOCOL）
    request_meta    VARCHAR(255) NULL,       -- llm_call：消息条数/总字符快览元数据（REQ-CT-B03）
    response_json   MEDIUMTEXT   NULL,       -- llm_call：content/thinking/tool_calls/usage/finish_reason
    output_text     MEDIUMTEXT   NULL,       -- tool_call：截断前原始输出，32KB 护栏
    truncated       TINYINT(1)   NOT NULL DEFAULT 0,  -- 任一内容列被截断（REQ-CT-B05）
    success         TINYINT(1)   NULL,       -- tool_call 成败；llm_call 失败以 error 非空表达
    error           VARCHAR(1024) NULL,
    duration_ms     BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_trace_seq (trace_id, step_seq),        -- 时间线查询（IF-002 主路径）
    KEY idx_conv_time (conversation_id, created_at), -- 会话视角多轮回看（REQ-CT-D02）
    KEY idx_created (created_at)                     -- 留存清理批删（REQ-CT-E02）
);

-- 既有消息表加列（追踪标识回填，REQ-CT-B02；SchemaInitializer addColumnIfNotExists 模式）
ALTER TABLE app_conversation_message
    ADD COLUMN trace_id VARCHAR(64) NULL,   -- 当轮运行的追踪标识；由终态落库路径回填
    ADD KEY idx_trace (trace_id);
```

- 关系：app_conversation_message.trace_id N—1 agent_step_log.trace_id（按 trace_id 取步骤集）；agent_step_log.conversation_id 可空指向 app_conversation。
- 备注：内容列统一 MEDIUMTEXT + truncated 标记；双处同步列入验收（守护测试对齐 SkillDdlParityTest 先例）。

## 逻辑 {#logic}

<!-- implement 阶段填写：采集观察点接线、增量 diff、终态批量写、重放算法的非显然分支 -->

## 边界设计 {#seam}

**什么在变：** 步骤的存储介质与内容协议。当前实现 = MySQL MEDIUMTEXT + 增量 JSON；未来若换存储或改协议，只动 trace 包内的编解码对（写侧编码在 IF-001 实现、读侧解码在 IF-002 实现，同包共生防漂移）。按单适配器不立真缝的规则，这里只标注假想缝、不建 adapter 接口——真缝等第二个实现出现再形式化。

**稳定侧：** 引擎只依赖 IF-001 的三个观察方法；查询方（operation）只依赖 IF-002 一个组装方法。两侧调用方对协议内部零感知。

**对既有代码的三处触碰（均为既有模式顺延，非新缝）：**
1. 编排器把 conversationId 写入 `runtimeState`（沿用 HISTORY_KEY 的请求级键先例）→ REQ-CT-A04；
2. 引擎 onComplete 回调扩 traceId 参数，`persistAnswer` 落库时回填 `app_conversation_message.trace_id` → REQ-CT-B02；
3. executeSync 同构接线覆盖独立智能体运行 → REQ-CT-A01。

**明确不作为缝的东西：** LlmService 保持完全无感（采集在引擎观察点，模型客户端不动）；RunEventPublisher 与 SSE 事件通道不动（易失事件与本特性是互补关系，合流只会把审计绑上推送语义）。

**浅模块内联（S）：** 留存清理器未通过 deletion test——其复杂度（读配置 + 批删循环）仅约 30 行且无处再現，内联为 agent trace 包内 `@Scheduled` 方法，不立接口、不立适配器；保留天数配置缺省 180（REQ-CT-E01/E02）。

**可深化方向：** 会话属主自看、辅助 LLM 调用（auxiliary 步骤型）、审计导出都在现有接口面内扩展——step_type 字符串列与 ResolvedStep 的可空字段已预留，无需改契约。

## 追溯 {#trace}

<!-- implement 阶段填写：模块/缝/深化选择 → REQ-CT-* 映射 -->

## 代码 {#code}

<!-- implement 阶段填写：新增文件（trace 包七件 + operation 两件）与 DDL 双处触碰的行级登记；引擎/编排器既有文件触碰点见「边界设计」，归属各自既有模块 -->
