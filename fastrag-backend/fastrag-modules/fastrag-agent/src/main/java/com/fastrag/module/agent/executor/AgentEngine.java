package com.fastrag.module.agent.executor;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.ai.model.*;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.util.JsonSchemaValidator;
import com.fastrag.common.util.OutputBudget;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.event.RunEventPublisher;
import com.fastrag.module.agent.middleware.AgentMiddlewareChain;
import com.fastrag.module.agent.middleware.ModelRetryMiddleware;
import com.fastrag.module.agent.middleware.ToolCallInterceptor;
import com.fastrag.module.tools.executor.*;
import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 智能体核心执行引擎，统一的流式/同步执行引擎，支持工具调用循环、中间件链、thinking提取和SSE事件推送。
 *
 * <p>核心职责：
 * <ul>
 *   <li>实现Agent的核心执行循环：调用LLM -> 解析工具调用 -> 执行工具 -> 将结果反馈LLM -> 重复直到获得最终回答</li>
 *   <li>在执行前后集成中间件链，实现工具注册、上下文增强、响应拦截等横切关注点</li>
 *   <li>支持流式执行（SSE推送）和同步执行（返回AgentResult）两种模式</li>
 *   <li>提取LLM的thinking内容（如DeepSeek/Qwen3的思考过程）并单独推送</li>
 *   <li>处理工具调用失败的场景（连续失败超过3次时注入系统提示要求LLM停止重试）</li>
 * </ul></p>
 *
 * <p>两种执行模式：
 * <ul>
 *   <li>executeStream - 流式执行，用于应用对话场景，通过SseEmitter实时推送事件（init/message/thinking/tool_call/tool_result/end/error等）</li>
 *   <li>executeSync - 同步执行，用于Agent/子Agent场景，返回AgentResult对象，事件记录到RunEventPublisher</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>执行流程：解析工具定义 -> 构建消息列表 -> 前置中间件（注册新工具）-> LLM调用循环（最多MAX_ITERATIONS=15轮）-> 后置中间件 -> 工具执行</li>
 *   <li>工具执行：参数解析 -> JSON Schema验证 -> 查找ToolExecutor -> 执行 -> 截断输出到5000字符反馈给LLM</li>
 *   <li>中间件拦截：通过AgentMiddlewareChain.getToolCallInterceptor检查是否有中间件要拦截工具调用（如SubAgentMiddleware拦截task工具）</li>
 *   <li>流式消费：使用CountDownLatch阻塞等待Flux流完成，分类处理THINKING/CONTENT/TOOL_CALL_DELTA/FINISH事件</li>
 *   <li>JSON修复：当LLM生成包含未转义双引号的无效JSON参数时，自动尝试修复</li>
 *   <li>到达最大迭代次数时返回兜底提示信息</li>
 * </ul></p>
 *
 * <p>与其他模块的交互：
 * <ul>
 *   <li>依赖LlmService（fastrag-ai模块）进行LLM调用</li>
 *   <li>依赖ToolRegistry和ToolExecutorFactory（fastrag-tools模块）进行工具解析和执行</li>
 *   <li>依赖AgentMiddlewareChain执行中间件链</li>
 *   <li>依赖RunEventPublisher记录运行事件</li>
 * </ul></p>
 *
 * @see AgentMiddlewareChain 中间件链
 * @see AgentResult 同步执行结果
 * @see LlmStreamResult 流式执行结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEngine {

    private final LlmService llmService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorFactory executorFactory;
    private final RunEventPublisher eventPublisher;
    private final AgentMiddlewareChain middlewareChain;
    private final ObjectMapper objectMapper;

    @Value("${ai.gateway.timeout:30}")
    private int gatewayTimeoutSeconds = 30;

    /** 单次 LLM 流式调用最大等待时长（秒）；需小于 SSE emitter 超时（默认 5 分钟） */
    @Value("${ai.gateway.stream-timeout:240}")
    private long streamTimeoutSeconds;

    /** 最大工具调用轮次 */
    private static final int MAX_ITERATIONS = 15;

    /**
     * 轮次耗尽且无最终回答时的兜底文案（QF-4）：流式/同步共用，保证两条执行路径语义一致。
     */
    static String stepsExhaustedFallback(int maxSteps) {
        return "抱歉，执行已达到最大步数限制（" + maxSteps + "步），未能完成回答。"
                + "可能原因是工具执行失败导致无法获取所需数据。"
                + "请尝试简化问题或检查数据源连接是否正常。";
    }

    /** 工具输出截断长度 */
    private static final int TOOL_OUTPUT_MAX = 5000;

    // ==================== I8：只读工具并行执行 ====================

    /** I8：无副作用工具白名单——单轮全为这些工具且 ≥2 个调用时并行执行。 */
    private static final Set<String> READONLY_PARALLEL_TOOLS = Set.of(
            "read_file", "list_directory", "grep", "glob",
            "list_kbs", "query_kb", "list_tables", "describe_table");

    /** I8：并行执行并发上限（信号量）。 */
    private static final int PARALLEL_MAX_CONCURRENCY = 8;

    /** I8：只读批次执行线程池（守护线程，不阻塞进程退出）。 */
    private final java.util.concurrent.ExecutorService toolBatchExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(PARALLEL_MAX_CONCURRENCY, r -> {
                Thread t = new Thread(r, "agent-tool-batch");
                t.setDaemon(true);
                return t;
            });

    /** I8：单次工具执行结果（含引擎侧计时）。 */
    private record ToolExecution(ToolResult result, long durationMs) {
    }

    @jakarta.annotation.PreDestroy
    void shutdownToolBatchExecutor() {
        toolBatchExecutor.shutdownNow();
    }

    /**
     * I8：全只读白名单轮判定（≥2 个调用且全部命中白名单才并行；调用方已保证批次内全部可执行）。
     */
    static boolean isReadOnlyParallelRound(List<ChatMessage.ToolCall> calls) {
        if (calls == null || calls.size() < 2) {
            return false;
        }
        for (ChatMessage.ToolCall tc : calls) {
            String name = tc.getFunction() != null ? tc.getFunction().getName() : null;
            if (name == null || !READONLY_PARALLEL_TOOLS.contains(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * I8：工具批次统一执行入口——并行/串行由 {@link #isReadOnlyParallelRound} 判定，
     * 结果严格按入参顺序返回（toolCallId 对齐回填由调用方按序消费保证）。
     */
    private List<ToolExecution> executeToolBatch(List<ChatMessage.ToolCall> calls,
                                                 List<ToolDefinition> defs,
                                                 BaseContext context, AgentRun run) {
        if (calls.isEmpty()) {
            return List.of();
        }
        if (!isReadOnlyParallelRound(calls)) {
            // 写类与混合轮保持串行
            List<ToolExecution> results = new ArrayList<>(calls.size());
            for (int i = 0; i < calls.size(); i++) {
                results.add(executeOne(defs.get(i), calls.get(i), context, run));
            }
            return results;
        }
        log.info("[AgentEngine] I8 read-only parallel round: {} tool calls, max {} concurrent",
                calls.size(), PARALLEL_MAX_CONCURRENCY);
        java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(PARALLEL_MAX_CONCURRENCY);
        List<java.util.concurrent.CompletableFuture<ToolExecution>> futures = new ArrayList<>(calls.size());
        for (int i = 0; i < calls.size(); i++) {
            ChatMessage.ToolCall tc = calls.get(i);
            ToolDefinition def = defs.get(i);
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    permits.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ToolExecution(
                            ToolResult.error("interrupted while awaiting parallel slot", 0), 0L);
                }
                try {
                    return executeOne(def, tc, context, run);
                } finally {
                    permits.release();
                }
            }, toolBatchExecutor));
        }
        List<ToolExecution> results = new ArrayList<>(calls.size());
        for (java.util.concurrent.CompletableFuture<ToolExecution> f : futures) {
            try {
                results.add(f.get(10, TimeUnit.MINUTES));
            } catch (Exception e) {
                log.warn("[AgentEngine] Parallel tool execution failed: {}", e.getMessage());
                results.add(new ToolExecution(
                        ToolResult.error("parallel tool execution failed: " + e.getMessage(), 0), 0L));
            }
        }
        return results;
    }

    /** I8：单次执行（含计时与兜底——执行器抛出的运行时异常转为错误结果，不拖垮整轮）。 */
    private ToolExecution executeOne(ToolDefinition toolDef, ChatMessage.ToolCall tc,
                                     BaseContext context, AgentRun run) {
        long start = System.currentTimeMillis();
        try {
            return new ToolExecution(executeTool(toolDef, tc, context, run),
                    System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            log.warn("[AgentEngine] Tool '{}' execution threw: {}", tc.getFunction().getName(), e.getMessage());
            return new ToolExecution(
                    ToolResult.error("Execution failed: " + e.getMessage(),
                            (int) (System.currentTimeMillis() - start)),
                    System.currentTimeMillis() - start);
        }
    }

    // ==================== 流完成回调接口 ====================
    @FunctionalInterface
    public interface StreamCompleteCallback {
        void accept(String answer, String thinkingContent, String toolCallsJson);
    }

    // ==================== 流式执行（应用对话场景） ====================

    /**
     * 流式执行智能体：支持工具调用循环 + 中间件链 + thinking 提取 + SSE 事件推送。
     *
     * @param context      运行时上下文（需已设置 modelConfig）
     * @param emitter      SSE 发射器（已设置 timeout 和 callback）
     * @param modelConfig  模型配置（apiUrl, apiKey, enableThinking, temperature）
     * @param run          AgentRun 记录（可为 null，用于事件记录）
     * @param onComplete   流完成回调（可选，参数为 answer + thinkingContent + toolCallsJson）
     */
    public void executeStream(BaseContext context,
                              SseEmitter emitter,
                              ModelConfig modelConfig,
                              AgentRun run,
                              StreamCompleteCallback onComplete) {
        long totalStart = System.currentTimeMillis();
        StringBuilder fullAnswer = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        List<Map<String, Object>> toolCallRecords = new ArrayList<>();
        Map<String, Integer> toolFailCount = new HashMap<>();
        int totalIterations = 0;

        try {
            // 1. 解析工具定义
            List<ToolDefinition> tools = toolRegistry.resolveTools(context.getTools());
            // M3-3（D14）：install_skill 条件注入——引擎侧过滤（绑定技能非空或沙箱启用才保留）
            filterInstallSkill(context, tools);
            List<ChatRequest.ToolDefinition> openaiTools = toolRegistry.toOpenAITools(tools);
            log.info("[AgentEngine] executeStream start: model={}, tools={}", modelConfig.getModel(), tools.size());

            // 2. 构建初始消息列表
            List<ChatMessage> messages = new ArrayList<>();
            if (StrUtil.isNotBlank(context.getSystemPrompt())) {
                messages.add(new ChatMessage("system", context.getSystemPrompt()));
            }
            String userQuery = extractQuery(run);
            messages.add(new ChatMessage("user", userQuery));

            // 3. 发送 init 事件
            sendSseEvent(emitter, "init", Map.of("status", "loading"));

            // 4. 前置中间件链（中间件会往 tools 列表中添加新工具）
            middlewareChain.executeBefore(context, messages, tools);

            // 中间件可能已添加新工具到 tools 列表，重新生成 openaiTools
            openaiTools = toolRegistry.toOpenAITools(tools);
            log.info("[AgentEngine] After middleware: total tools={}", tools.size());

            // 5. 工具调用循环（maxSteps 可通过 context 配置）
            int maxIterations = (context.getMaxSteps() != null && context.getMaxSteps() > 0)
                    ? context.getMaxSteps() : MAX_ITERATIONS;
            // I5：防卡死守卫（length 截断/空响应/复读），每 run 一个实例
            RoundGuard guard = new RoundGuard();
            // I5：守卫判定终止时若尚无任何输出，走兜底文案
            boolean guardStopped = false;
            for (int round = 0; round < maxIterations; round++) {
                totalIterations++;
                log.info("[AgentEngine] Iteration {}/{}", round + 1, maxIterations);

                // I6：每轮循环边界（本轮消息回灌后、下轮模型调用前）——SummarizationMiddleware 调用点
                middlewareChain.executeRoundBoundary(context, messages, tools);

                // M2-3（D7）：技能激活追加工具依赖后，本轮模型调用前重算工具
                List<ChatRequest.ToolDefinition> recomputed = recomputeToolsIfDirty(context, tools);
                if (recomputed != null) {
                    openaiTools = recomputed;
                }

                // 打印当前消息列表摘要
                logMessagesSummary(messages, round);

                // 5a/5b. 调用 LLM（流式 + 工具 + thinking；I7：瞬时异常指数退避重试）
                LlmStreamResult result = consumeStreamWithRetry(
                        context, modelConfig, openaiTools, messages,
                        emitter, fullAnswer, thinkingContent);

                // 5c. 后置中间件链
                middlewareChain.executeAfter(context, result.getResponse());

                // 5d. I5 防卡死守卫：裁决本轮走向（执行工具 / 注入提示重试 / 结束）
                RoundGuard.Action action = guard.evaluate(result.getResponse());
                if (action == RoundGuard.Action.EXECUTE_TOOLS) {
                    log.debug("[AgentEngine] Round {} proceeds to tool execution", round + 1);
                } else if (action == RoundGuard.Action.FINISH) {
                    if (result.getResponse().hasToolCalls()) {
                        log.warn("[AgentEngine] Guard stopped tool loop: consecutive length truncation (iterations={})",
                                totalIterations);
                    } else {
                        log.info("[AgentEngine] LLM returned final answer (iterations={})", totalIterations);
                    }
                    guardStopped = fullAnswer.isEmpty();
                    break;
                } else {
                    // RETRY_LENGTH / RETRY_EMPTY / RETRY_REPEAT：注入 system 提示后重试
                    messages.add(new ChatMessage("system", action.systemMessage));
                    log.info("[AgentEngine] Round guard retry ({}): injected system nudge", action);
                    continue;
                }

                // 5e. 将 assistant 消息（含 tool_calls）加入历史
                // content 设置为 null（NON_NULL 会排除它），因为对于 tool_calls 消息
                // 某些 API（如 SiliconFlow）不允许 content 为空字符串
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(null);
                assistantMsg.setToolCalls(result.getResponse().getToolCalls());
                messages.add(assistantMsg);
                log.debug("[AgentEngine] Added assistant message with {} toolCalls, content=null(NON_NULL excluded)",
                        result.getResponse().getToolCalls().size());
                for (ChatMessage.ToolCall tc : result.getResponse().getToolCalls()) {
                    log.debug("[AgentEngine]   toolCall: id={}, name={}, argsLen={}",
                            tc.getId(),
                            tc.getFunction() != null ? tc.getFunction().getName() : "null",
                            tc.getFunction() != null && tc.getFunction().getArguments() != null ? tc.getFunction().getArguments().length() : 0);
                }

                // 5f. 执行每个工具调用
                // I8：先分类（拦截/缺失 inline 处理 + tool_call 事件），可执行批次统一执行——
                // 全只读白名单轮 ≥2 个调用时并行（CompletableFuture + 信号量 8 并发），
                // 写类与混合轮保持串行；结果按原顺序（toolCallId 对齐）回填。
                List<ChatMessage.ToolCall> batchCalls = new ArrayList<>();
                List<ToolDefinition> batchDefs = new ArrayList<>();
                for (ChatMessage.ToolCall tc : result.getResponse().getToolCalls()) {
                    // 中间件拦截（如 SkillsMiddleware 激活技能）
                    ToolCallInterceptor interceptor =
                            middlewareChain.getToolCallInterceptor(context, tc);
                    if (interceptor != null && interceptor.isSkip()) {
                        // 中间件处理了该工具调用
                        ChatMessage interceptorMsg = interceptor.getResultMessage();
                        if (interceptorMsg.getName() == null && tc.getFunction() != null) {
                            interceptorMsg.setName(tc.getFunction().getName());
                        }
                        messages.add(interceptorMsg);
                        // 推送 tool_result 事件
                        sendSseEvent(emitter, "tool_result", Map.of(
                                "id", tc.getId(),
                                "name", tc.getFunction().getName(),
                                "success", true,
                                "output", truncate(interceptor.getResultMessage().getContent(), TOOL_OUTPUT_MAX),
                                "durationMs", 0,
                                "intercepted", true
                        ));
                        // 记录中间件拦截的工具调用
                        toolCallRecords.add(Map.of(
                                "id", tc.getId(),
                                "name", tc.getFunction().getName(),
                                "arguments", tc.getFunction().getArguments(),
                                "result", Map.of(
                                        "success", true,
                                        "output", truncate(interceptor.getResultMessage().getContent(), TOOL_OUTPUT_MAX),
                                        "durationMs", 0
                                )
                        ));
                        continue;
                    }

                    // 发送 tool_call 事件
                    sendSseEvent(emitter, "tool_call", Map.of(
                            "id", tc.getId(),
                            "name", tc.getFunction().getName(),
                            "arguments", tc.getFunction().getArguments(),
                            "index", result.getResponse().getToolCalls().indexOf(tc),
                            "status", "calling"
                    ));

                    // 查找工具定义
                    ToolDefinition toolDef = findTool(tools, tc.getFunction().getName());
                    if (toolDef == null) {
                        String errOutput = "Error: Tool '" + tc.getFunction().getName() + "' not found";
                        sendSseEvent(emitter, "tool_result", Map.of(
                                "id", tc.getId(), "name", tc.getFunction().getName(),
                                "success", false, "output", errOutput, "durationMs", 0));
                        ChatMessage toolNotFoundMsg = new ChatMessage("tool", errOutput, tc.getId());
                        toolNotFoundMsg.setName(tc.getFunction().getName());
                        messages.add(toolNotFoundMsg);
                        // 记录未找到的工具调用
                        toolCallRecords.add(Map.of(
                                "id", tc.getId(),
                                "name", tc.getFunction().getName(),
                                "arguments", tc.getFunction().getArguments(),
                                "result", Map.of(
                                        "success", false,
                                        "output", errOutput,
                                        "durationMs", 0
                                )
                        ));
                        // 跟踪工具失败
                        toolFailCount.merge(tc.getFunction().getName(), 1, Integer::sum);
                        continue;
                    }

                    // I8：可执行调用进入批次（后面统一执行，写类/混合轮串行、全只读轮并行）
                    batchCalls.add(tc);
                    batchDefs.add(toolDef);
                }

                // I8：批次执行——并行/串行由白名单判定；结果严格按 batchCalls 顺序（toolCallId 对齐）
                List<ToolExecution> executions = executeToolBatch(batchCalls, batchDefs, context, run);
                for (int i = 0; i < batchCalls.size(); i++) {
                    ChatMessage.ToolCall tc = batchCalls.get(i);
                    ToolResult toolResult = executions.get(i).result();
                    long toolDuration = executions.get(i).durationMs();

                    // 发送 tool_result 事件
                    Map<String, Object> resultData = new LinkedHashMap<>();
                    resultData.put("id", tc.getId());
                    resultData.put("name", tc.getFunction().getName());
                    resultData.put("success", toolResult.isSuccess());
                    resultData.put("output", truncate(toolResult.getOutput(), TOOL_OUTPUT_MAX));
                    resultData.put("durationMs", toolDuration);
                    if (toolResult.getError() != null) {
                        resultData.put("error", toolResult.getError());
                    }
                    // 结构化通道（app-chat-optimize 二批）：display_type+tool_data 直达前端专用渲染器
                    if (toolResult.getStructured() != null && !toolResult.getStructured().isEmpty()) {
                        resultData.put("data", toolResult.getStructured());
                    }
                    sendSseEvent(emitter, "tool_result", resultData);

                    // 加入消息历史（I3 注释修正：SSE tool_result 与事件通道一致只存截断后内容——
                    // 5000 字符预算。旧注释「前端仍然可查看完整结果」失实：通道层不做全量承诺，
                    // 需要完整输出的工具应自行分页或落盘后返回引用）
                    String resultContent = toolResult.isSuccess()
                            ? toolResult.getOutput() : "Error: " + toolResult.getError();
                    // 工具返回数据给 LLM 时截断到 5000 字符
                    // 注意: tool 消息需要包含 name 字段（SiliconFlow/Qwen 等要求）
                    ChatMessage toolResultMsg = new ChatMessage("tool",
                            truncate(resultContent, 5000), tc.getId());
                    toolResultMsg.setName(tc.getFunction().getName());
                    messages.add(toolResultMsg);
                    log.debug("[AgentEngine] Added tool result message: role=tool, toolCallId={}, name={}, contentLen={}",
                            tc.getId(), tc.getFunction().getName(),
                            truncate(resultContent, 5000).length());

                    // 事件记录
                    if (run != null && run.getId() != null) {
                        publishToolCallEvent(run.getId(), tc, toolResult, toolDuration);
                    }
                    // 记录工具调用（structured 一并入库，历史回看同构）
                    Map<String, Object> resultPayload = new LinkedHashMap<>();
                    resultPayload.put("success", toolResult.isSuccess());
                    resultPayload.put("output", toolResult.isSuccess()
                            ? truncate(toolResult.getOutput(), TOOL_OUTPUT_MAX)
                            : (toolResult.getError() != null ? toolResult.getError() : "unknown error"));
                    resultPayload.put("durationMs", toolDuration);
                    if (toolResult.getStructured() != null && !toolResult.getStructured().isEmpty()) {
                        resultPayload.put("data", toolResult.getStructured());
                    }
                    toolCallRecords.add(Map.of(
                            "id", tc.getId(),
                            "name", tc.getFunction().getName(),
                            "arguments", tc.getFunction().getArguments(),
                            "result", resultPayload
                    ));
                    // 跟踪工具失败次数
                    if (!toolResult.isSuccess()) {
                        String toolName = tc.getFunction().getName();
                        toolFailCount.merge(toolName, 1, Integer::sum);
                    }
                }

                // 5g. 如果某个工具连续失败次数过多，主动告诉 LLM 停止重试
                if (!toolFailCount.isEmpty()) {
                    int maxFail = toolFailCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                    if (maxFail >= 3) {
                        String failMsg = "系统提示：以下工具已连续失败 " + maxFail + " 次，"
                                + "请不要再重复调用这些工具，直接基于已有信息回答用户问题："
                                + toolFailCount.entrySet().stream()
                                    .map(e -> e.getKey() + "(" + e.getValue() + "次)")
                                    .collect(java.util.stream.Collectors.joining(", "));
                        messages.add(new ChatMessage("system", failMsg));
                        log.warn("[AgentEngine] Tool failure threshold reached, injecting stop-retry message: {}", failMsg);
                        // 重置计数器，避免重复注入
                        toolFailCount.clear();
                    }
                }

                // 5h. 推送 agent_state（todos 等）
                pushAgentState(context, emitter);
            }

            // 6. 发送 end 事件
            long totalDuration = System.currentTimeMillis() - totalStart;

            // 检查是否因达到最大迭代次数或守卫终止（I5 连续截断/空响应）而导致无最终回答
            String finalContent = fullAnswer.toString();
            if (finalContent.isEmpty() && (totalIterations >= maxIterations || guardStopped)) {
                finalContent = stepsExhaustedFallback(maxIterations);
                // 将兜底内容推送给前端
                sendSseEvent(emitter, "content", Map.of("content", finalContent));
            }

            // 调用完成回调（AppServiceImpl 保存消息等）；iterations 经 runtimeState 侧通道交回编排层落库
            context.getRuntimeState().put("iterations", totalIterations);
            if (onComplete != null) {
                try {
                    String toolCallsJson = objectMapper.writeValueAsString(toolCallRecords);
                    onComplete.accept(finalContent, thinkingContent.toString(), toolCallsJson);
                } catch (Exception e) {
                    log.warn("[AgentEngine] onComplete callback error: {}", e.getMessage());
                }
            }

            Map<String, Object> endData = new LinkedHashMap<>();
            endData.put("content", finalContent);
            endData.put("durationMs", totalDuration);
            endData.put("iterations", totalIterations);
            endData.put("hasThinking", thinkingContent.length() > 0);
            sendSseEvent(emitter, "end", endData);
            emitter.complete();

            log.info("[AgentEngine] executeStream complete: duration={}ms, iterations={}, contentLen={}",
                    totalDuration, totalIterations, fullAnswer.length());

        } catch (Exception e) {
            log.error("[AgentEngine] executeStream fatal error", e);
            try {
                Map<String, Object> errData = new LinkedHashMap<>();
                errData.put("message", "Agent execution error: " + e.getMessage());
                sendSseEvent(emitter, "error", errData);
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    // ==================== 同步执行（Agent 场景） ====================

    /**
     * 同步执行智能体：执行工具调用循环但不推 SSE。
     * 结果写入 RunEventPublisher，返回 AgentResult。
     *
     * @param run     AgentRun 记录
     * @param context 运行时上下文（需已设置 modelConfig）
     * @return AgentResult
     */
    public AgentResult executeSync(AgentRun run, BaseContext context) {
        long totalStart = System.currentTimeMillis();
        int totalIterations = 0;
        List<AgentResult.ToolCallRecord> toolCallRecords = new ArrayList<>();

        try {
            // 1. 解析工具
            List<ToolDefinition> tools = toolRegistry.resolveTools(context.getTools());
            // M3-3（D14）：install_skill 条件注入——引擎侧过滤（绑定技能非空或沙箱启用才保留）
            filterInstallSkill(context, tools);
            List<ChatRequest.ToolDefinition> openaiTools = toolRegistry.toOpenAITools(tools);
            ModelConfig mc = context.getModelConfig() != null
                    ? context.getModelConfig() : ModelConfig.default_(context.getModel());

            log.info("[AgentEngine] executeSync start: runId={}, model={}, tools={}",
                    run.getId(), mc.getModel(), tools.size());

            // 2. 构建消息
            List<ChatMessage> messages = new ArrayList<>();
            if (StrUtil.isNotBlank(context.getSystemPrompt())) {
                messages.add(new ChatMessage("system", context.getSystemPrompt()));
            }
            messages.add(new ChatMessage("user", extractQuery(run)));

            // 3. 前置中间件（中间件会往 tools 列表中添加新工具）
            middlewareChain.executeBefore(context, messages, tools);

            // 重新生成 openaiTools（包含中间件注册的新工具）
            openaiTools = toolRegistry.toOpenAITools(tools);
            log.info("[AgentEngine] After middleware: total tools={}", tools.size());

            // 4. 工具调用循环（maxSteps 可通过 context 配置）
            String finalAnswer = "";
            int maxIterationsSync = (context.getMaxSteps() != null && context.getMaxSteps() > 0)
                    ? context.getMaxSteps() : MAX_ITERATIONS;
            // I5：防卡死守卫；I7：retryConfig 由 ModelRetryMiddleware 预写 runtimeState
            RoundGuard guard = new RoundGuard();
            boolean guardStoppedSync = false;
            for (int round = 0; round < maxIterationsSync; round++) {
                totalIterations++;

                // I6：每轮循环边界（本轮消息回灌后、下轮模型调用前）
                middlewareChain.executeRoundBoundary(context, messages, tools);

                // M2-3（D7）：技能激活追加工具依赖后，本轮模型调用前重算工具
                List<ChatRequest.ToolDefinition> recomputed = recomputeToolsIfDirty(context, tools);
                if (recomputed != null) {
                    openaiTools = recomputed;
                }

                // I7：LLM 调用（瞬时异常指数退避重试，retryConfig 读自 runtimeState）
                List<ChatRequest.ToolDefinition> toolsForCall = openaiTools;
                ChatResponse resp = callLlmWithRetry(
                        () -> llmService.chatWithTools(
                                mc.getModel(), messages, toolsForCall,
                                mc.getTemperature(), mc.getApiUrl(), mc.getApiKey()),
                        retryConfigOf(context), "chatWithTools");

                middlewareChain.executeAfter(context, resp);

                // I5 防卡死守卫：裁决本轮走向
                RoundGuard.Action action = guard.evaluate(resp);
                if (action == RoundGuard.Action.EXECUTE_TOOLS) {
                    // 走工具执行，见下
                } else if (action == RoundGuard.Action.FINISH) {
                    if (!resp.hasToolCalls()) {
                        finalAnswer = resp.getContent();
                    } else {
                        log.warn("[AgentEngine] Guard stopped sync tool loop: consecutive length truncation");
                    }
                    guardStoppedSync = StrUtil.isBlank(finalAnswer);
                    break;
                } else {
                    // RETRY_*：注入 system 提示后重试
                    messages.add(new ChatMessage("system", action.systemMessage));
                    continue;
                }

                // Add assistant message
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(resp.getContent());
                assistantMsg.setToolCalls(resp.getToolCalls());
                messages.add(assistantMsg);

                // Execute tools
                // I8：先分类（拦截/缺失 inline 处理），可执行批次统一执行（全只读轮并行，其余串行）
                List<ChatMessage.ToolCall> batchCallsSync = new ArrayList<>();
                List<ToolDefinition> batchDefsSync = new ArrayList<>();
                for (ChatMessage.ToolCall tc : resp.getToolCalls()) {
                    // Middleware intercept
                    ToolCallInterceptor interceptor =
                            middlewareChain.getToolCallInterceptor(context, tc);
                    if (interceptor != null && interceptor.isSkip()) {
                        ChatMessage interceptorMsg = interceptor.getResultMessage();
                        if (interceptorMsg.getName() == null && tc.getFunction() != null) {
                            interceptorMsg.setName(tc.getFunction().getName());
                        }
                        messages.add(interceptorMsg);
                        continue;
                    }

                    ToolDefinition toolDef = findTool(tools, tc.getFunction().getName());
                    if (toolDef == null) {
                        ChatMessage toolNotFoundMsg = new ChatMessage("tool",
                                "Error: Tool not found: " + tc.getFunction().getName(), tc.getId());
                        toolNotFoundMsg.setName(tc.getFunction().getName());
                        messages.add(toolNotFoundMsg);
                        continue;
                    }

                    batchCallsSync.add(tc);
                    batchDefsSync.add(toolDef);
                }

                // I8：批次执行，结果按 toolCallId 对齐（原顺序）回填
                List<ToolExecution> executions = executeToolBatch(batchCallsSync, batchDefsSync, context, run);
                for (int i = 0; i < batchCallsSync.size(); i++) {
                    ChatMessage.ToolCall tc = batchCallsSync.get(i);
                    ToolResult toolResult = executions.get(i).result();
                    long toolDuration = executions.get(i).durationMs();

                    String resultContent = toolResult.isSuccess()
                            ? toolResult.getOutput() : "Error: " + toolResult.getError();
                    ChatMessage toolResultMsg = new ChatMessage("tool",
                            truncate(resultContent, TOOL_OUTPUT_MAX), tc.getId());
                    toolResultMsg.setName(tc.getFunction().getName());
                    messages.add(toolResultMsg);

                    publishToolCallEvent(run.getId(), tc, toolResult, toolDuration);

                    AgentResult.ToolCallRecord record = new AgentResult.ToolCallRecord();
                    record.setToolName(tc.getFunction().getName());
                    record.setSuccess(toolResult.isSuccess());
                    record.setOutput(toolResult.getOutput());
                    record.setError(toolResult.getError());
                    record.setDurationMs((int) toolDuration);
                    toolCallRecords.add(record);
                }
            }

            // QF-4：轮次耗尽（或 I5 守卫终止）且无最终回答时，填入与流式分支一致的兜底文案（success 语义不变）
            if (StrUtil.isBlank(finalAnswer) && (totalIterations >= maxIterationsSync || guardStoppedSync)) {
                finalAnswer = stepsExhaustedFallback(maxIterationsSync);
            }

            AgentResult result = AgentResult.success(finalAnswer);
            result.setToolCallRecords(toolCallRecords);
            result.setTotalDurationMs((int)(System.currentTimeMillis() - totalStart));
            log.info("[AgentEngine] executeSync complete: iterations={}", totalIterations);
            return result;

        } catch (Exception e) {
            log.error("[AgentEngine] executeSync fatal error", e);
            AgentResult r = AgentResult.error("Agent execution error: " + e.getMessage());
            r.setTotalDurationMs((int)(System.currentTimeMillis() - totalStart));
            return r;
        }
    }

    // ==================== 流式消费方法 ====================

    /**
     * 消费 Flux&lt;StreamEvent&gt;，分类推送 SSE 事件，累积完整响应。
     * 使用 CountDownLatch 阻塞直到流完成（在 Executor 线程中调用）。
     */
    private LlmStreamResult consumeStream(
            Flux<StreamEvent> flux, SseEmitter emitter,
            StringBuilder fullAnswer, StringBuilder thinkingContent) throws InterruptedException {

        AtomicReference<String> finishReason = new AtomicReference<>("stop");
        List<ChatMessage.ToolCall> accumulatedToolCalls = new ArrayList<>();
        StringBuilder contentAccum = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        flux.subscribe(
                event -> {
                    try {
                        switch (event.getType()) {
                            case THINKING -> {
                                thinkingContent.append(event.getContent());
                                sendSseEvent(emitter, "thinking",
                                        Map.of("content", event.getContent()));
                            }
                            case CONTENT -> {
                                contentAccum.append(event.getContent());
                                fullAnswer.append(event.getContent());
                                sendSseEvent(emitter, "message",
                                        Map.of("content", event.getContent()));
                            }
                            case TOOL_CALL_DELTA -> {
                                mergeToolCallDelta(accumulatedToolCalls, event.getToolCall());
                            }
                            case FINISH -> {
                                finishReason.set(event.getFinishReason());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[AgentEngine] SSE send error during consumeStream: {}", e.getMessage());
                    }
                },
                error -> {
                    log.error("[AgentEngine] LLM stream error: {}", error.getMessage());
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                        log.error("[AgentEngine] LLM stream error body: {}", wcre.getResponseBodyAsString());
                    }
                    // 打印当前累积的 toolCalls 用于诊断
                    if (!accumulatedToolCalls.isEmpty()) {
                        for (ChatMessage.ToolCall tc : accumulatedToolCalls) {
                            log.debug("[AgentEngine] On error, accumulated toolCall: id={}, name={}, args={}",
                                    tc.getId(),
                                    tc.getFunction() != null ? tc.getFunction().getName() : "null",
                                    tc.getFunction() != null ? tc.getFunction().getArguments() : "null");
                        }
                    }
                    errorRef.set(error);
                    latch.countDown();
                },
                () -> latch.countDown()
        );

        // 阻塞等待流完成（长回答/推理模型可能超过 90s，使用独立流式超时配置）
        long timeout = streamTimeoutSeconds > 0 ? streamTimeoutSeconds : Math.max(60L, (long) gatewayTimeoutSeconds * 3);
        boolean completed = latch.await(timeout, TimeUnit.SECONDS);

        if (!completed) {
            log.warn("[AgentEngine] Stream timeout after {}s", timeout);
            // I7：带上 TimeoutException cause，供瞬时异常判定（可重试）
            throw new RuntimeException("LLM stream timeout after " + timeout + "s",
                    new java.util.concurrent.TimeoutException("stream timeout"));
        }

        // 处理错误
        if (errorRef.get() != null) {
            // I7：带上原始 cause，供瞬时异常判定（可重试）
            throw new RuntimeException("LLM stream error: " + errorRef.get().getMessage(), errorRef.get());
        }

        // 构建 ChatResponse
        ChatResponse response = new ChatResponse();
        response.setContent(contentAccum.toString());
        response.setFinishReason(finishReason.get());
        response.setToolCalls(accumulatedToolCalls);

        return new LlmStreamResult(response, false);
    }

    // ==================== I7：LLM 调用瞬时异常重试 ====================

    /**
     * 流式调用 + 消费（带 I7 重试）：瞬时异常（IOException/超时/5xx/429）按 retryConfig
     * 指数退避重试。retryConfig 由 ModelRetryMiddleware 预写 runtimeState（未配置不重试）。
     * 重试前回滚本次尝试对 fullAnswer/thinkingContent 的部分累积，避免内容重复。
     */
    private LlmStreamResult consumeStreamWithRetry(BaseContext context, ModelConfig modelConfig,
                                                   List<ChatRequest.ToolDefinition> openaiTools,
                                                   List<ChatMessage> messages,
                                                   SseEmitter emitter,
                                                   StringBuilder fullAnswer,
                                                   StringBuilder thinkingContent) throws InterruptedException {
        ModelRetryMiddleware.RetryConfig retryConfig = retryConfigOf(context);
        int maxRetries = retryConfig != null ? Math.max(0, retryConfig.getMaxRetries()) : 0;
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0 && last != null) {
                long wait = Math.min(retryConfig.getWaitTime(attempt - 1), 30_000L);
                log.warn("[AgentEngine] Transient LLM failure (streamChatWithTools), retry {}/{} in {}ms: {}",
                        attempt, maxRetries, wait, last.getMessage());
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
            int answerLen = fullAnswer.length();
            int thinkingLen = thinkingContent.length();
            try {
                Flux<StreamEvent> flux = llmService.streamChatWithTools(
                        modelConfig.getModel(), messages, openaiTools,
                        modelConfig.getTemperature(), modelConfig.getApiUrl(),
                        modelConfig.getApiKey(), modelConfig.isEnableThinking());
                return consumeStream(flux, emitter, fullAnswer, thinkingContent);
            } catch (RuntimeException e) {
                if (fullAnswer.length() > answerLen) fullAnswer.setLength(answerLen);
                if (thinkingContent.length() > thinkingLen) thinkingContent.setLength(thinkingLen);
                if (!isTransientLlmFailure(e)) {
                    throw e;
                }
                last = e;
            }
        }
        throw last;
    }

    /**
     * I7：读取 ModelRetryMiddleware 预写的重试配置（无则返回 null = 不重试）。
     */
    private ModelRetryMiddleware.RetryConfig retryConfigOf(BaseContext context) {
        try {
            Object cfg = context.getRuntimeState().get("retryConfig");
            return cfg instanceof ModelRetryMiddleware.RetryConfig rc ? rc : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * I7：带指数退避的 LLM 调用。仅对瞬时异常（isTransientLlmFailure）重试 maxRetries 次；
     * 非瞬时异常直接抛出。maxRetries≤0 或无配置时行为与直调一致。
     */
    private <T> T callLlmWithRetry(java.util.function.Supplier<T> call,
                                   ModelRetryMiddleware.RetryConfig config, String desc) {
        int maxRetries = config != null ? Math.max(0, config.getMaxRetries()) : 0;
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0 && last != null) {
                long wait = Math.min(config.getWaitTime(attempt - 1), 30_000L);
                log.warn("[AgentEngine] Transient LLM failure ({}), retry {}/{} in {}ms: {}",
                        desc, attempt, maxRetries, wait, last.getMessage());
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw last;
                }
            }
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (!isTransientLlmFailure(e)) {
                    throw e;
                }
                last = e;
            }
        }
        throw last;
    }

    /**
     * I7：瞬时异常判定——沿 cause 链找 IOException / 超时 / WebClient 请求异常（连接层失败）
     * 或 5xx、429 响应。4xx 业务错误不重试。
     */
    static boolean isTransientLlmFailure(Throwable e) {
        Throwable t = e;
        int depth = 0;
        while (t != null && depth++ < 8) {
            if (t instanceof java.io.IOException
                    || t instanceof java.util.concurrent.TimeoutException
                    || t instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
                return true;
            }
            if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                int status = wcre.getStatusCode().value();
                return status >= 500 || status == 429;
            }
            t = t.getCause();
        }
        return false;
    }

    // ==================== 工具执行 ====================

    /**
     * 执行单个工具调用（参数解析 + Schema 验证 + 执行器调用）。
     */
    private ToolResult executeTool(ToolDefinition toolDef, ChatMessage.ToolCall tc,
                                   BaseContext context, AgentRun run) {
        String argsJson = tc.getFunction().getArguments();

        // 解析参数
        Map<String, Object> args;
        try {
            args = objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[AgentEngine] Failed to parse arguments for {}: {}, attempting repair...",
                    tc.getFunction().getName(), e.getMessage());
            // 尝试修复常见 JSON 问题（如字符串值中的未转义双引号）
            String repaired = tryRepairJsonArguments(argsJson);
            if (repaired != null) {
                try {
                    args = objectMapper.readValue(repaired, new TypeReference<Map<String, Object>>() {});
                    log.info("[AgentEngine] JSON repair succeeded for tool: {}", tc.getFunction().getName());
                } catch (Exception e2) {
                    log.error("[AgentEngine] JSON repair also failed for {}: {}", tc.getFunction().getName(), e2.getMessage());
                    return ToolResult.error("Invalid arguments JSON: " + e.getMessage()
                            + ". Please ensure all double quotes inside string values are escaped with backslash.", 0);
                }
            } else {
                return ToolResult.error("Invalid arguments JSON: " + e.getMessage()
                        + ". Please ensure all double quotes inside string values are escaped with backslash.", 0);
            }
        }

        // JSON Schema 验证
        if (toolDef.getInputSchema() != null) {
            JsonSchemaValidator.ValidationResult vr =
                    JsonSchemaValidator.validate(args, toolDef.getInputSchema());
            if (!vr.valid()) {
                log.warn("[AgentEngine] Validation failed for {}: {}", tc.getFunction().getName(), vr.errors());
                return ToolResult.error("Validation failed: " + vr.errors(), 0);
            }
        }

        // 构建 ToolContext
        ToolContext toolCtx = new ToolContext();
        toolCtx.setUserId(context.getUid());
        toolCtx.setRunId(context.getRunId());
        if (run != null) {
            toolCtx.setRunId(run.getId());
        }
        toolCtx.setQuery(run != null ? extractQuery(run) : "");
        // 传递 extendions（如 fileThreadId 等）
        if (context.getRuntimeState() != null) {
            context.getRuntimeState().forEach((k, v) -> toolCtx.setExtension(k, v));
        }

        // 查找执行器并执行
        ToolExecutor executor = executorFactory.getExecutor(toolDef.getType());
        if (executor == null) {
            return ToolResult.error("No executor for type: " + toolDef.getType(), 0);
        }

        return executor.execute(toolDef, args, toolCtx);
    }

    // ==================== 辅助方法 ====================

    /**
     * M3-3（D14，spec REQ-E03 修订）：install_skill 条件注入——引擎侧过滤（ToolRegistry 恒注入行为不动，
     * Deviation 已登记）。仅当 context 绑定技能非空或沙箱启用时保留 install_skill，否则从工具列表剔除。
     */
    private void filterInstallSkill(BaseContext context, List<ToolDefinition> tools) {
        if (keepsInstallSkill(context)) return;
        tools.removeIf(t -> "install_skill".equals(t.getName()));
    }

    /**
     * install_skill 保留判定：绑定技能非空 || 沙箱启用。
     * 沙箱启用取数路径对齐 SkillsMiddleware.sandboxConfigIdOf（runtimeState 优先，回落 ChatBotContext 字段）。
     */
    static boolean keepsInstallSkill(BaseContext context) {
        if (context.getSkills() != null && !context.getSkills().isEmpty()) return true;
        Object fromState = context.getRuntimeState() != null
                ? context.getRuntimeState().get("sandboxConfigId") : null;
        if (fromState instanceof String s && !s.isBlank()) return true;
        if (context instanceof com.fastrag.module.agent.context.ChatBotContext chatBot) {
            String cfgId = chatBot.getSandboxConfigId();
            return cfgId != null && !cfgId.isBlank();
        }
        return false;
    }

    /**
     * M2-3（D7）：模型调用前若 toolsDirty（技能激活等中间件在运行中向 context.tools 追加了
     * 工具依赖），重走 resolveTools → openai 转换并清标志。只重算一次/置位一次，勿每轮全量重查。
     * 中间件直接注入 tools 列表的定义（filesystem/database 等）按名称去重保留，不因重解析丢失。
     *
     * @return 重算后的 openai tools；null 表示无需重算（调用方沿用原列表）
     */
    private List<ChatRequest.ToolDefinition> recomputeToolsIfDirty(BaseContext context,
                                                                   List<ToolDefinition> tools) {
        if (!context.isToolsDirty()) return null;
        List<ToolDefinition> fresh = toolRegistry.resolveTools(context.getTools());
        for (ToolDefinition def : fresh) {
            boolean exists = (def.getName() != null && findTool(tools, def.getName()) != null)
                    || (def.getToolId() != null && findTool(tools, def.getToolId()) != null);
            if (!exists) tools.add(def);
        }
        // M3-3：重解析会重新恒注入 install_skill，过滤须在合并后执行
        filterInstallSkill(context, tools);
        context.setToolsDirty(false);
        log.info("[AgentEngine] toolsDirty detected: recomputed tools, total={}", tools.size());
        return toolRegistry.toOpenAITools(tools);
    }

    /**
     * 尝试修复 LLM 生成的无效 JSON arguments。
     * <p>
     * 常见问题：LLM 生成包含 Python 代码的 JSON 时，
     * 字符串值中的双引号（如 {@code f"SELECT..."}）未被转义。
     * 本方法使用状态机扫描字符串值中的未转义引号并加反斜杠转义。
     *
     * @param json 可能无效的 JSON 字符串
     * @return 修复后的 JSON，或 null（无法修复）
     */
    private String tryRepairJsonArguments(String json) {
        if (json == null || json.isBlank()) return null;

        StringBuilder sb = new StringBuilder(json.length() + 64);
        boolean inString = false;       // 当前是否在 JSON 字符串值内部
        boolean escaped = false;        // 前一个字符是反斜杠

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                // 转义状态：原样输出
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                if (inString) {
                    // 在字符串内部遇到引号 → 判断是结束符还是内部引号
                    // 规则：如果下一个字符是 JSON 结构字符（,:}[]\s），则是结束符
                    boolean isStructuralEnd = false;
                    if (i + 1 >= json.length()) {
                        isStructuralEnd = true;
                    } else {
                        char next = json.charAt(i + 1);
                        isStructuralEnd = next == ',' || next == ':' || next == '}'
                                || next == ']' || next == '\n' || next == '\r'
                                || next == ' ' || next == '\t';
                    }
                    if (isStructuralEnd) {
                        inString = false;
                        sb.append(c);
                    } else {
                        // 内部引号 → 转义
                        sb.append('\\');
                        sb.append(c);
                    }
                } else {
                    // 进入字符串值
                    inString = true;
                    sb.append(c);
                }
                continue;
            }

            // 换行符在 JSON 字符串外部是非法的，替换为空格
            if (!inString && (c == '\n' || c == '\r')) {
                sb.append(' ');
                continue;
            }

            sb.append(c);
        }

        // 如果修复后的字符串与原字符串相同，说明无法修复
        String result = sb.toString();
        if (result.equals(json)) return null;

        return result;
    }

    /**
     * 推送智能体状态（todos 等）到前端。
     */
    private void pushAgentState(BaseContext context, SseEmitter emitter) {
        Object todos = context.getRuntimeState().get("todos");
        Object artifacts = context.getRuntimeState().get("artifacts");
        if (todos != null || artifacts != null) {
            Map<String, Object> stateData = new LinkedHashMap<>();
            if (todos != null) stateData.put("todos", todos);
            if (artifacts != null) stateData.put("artifacts", artifacts);
            sendSseEvent(emitter, "agent_state", stateData);
        }
    }

    /**
     * 发布工具调用事件到 RunEventPublisher。
     */
    private void publishToolCallEvent(String runId, ChatMessage.ToolCall tc,
                                      ToolResult result, long duration) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toolName", tc.getFunction().getName());
            payload.put("toolCallId", tc.getId());
            payload.put("success", result.isSuccess());
            // I3：事件表只存截断后内容（与 I2 容量上限配合，防止大输出撑爆事件存储）
            payload.put("output", truncate(result.getOutput(), TOOL_OUTPUT_MAX));
            payload.put("error", result.getError());
            payload.put("durationMs", duration);
            eventPublisher.pushEvent(runId, "tool_call", payload);
        } catch (Exception e) {
            log.debug("[AgentEngine] Event publish failed (non-critical): {}", e.getMessage());
        }
    }

    /**
     * 增量合并 tool_call 片段（index-based）。
     * StreamEvent 中的 toolCall 已经是 LlmService 累积后的完整对象，
     * 按 index 保持最新的版本。
     */
    private void mergeToolCallDelta(List<ChatMessage.ToolCall> accumulated,
                                     ChatMessage.ToolCall delta) {
        if (delta == null) return;
        // 按 index 查找并更新
        int index = accumulated.size();
        // 检查是否已存在相同 id 的 tool_call
        for (int i = 0; i < accumulated.size(); i++) {
            ChatMessage.ToolCall existing = accumulated.get(i);
            if (existing.getId() != null && existing.getId().equals(delta.getId())) {
                // 更新已存在的条目
                accumulated.set(i, delta);
                return;
            }
            if (existing.getFunction() != null && delta.getFunction() != null
                    && existing.getFunction().getName() != null
                    && existing.getFunction().getName().equals(delta.getFunction().getName())) {
                accumulated.set(i, delta);
                return;
            }
        }
        // 新条目
        accumulated.add(delta);
    }

    /**
     * 查找工具定义（按 name 或 toolId）。
     */
    private ToolDefinition findTool(List<ToolDefinition> tools, String name) {
        return tools.stream()
                .filter(t -> name.equals(t.getName()) || name.equals(t.getToolId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从 AgentRun 的 inputPayload 中提取查询文本。
     */
    @SuppressWarnings("unchecked")
    private String extractQuery(AgentRun run) {
        if (run == null || run.getInputPayload() == null) return "";
        Object input = run.getInputPayload();
        if (input instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) input;
            Object q = m.get("query");
            if (q != null) return q.toString();
            Object m2 = m.get("message");
            if (m2 != null) return m2.toString();
            Object m3 = m.get("input");
            if (m3 != null) return m3.toString();
        }
        return input.toString();
    }

    /**
     * 截断文本到指定长度（I1：rune 安全——不拆散 emoji 等增补码点，见 OutputBudget）。
     */
    private String truncate(String text, int maxLen) {
        return OutputBudget.truncate(text, maxLen, OutputBudget.DEFAULT_TRUNCATED_MARKER);
    }

    /**
     * 打印当前消息列表摘要（用于调试 LLM 调用前的上下文）。
     */
    private void logMessagesSummary(List<ChatMessage> messages, int round) {
        log.info("[AgentEngine] Round {}: messageList size={}", round + 1, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String contentPreview = msg.getContent() != null
                    ? (msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "...(" + msg.getContent().length() + "c)" : msg.getContent())
                    : "null";
            String tcInfo = msg.getToolCalls() != null
                    ? "toolCalls=" + msg.getToolCalls().stream()
                        .map(tc -> tc.getId() + ":" + (tc.getFunction() != null ? tc.getFunction().getName() : "?"))
                        .reduce((a, b) -> a + "," + b).orElse("")
                    : "";
            String tcIdInfo = msg.getToolCallId() != null ? "toolCallId=" + msg.getToolCallId() : "";
            String nameInfo = msg.getName() != null ? "name=" + msg.getName() : "";
            log.debug("[AgentEngine]   msg[{}]: role={}, content=[{}], {}, {}, {}", i, msg.getRole(), contentPreview, tcInfo, tcIdInfo, nameInfo);
        }
    }

    /**
     * 安全发送 SSE 事件。
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.warn("[AgentEngine] Failed to send SSE event '{}': {}", eventName, e.getMessage());
        }
    }
}
