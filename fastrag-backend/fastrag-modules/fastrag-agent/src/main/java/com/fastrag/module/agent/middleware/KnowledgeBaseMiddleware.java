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
 * <p>当 context.knowledges 非空时，自动注册内置知识库检索工具供 LLM 使用；
 * 并向 system 消息注入能力提示与检索决策启发（全 agentic 化，2026-09-30——
 * 预检索管线移除后，8B 级模型对「该不该查库」缺乏指引，常该查不查）。</p>
 */
@Slf4j
@Component
public class KnowledgeBaseMiddleware implements AgentMiddleware {

    /** KB 能力提示（与 FilesystemMiddleware 沙箱提示同型）：告知模型知识库可用、何时该检索、如何引用 */
    private static final String KB_CAPABILITY_HINT =
            "\n\n【知识库已接入】本应用绑定了知识库：涉及业务、文档、规程、FAQ 类的问题，请先调用 query_kb 检索相关资料"
            + "（可多次检索、可更换关键词重试），并基于返回的资料回答，引用资料时在对应句子末尾以 [文件名] 标注来源；"
            + "与知识库无关的问题（如通用编程、代码执行、常识问答）无需检索，直接回答。";

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

            // 能力提示注入：追加到首条 system 消息（messages 由引擎构建后传入，此处可变）
            for (ChatMessage msg : messages) {
                if ("system".equals(msg.getRole())) {
                    String base = msg.getContent() == null ? "" : msg.getContent();
                    msg.setContent(base + KB_CAPABILITY_HINT);
                    break;
                }
            }
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
