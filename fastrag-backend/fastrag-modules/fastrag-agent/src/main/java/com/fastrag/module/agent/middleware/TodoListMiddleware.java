package com.fastrag.module.agent.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 待办事项中间件。注册 write_todos 工具，在 interceptToolCall 中解析参数并更新 context.runtimeState.todos。
 *
 * <p>执行顺序: Order=7。</p>
 *
 * <p>LLM 可以调用 write_todos 工具来管理任务列表，
 * 中间件会将更新后的 todos 持久化到 context.runtimeState 中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoListMiddleware implements AgentMiddleware {

    private final ObjectMapper objectMapper;

    @Override
    public int getOrder() { return 7; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        try {
            // 注入当前 todos 到 system prompt
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> currentTodos =
                    (List<Map<String, Object>>) context.getRuntimeState().get("todos");
            if (currentTodos != null && !currentTodos.isEmpty()) {
                String todoPrompt = buildTodoPrompt(currentTodos);
                injectSystemPrompt(messages, todoPrompt);
                log.debug("[TodoListMiddleware] Injected {} existing todos into system prompt",
                        currentTodos.size());
            }

            // 注册 write_todos 工具
            tools.add(ToolDefinition.builder()
                    .toolId("builtin_write_todos")
                    .name("write_todos")
                    .description("更新待办事项列表。传入完整的待办事项数组，将替换当前列表。"
                            + "每个待办事项包含 title(标题)、status(状态: pending/in_progress/completed)、"
                            + "priority(优先级: high/medium/low)。")
                    .type("builtin")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "todos", Map.of(
                                            "type", "array",
                                            "description", "待办事项列表",
                                            "items", Map.of(
                                                    "type", "object",
                                                    "properties", Map.of(
                                                            "title", Map.of("type", "string", "description", "任务标题"),
                                                            "status", Map.of("type", "string",
                                                                    "description", "状态", "enum",
                                                                    List.of("pending", "in_progress", "completed")),
                                                            "priority", Map.of("type", "string",
                                                                    "description", "优先级", "enum",
                                                                    List.of("high", "medium", "low"))
                                                    ),
                                                    "required", List.of("title", "status")
                                            )
                                    )
                            ),
                            "required", List.of("todos")
                    ))
                    .config(Map.of("action", "write_todos"))
                    .build());

            log.debug("[TodoListMiddleware] Registered write_todos tool");
        } catch (Exception e) {
            log.error("[TodoListMiddleware] Failed to register write_todos tool: {}", e.getMessage());
        }

        return context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        String funcName = toolCall.getFunction().getName();
        if (!"write_todos".equals(funcName)) {
            return null;
        }

        try {
            String argsJson = toolCall.getFunction().getArguments();
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);

            Object todosObj = args.get("todos");
            if (todosObj instanceof List) {
                List<Map<String, Object>> todos = (List<Map<String, Object>>) todosObj;
                context.getRuntimeState().put("todos", new ArrayList<>(todos));

                int total = todos.size();
                long completed = todos.stream()
                        .filter(t -> "completed".equals(t.get("status")))
                        .count();

                String confirmation = String.format(
                        "待办事项已更新: 共 %d 项, 已完成 %d 项, 进行中 %d 项。",
                        total,
                        completed,
                        todos.stream().filter(t -> "in_progress".equals(t.get("status"))).count()
                );

                log.info("[TodoListMiddleware] Todos updated: total={}, completed={}", total, completed);

                ChatMessage result = new ChatMessage("tool", confirmation, toolCall.getId());
                return ToolCallInterceptor.skip(result);
            }
        } catch (Exception e) {
            log.error("[TodoListMiddleware] Failed to parse write_todos args: {}", e.getMessage());
        }

        return null;
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }

    /**
     * 构建当前 todo 列表的 prompt 文本。
     */
    @SuppressWarnings("unchecked")
    private String buildTodoPrompt(List<Map<String, Object>> todos) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- 当前待办事项 ---\n");
        for (Map<String, Object> todo : todos) {
            String status = (String) todo.getOrDefault("status", "pending");
            String title = (String) todo.getOrDefault("title", "未命名任务");
            String priority = (String) todo.getOrDefault("priority", "medium");
            String icon = switch (status) {
                case "completed" -> "✅";
                case "in_progress" -> "🔄";
                default -> "⬜";
            };
            sb.append(String.format("%s [%s] %s (优先级: %s)\n", icon, status, title, priority));
        }
        sb.append("---\n");
        return sb.toString();
    }

    /**
     * 将 todo prompt 注入到 system message 末尾。
     */
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
}
