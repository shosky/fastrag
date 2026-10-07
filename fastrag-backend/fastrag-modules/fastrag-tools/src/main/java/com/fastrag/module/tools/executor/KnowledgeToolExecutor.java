package com.fastrag.module.tools.executor;

import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库工具执行器。
 *
 * <p>Agent可调用的知识库工具，提供知识库列表查询和知识库内容检索两大功能。
 * 实现 {@link ToolExecutor} 接口，type为"knowledge"。</p>
 *
 * <p>支持的工具操作：</p>
 * <ul>
 *   <li>list_kbs - 列出当前用户可访问的所有知识库，包含名称、ID和描述</li>
 *   <li>query_kb - 在指定知识库中执行向量检索，返回相关内容片段，支持自定义返回条数(top_k)</li>
 * </ul>
 *
 * <p>底层通过 {@code KbOperationService} 进行实际的知识库操作。</p>
 */
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

    /** R2（P0-5）：请求级句柄注册表键（与 AppServiceImpl.KB_HANDLE_MAP_KEY 同值，跨模块契约由测试钉住） */
    static final String KB_HANDLE_MAP_KEY = "kbHandleMap";
    /** R2：句柄编号游标键（与 AppServiceImpl.KB_HANDLE_SEQ_KEY 同值） */
    static final String KB_HANDLE_SEQ_KEY = "kbHandleSeq";

    private ToolResult queryKnowledgeBase(Map<String, Object> args, ToolContext ctx, long t0) {
        String kbId = (String) args.get("kb_id");
        if (kbId == null) kbId = (String) args.get("knowledge_base");
        String query = (String) args.get("query");
        if (query == null) query = (String) args.get("question");

        if (kbId == null || query == null) {
            return ToolResult.error("缺少参数：需要 kb_id 和 query", (int)(System.currentTimeMillis() - t0));
        }

        int topK = 10;
        if (args.containsKey("top_k")) {
            topK = ((Number) args.get("top_k")).intValue();
        }

        List<Map<String, Object>> chunks = kbOperationService.queryKnowledgeBase(kbId, query, topK);
        // R2：上游可能返回不可变 Map（如 knowledge 模块桩实现），统一拷贝为可变条目再追加 handle
        List<Map<String, Object>> mutableChunks = new java.util.ArrayList<>();
        if (chunks != null) {
            for (Map<String, Object> chunk : chunks) {
                mutableChunks.add(chunk == null ? new java.util.LinkedHashMap<>()
                        : new java.util.LinkedHashMap<>(chunk));
            }
        }

        // 结构化通道（app-chat-optimize 二批）：display_type+chunks 直达前端 KnowledgeChunksList。
        // R2（P0-5）：结构化通道保留真实 id（file_id/kb_id/chunk_index，前端用），条目追加 handle 供引用解析
        Map<String, Object> structured = new java.util.LinkedHashMap<>();
        structured.put("display_type", "knowledge_chunks");
        structured.put("kb_id", kbId);
        structured.put("query", query);
        structured.put("chunks", mutableChunks);

        // R2：请求级句柄注册表。AgentEngine 把 BaseContext.runtimeState 浅拷贝进 extensions——
        // 嵌套 Map/AtomicInteger 为共享引用，这里就地写入即请求级可见；未预填时降级为本调用内编号
        Map<String, Object> handleMap = ctx.getExtension(KB_HANDLE_MAP_KEY);
        if (handleMap == null) {
            handleMap = new java.util.LinkedHashMap<>();
        }
        java.util.concurrent.atomic.AtomicInteger seq = ctx.getExtension(KB_HANDLE_SEQ_KEY);
        if (seq == null) {
            seq = new java.util.concurrent.atomic.AtomicInteger(0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("知识库检索结果（共 ").append(mutableChunks.size())
          .append(" 条，引用资料时请使用 [cN] 编号标注来源）：\n\n");
        sb.append("查询：").append(query).append("\n\n");

        if (mutableChunks.isEmpty()) {
            sb.append("未找到相关结果。\n");
        } else {
            for (Map<String, Object> chunk : mutableChunks) {
                // 连续编号：同一请求内多次 query_kb 不断号，cN→真实 id 映射跨调用累积
                String handle = "c" + seq.incrementAndGet();
                chunk.put("handle", handle);
                Object fileId = chunk.get("file_id");
                Object chunkIndex = chunk.get("chunk_index");
                Map<String, Object> ref = new java.util.LinkedHashMap<>();
                ref.put("kbId", chunk.getOrDefault("kb_id", kbId));
                ref.put("fileId", fileId);
                ref.put("chunkIndex", chunkIndex instanceof Number ? chunkIndex : 0);
                handleMap.put(handle, ref);

                // 模型可见文本：[cN] fileName | 内容——不含任何真实 UUID
                sb.append("[").append(handle).append("] ")
                  .append(chunk.get("source") != null ? chunk.get("source") : "（未知来源）");
                if (chunk.containsKey("score")) {
                    sb.append(" (相关性: ").append(String.format("%.2f", chunk.get("score"))).append(")");
                }
                sb.append(" | ").append(chunk.getOrDefault("content", "")).append("\n\n");
            }
        }

        ToolResult result = ToolResult.success(sb.toString(), (int) (System.currentTimeMillis() - t0));
        result.setStructured(structured);
        return result;
    }
}
