package com.fastrag.module.agent.middleware;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ChatBotContext;
import com.fastrag.module.agent.context.ContextBuilder;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.executor.AgentEngine;
import com.fastrag.module.agent.executor.AgentResult;
import com.fastrag.module.agent.service.AgentRunService;
import com.fastrag.module.agent.service.AgentService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 子智能体中间件。
 * 提供 task 工具，允许主 Agent 将子任务委派给专门的子智能体处理。
 *
 * <p>执行顺序: Order=5。</p>
 */
@Slf4j
@Component
public class SubAgentMiddleware implements AgentMiddleware {

    private final AgentService agentService;
    private final AgentRunService agentRunService;
    private final ContextBuilder contextBuilder;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private AgentEngine agentEngine;

    public SubAgentMiddleware(AgentService agentService,
                               AgentRunService agentRunService,
                               ContextBuilder contextBuilder,
                               ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.agentRunService = agentRunService;
        this.contextBuilder = contextBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() { return 5; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (!(context instanceof ChatBotContext chatCtx)) {
            return context;
        }
        if (chatCtx.getSubagents() == null || chatCtx.getSubagents().isEmpty()) {
            log.debug("[SubAgentMiddleware] No subagents configured, skipping");
            return context;
        }

        log.info("[SubAgentMiddleware] Registering task tool with {} subagents: {}",
                chatCtx.getSubagents().size(), chatCtx.getSubagents());

        tools.add(ToolDefinition.builder()
                .toolId("builtin_task")
                .name("task")
                .description("将子任务委派给专门的子智能体处理。子智能体会独立分析问题、调用工具并返回结果。")
                .type("subagent")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "description", Map.of("type", "string",
                                        "description", "子任务的详细描述"),
                                "subagent_type", Map.of("type", "string",
                                        "description", "子智能体类型",
                                        "enum", chatCtx.getSubagents().toArray())
                        ),
                        "required", List.of("description", "subagent_type")
                ))
                .config(Map.of("subagents", chatCtx.getSubagents()))
                .build());

        String subagentPrompt = buildSubagentPrompt(chatCtx);
        injectSystemPrompt(messages, subagentPrompt);
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                   ChatMessage.ToolCall toolCall) {
        if (!"task".equals(toolCall.getFunction().getName())) {
            return null;
        }
        if (!(context instanceof ChatBotContext chatCtx)) {
            return null;
        }

        try {
            Map<String, Object> args = parseToolCallArgs(toolCall);
            String description = (String) args.get("description");
            String subagentType = (String) args.get("subagent_type");

            if (StrUtil.isBlank(description)) {
                return ToolCallInterceptor.skip(new ChatMessage("tool",
                        "Error: 'description' is required", toolCall.getId()));
            }
            if (StrUtil.isBlank(subagentType)) {
                return ToolCallInterceptor.skip(new ChatMessage("tool",
                        "Error: 'subagent_type' is required", toolCall.getId()));
            }
            if (chatCtx.getSubagents() == null || !chatCtx.getSubagents().contains(subagentType)) {
                return ToolCallInterceptor.skip(new ChatMessage("tool",
                        "Error: Unknown subagent type: " + subagentType, toolCall.getId()));
            }

            Agent subAgent = agentService.getVisibleSubagentBySlug(subagentType, null);
            if (subAgent == null) {
                return ToolCallInterceptor.skip(new ChatMessage("tool",
                        "Error: Subagent not found: " + subagentType, toolCall.getId()));
            }

            String childThreadId = IdUtil.fastSimpleUUID();
            AgentRun childRun = createChildRun(chatCtx, subAgent, description, childThreadId);
            BaseContext childContext = contextBuilder.buildContext(childRun);
            childContext.setUid(chatCtx.getUid());
            childContext.setThreadId(childThreadId);

            log.info("[SubAgentMiddleware] Executing sub-agent: type={}, runId={}",
                    subagentType, childRun.getId());

            AgentResult result = agentEngine.executeSync(childRun, childContext);

            updateSubAgentState(chatCtx, childRun, result);

            String output = result.isSuccess()
                    ? result.getAnswer() : "Error: " + result.getError();
            if (output.length() > 5000) {
                output = output.substring(0, 5000) + "...(truncated)";
            }
            return ToolCallInterceptor.skip(new ChatMessage("tool", output, toolCall.getId()));

        } catch (Exception e) {
            log.error("[SubAgentMiddleware] Task execution error", e);
            return ToolCallInterceptor.skip(new ChatMessage("tool",
                    "SubAgent execution failed: " + e.getMessage(), toolCall.getId()));
        }
    }

    private String buildSubagentPrompt(ChatBotContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- 可用子智能体 ---\n");
        sb.append("你可以使用 'task' 工具将子任务委派给以下子智能体：\n");
        for (String type : ctx.getSubagents()) {
            sb.append("- **").append(type).append("**\n");
        }
        sb.append("\n使用 {task(description, subagent_type)} 来委派任务。\n");
        sb.append("---\n");
        return sb.toString();
    }

    private AgentRun createChildRun(BaseContext parentCtx, Agent subAgent,
                                     String description, String childThreadId) {
        AgentRun childRun = new AgentRun();
        childRun.setId(IdUtil.fastSimpleUUID());
        childRun.setThreadId(childThreadId);
        childRun.setAgentId(subAgent.getId());
        childRun.setUid(parentCtx.getUid());
        childRun.setRequestId(IdUtil.fastSimpleUUID());
        childRun.setStatus("pending");
        childRun.setRunType("subagent");

        Map<String, Object> input = new HashMap<>();
        input.put("query", description);
        input.put("description", description);
        childRun.setInputPayload(input);
        childRun.setCreatedAt(LocalDateTime.now());
        childRun.setUpdatedAt(LocalDateTime.now());

        agentRunService.createRunDirect(childRun);
        return childRun;
    }

    @SuppressWarnings("unchecked")
    private void updateSubAgentState(ChatBotContext ctx, AgentRun childRun, AgentResult result) {
        List<Map<String, Object>> subagentRuns =
                (List<Map<String, Object>>) ctx.getRuntimeState()
                        .computeIfAbsent("subagent_runs", k -> new ArrayList<>());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", childRun.getId());
        state.put("threadId", childRun.getThreadId());
        state.put("agentId", childRun.getAgentId());
        state.put("status", result.isSuccess() ? "completed" : "failed");
        state.put("result_preview", result.isSuccess()
                ? StrUtil.sub(result.getAnswer(), 0, 200)
                : "Error: " + result.getError());
        subagentRuns.add(state);
    }

    private void injectSystemPrompt(List<ChatMessage> messages, String prompt) {
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                String existing = msg.getContent();
                msg.setContent(existing != null ? existing + prompt : prompt);
                return;
            }
        }
        messages.add(0, new ChatMessage("system", prompt));
    }

    private Map<String, Object> parseToolCallArgs(ChatMessage.ToolCall toolCall) {
        String argsJson = toolCall.getFunction().getArguments();
        try {
            return objectMapper.readValue(argsJson,
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
