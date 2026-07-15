package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工具调用补丁中间件。在 afterModelCall 阶段修复 LLM 返回的不规范 tool_calls。
 *
 * <p>执行顺序: Order=8。</p>
 *
 * <p>修复内容:
 * <ul>
 *   <li>补丁空 ID: 生成 "call_patch_" + 随机字符串</li>
 *   <li>补丁空名称: 根据参数推断工具名（file_path->read_file, command->execute, query->query_kb）</li>
 *   <li>补丁不完整 JSON: 尝试关闭未闭合的引号和括号</li>
 * </ul></p>
 */
@Slf4j
@Component
public class PatchToolCallsMiddleware implements AgentMiddleware {

    /** 参数名到工具名的推断映射 */
    private static final Map<String, String> ARG_TO_TOOL_HINT = Map.of(
            "file_path", "read_file",
            "path", "read_file",
            "command", "execute",
            "query", "query_kb",
            "keyword", "find_kb_document",
            "todos", "write_todos",
            "code", "write_file",
            "content", "write_file",
            "dir_path", "list_directory"
    );

    @Override
    public int getOrder() { return 8; }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        if (response == null || !response.hasToolCalls()) {
            return context;
        }

        try {
            for (ChatMessage.ToolCall toolCall : response.getToolCalls()) {
                patchToolCall(toolCall);
            }
            log.debug("[PatchToolCallsMiddleware] Patched {} tool_calls", response.getToolCalls().size());
        } catch (Exception e) {
            log.error("[PatchToolCallsMiddleware] Failed to patch tool calls: {}", e.getMessage());
        }

        return context;
    }

    /**
     * 对单个 tool_call 进行补丁修复。
     */
    private void patchToolCall(ChatMessage.ToolCall toolCall) {
        // 1. 补丁空 ID
        if (toolCall.getId() == null || toolCall.getId().isBlank()) {
            String patchedId = "call_patch_" + generateShortId();
            toolCall.setId(patchedId);
            log.info("[PatchToolCallsMiddleware] Patched empty tool_call id -> {}", patchedId);
        }

        // 确保 function 对象存在
        if (toolCall.getFunction() == null) {
            toolCall.setFunction(new ChatMessage.FunctionCall());
        }

        ChatMessage.FunctionCall fn = toolCall.getFunction();

        // 2. 补丁空名称: 根据参数推断
        if (fn.getName() == null || fn.getName().isBlank()) {
            String guessedName = guessToolNameFromArgs(fn.getArguments());
            if (guessedName != null) {
                fn.setName(guessedName);
                log.info("[PatchToolCallsMiddleware] Patched empty tool name -> {} (guessed from args)", guessedName);
            } else {
                fn.setName("unknown_tool");
                log.warn("[PatchToolCallsMiddleware] Cannot guess tool name from args, set to 'unknown_tool'");
            }
        }

        // 3. 补丁不完整 JSON
        if (fn.getArguments() != null && fn.getArguments().isBlank()) {
            fn.setArguments("{}");
        } else if (fn.getArguments() != null && isIncompleteJson(fn.getArguments())) {
            String patched = tryCloseJson(fn.getArguments());
            if (patched != null) {
                log.info("[PatchToolCallsMiddleware] Patched incomplete JSON arguments");
                fn.setArguments(patched);
            }
        }
    }

    /**
     * 根据参数内容推断工具名。
     */
    private String guessToolNameFromArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return null;
        }

        // 遍历参数名映射，检查 JSON 中是否包含对应的 key
        for (Map.Entry<String, String> entry : ARG_TO_TOOL_HINT.entrySet()) {
            if (argsJson.contains("\"" + entry.getKey() + "\"")) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 检查 JSON 字符串是否不完整。
     */
    private boolean isIncompleteJson(String json) {
        if (json == null || json.isEmpty()) return false;
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') openBraces++;
            else if (c == '}') openBraces--;
            else if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
        }

        // 如果有未闭合的括号或引号
        return openBraces != 0 || openBrackets != 0 || inString;
    }

    /**
     * 尝试关闭不完整的 JSON 字符串。
     */
    private String tryCloseJson(String json) {
        try {
            StringBuilder sb = new StringBuilder(json.trim());

            // 如果在字符串中间截断，关闭引号
            // 先移除末尾可能的不完整 key-value
            while (sb.length() > 0) {
                char last = sb.charAt(sb.length() - 1);
                if (last == '{' || last == '[' || last == ',' || last == ' ') {
                    sb.deleteCharAt(sb.length() - 1);
                } else {
                    break;
                }
            }

            // 如果有未闭合的字符串值，关闭它
            // 简单策略：统计引号数量，奇数则补一个
            int quoteCount = 0;
            for (int i = 0; i < sb.length(); i++) {
                if (sb.charAt(i) == '"' && (i == 0 || sb.charAt(i - 1) != '\\')) {
                    quoteCount++;
                }
            }
            if (quoteCount % 2 != 0) {
                sb.append('"');
            }

            // 关闭括号
            // 计算需要关闭多少个 ] 和 }
            int openBraces = 0;
            int openBrackets = 0;
            boolean inStr = false;
            boolean esc = false;
            for (int i = 0; i < sb.length(); i++) {
                char c = sb.charAt(i);
                if (esc) { esc = false; continue; }
                if (c == '\\') { esc = true; continue; }
                if (c == '"') { inStr = !inStr; continue; }
                if (inStr) continue;
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }

            for (int i = 0; i < openBrackets; i++) sb.append(']');
            for (int i = 0; i < openBraces; i++) sb.append('}');

            return sb.toString();
        } catch (Exception e) {
            log.debug("[PatchToolCallsMiddleware] Failed to close JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 生成短随机 ID（8 位十六进制）。
     */
    private String generateShortId() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong()).substring(0, 8);
    }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        return null;
    }
}
