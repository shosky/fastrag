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
 * 知识库中间件。注册知识库工具(list_kbs, query_kb)。
 *
 * <p>执行顺序: Order=3。</p>
 *
 * <p>当 context.knowledges 非空时，自动注册内置知识库检索工具供 LLM 使用；
 * 并向 system 消息注入能力提示与检索决策启发（全 agentic 化，2026-09-30）。
 * 2026-10-19 修订（诊断「绑库应用对话不检索」）：提示词由「模型自判豁免」改为强门控——
 * 除纯代码执行/数学计算/日常闲聊外一律先检索（弱 8B 级模型自分类常把领域题判成常识题，
 * 该查不查，且把引用格式 [文件名] 当占位符幻觉输出）；引用口径统一到执行器的 [cN] 编号。
 * query_kb 注册 schema 参数名与 KnowledgeToolExecutor 契约对齐（kb_id/top_k），
 * 绑定库烙进 config.kbIds 供执行器在模型未传 kb_id 时缺省回落。</p>
 */
@Slf4j
@Component
public class KnowledgeBaseMiddleware implements AgentMiddleware {

    /** KB 能力提示（与 FilesystemMiddleware 沙箱提示同型）：强门控——默认必查，豁免面收窄到明确无关场景 */
    private static final String KB_CAPABILITY_HINT =
            "\n\n【知识库已接入】本应用绑定了知识库：除纯代码执行、数学计算、日常闲聊外，"
            + "凡可能由知识库资料回答的问题——业务、文档、规程、制度、产品、FAQ，以及领域术语、定义、概念等解释类问题——"
            + "必须先调用 query_kb 检索资料，并基于返回的资料回答；即使你自认为知道答案，也应以检索结果为准"
            + "（可多次检索、可更换关键词重试）。仅在检索确认无相关内容后，才可用通用知识回答并向用户说明。"
            + "引用资料时在对应句子末尾使用检索结果中的 [cN] 编号标注来源。";

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

            // query_kb（2026-10-19 修订：schema 参数名与 KnowledgeToolExecutor 契约对齐——kb_id/top_k；
            // kb_id 可选，模型未传时执行器回落到 config.kbIds 绑定库，无需预知库 ID）
            // query_kb（2026-10-20 修订：单库绑定 schema 不暴露 kb_id——8B 级模型面对可选参数会
            // 「填空」传空串（如 {"query":"新办etc","kb_id":""}），不如不给它这个参数位；
            // 多库绑定保留 kb_id 供模型指定范围。执行器侧对空串/缺省一律回落 config.kbIds 兜底）
            boolean multiKb = context.getKnowledges().size() > 1;
            java.util.Map<String, Object> queryKbProperties = new java.util.LinkedHashMap<>();
            queryKbProperties.put("query", Map.of("type", "string", "description", "查询问题"));
            if (multiKb) {
                queryKbProperties.put("kb_id", Map.of("type", "string", "description",
                        "知识库ID（已绑定多个知识库时用于指定检索范围；不确定可先调 list_kbs 查询）"));
            }
            queryKbProperties.put("top_k", Map.of("type", "integer", "description", "返回结果数量", "default", 10));
            tools.add(ToolDefinition.builder()
                    .toolId("builtin_query_kb")
                    .name("query_kb")
                    .description("在知识库中检索与查询相关的文档片段（已绑定的知识库可直接检索，无需提供 kb_id）")
                    .type("knowledge")
                    .inputSchema(Map.of(
                            "type", "object",
                            "properties", queryKbProperties,
                            "required", List.of("query")
                    ))
                    .config(Map.of("action", "query_kb", "kbIds", context.getKnowledges()))
                    .build());

            log.info("[KnowledgeBaseMiddleware] Registered 2 knowledge base tools (list_kbs, query_kb)");

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
