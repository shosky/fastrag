package com.fastrag.module.tools.executor;

import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeToolExecutor implements ToolExecutor {

    private final KbOperationService kbOperationService;

    @Override
    public String getType() { return "knowledge"; }

    @Override
    public ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx) {
        long t0 = System.currentTimeMillis();
        String toolName = tool.getName();

        try {
            return switch (toolName) {
                case "list_kbs" -> listKnowledgeBases(ctx, t0);
                case "query_kb" -> queryKnowledgeBase(arguments, ctx, t0);
                default -> ToolResult.error("Unknown KB tool: " + toolName, (int)(System.currentTimeMillis() - t0));
            };
        } catch (Exception e) {
            log.error("[KBTool] Execution failed: tool={}", toolName, e);
            return ToolResult.error(e.getMessage(), (int)(System.currentTimeMillis() - t0));
        }
    }

    private ToolResult listKnowledgeBases(ToolContext ctx, long t0) {
        List<Map<String, Object>> kbs = kbOperationService.listKnowledgeBases(ctx.getUserId());

        StringBuilder sb = new StringBuilder();
        sb.append("可访问的知识库列表：\n");
        if (kbs == null || kbs.isEmpty()) {
            sb.append("（暂无可用知识库）\n");
        } else {
            for (Map<String, Object> kb : kbs) {
                sb.append("- ").append(kb.getOrDefault("name", "Unknown"))
                  .append(" (").append(kb.getOrDefault("id", "")).append(")");
                if (kb.containsKey("description") && kb.get("description") != null) {
                    sb.append(": ").append(kb.get("description"));
                }
                sb.append("\n");
            }
        }

        return ToolResult.success(sb.toString(), (int)(System.currentTimeMillis() - t0));
    }

    private ToolResult queryKnowledgeBase(Map<String, Object> args, ToolContext ctx, long t0) {
        String kbId = (String) args.get("kb_id");
        if (kbId == null) kbId = (String) args.get("knowledge_base");
        String query = (String) args.get("query");
        if (query == null) query = (String) args.get("question");

        if (kbId == null || query == null) {
            return ToolResult.error("缺少参数：需要 kb_id 和 query", (int)(System.currentTimeMillis() - t0));
        }

        int topK = 5;
        if (args.containsKey("top_k")) {
            topK = ((Number) args.get("top_k")).intValue();
        }

        List<Map<String, Object>> chunks = kbOperationService.queryKnowledgeBase(kbId, query, topK);

        StringBuilder sb = new StringBuilder();
        sb.append("知识库检索结果（").append(kbId).append("）：\n\n");
        sb.append("查询：").append(query).append("\n\n");

        if (chunks == null || chunks.isEmpty()) {
            sb.append("未找到相关结果。\n");
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> chunk = chunks.get(i);
                sb.append("--- 结果 ").append(i + 1);
                if (chunk.containsKey("score")) {
                    sb.append(" (相关性: ").append(String.format("%.2f", chunk.get("score"))).append(")");
                }
                sb.append(" ---\n");
                sb.append(chunk.getOrDefault("content", "")).append("\n");
                if (chunk.containsKey("source") && chunk.get("source") != null) {
                    sb.append("来源: ").append(chunk.get("source")).append("\n");
                }
                sb.append("\n");
            }
        }

        return ToolResult.success(sb.toString(), (int)(System.currentTimeMillis() - t0));
    }
}
