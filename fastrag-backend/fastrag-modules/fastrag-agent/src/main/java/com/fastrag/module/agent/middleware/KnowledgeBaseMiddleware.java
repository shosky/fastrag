package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库中间件。注册知识库工具(list_kbs, query_kb, find_kb_document)。
 *
 * <p>执行顺序: Order=3。</p>
 *
 * <p>当 context.knowledges 非空时，自动注册内置知识库检索工具供 LLM 使用。</p>
 */
@Slf4j
@Component
public class KnowledgeBaseMiddleware implements AgentMiddleware {

    @Override
    public int getOrder() { return 3; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (context.getKnowledges() == null || context.getKnowledges().isEmpty()) {
            return context;
        }

        try {
            log.info("[KnowledgeBaseMiddleware] Registering KB tools for {} knowledge bases",
                    context.getKnowledges().size());

            // list_kbs
            tools.add(ToolDefinition.builder()
                    .toolId("builtin_list_kbs")
                    .name("list_kbs")
                    .description("列出当前可用的知识库列表")
                    .type("knowledge")
                    .inputSchema(Map.of("type", "object", "properties", Map.of()))
                    .config(Map.of("action", "list_kbs"))
                    .build());

            // query_kb
            tools.add(ToolDefinition.builder()
                    .toolId("builtin_query_kb")
                    .name("query_kb")
                    .description("在知识库中检索与查询相关的文档片段")
                    .type("knowledge")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string", "description", "查询问题"),
                                    "topK", Map.of("type", "integer", "description", "返回结果数量", "default", 10),
                                    "knowledgeId", Map.of("type", "string", "description", "指定知识库ID（可选）")
                            ),
                            "required", List.of("query")
                    ))
                    .config(Map.of("action", "query_kb"))
                    .build());

            // find_kb_document
            tools.add(ToolDefinition.builder()
                    .toolId("builtin_find_kb_document")
                    .name("find_kb_document")
                    .description("在知识库中查找特定文档")
                    .type("knowledge")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "keyword", Map.of("type", "string", "description", "搜索关键词")
                            ),
                            "required", List.of("keyword")
                    ))
                    .config(Map.of("action", "find_kb_document"))
                    .build());

            log.info("[KnowledgeBaseMiddleware] Registered 3 knowledge base tools");
        } catch (Exception e) {
            log.error("[KnowledgeBaseMiddleware] Failed to register KB tools: {}", e.getMessage());
        }

        return context;
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        return null;
    }
}
