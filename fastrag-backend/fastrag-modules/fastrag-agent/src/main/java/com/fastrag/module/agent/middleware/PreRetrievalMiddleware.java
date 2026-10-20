package com.fastrag.module.agent.middleware;

import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并行预检索中间件（ADR-PARALLEL-PRE-RETRIEVAL，IF-003）。Order=13（尾部：全部
 * system 附加块注入之后、首轮模型调用之前——4 号位已被 SkillsMiddleware 占用，实现偏差已记录）。
 *
 * <p>编排层（AppServiceImpl）按豁免规则置位 {@link BaseContext#setPreRetrieveKb(Boolean)}；
 * 本中间件在首轮 beforeModelCall 时以当轮用户查询对绑定知识库逐库检索（topK=10），
 * 结果以 [cN] 句柄渲染为 system 附加块注入，句柄注册进共享 KB_HANDLE_MAP（与 query_kb
 * 工具通道共享 KB_HANDLE_SEQ 连续编号），来源写入 runtimeState 供编排层合并 sources 事件。</p>
 *
 * <p>失败语义：检索异常或空结果不注入、对话照常（fail-open）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreRetrievalMiddleware implements AgentMiddleware {

    /** 与 KnowledgeToolExecutor.KB_HANDLE_MAP_KEY/KB_HANDLE_SEQ_KEY 同值（跨模块契约由测试钉住） */
    static final String KB_HANDLE_MAP_KEY = "kbHandleMap";
    static final String KB_HANDLE_SEQ_KEY = "kbHandleSeq";
    /** 预检索来源 runtimeState 键：编排层合并 sources 事件与 references 落库 */
    public static final String PRE_RETRIEVAL_SOURCES_KEY = "preRetrievalSources";
    private static final int PRE_RETRIEVE_TOP_K = 10;
    private static final int INJECT_MAX_CHARS = 5000;

    private final KbOperationService kbOperationService;

    @Override
    public int getOrder() { return 13; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (!Boolean.TRUE.equals(context.getPreRetrieveKb())
                || context.getKnowledges() == null || context.getKnowledges().isEmpty()) {
            return context;
        }
        String query = extractUserQuery(messages);
        if (query == null || query.isBlank()) {
            return context;
        }
        try {
            List<Map<String, Object>> chunks = new java.util.ArrayList<>();
            for (String kbId : context.getKnowledges()) {
                List<Map<String, Object>> part =
                        kbOperationService.queryKnowledgeBase(kbId, query, PRE_RETRIEVE_TOP_K);
                if (part == null) continue;
                for (Map<String, Object> chunk : part) {
                    Map<String, Object> copy = chunk == null
                            ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(chunk);
                    copy.putIfAbsent("kb_id", kbId);
                    chunks.add(copy);
                }
            }
            if (chunks.isEmpty()) {
                log.info("[PreRetrieval] no hits for '{}', skip injection", query);
                return context;
            }

            Map<String, Object> handleMap = handleMapOf(context);
            AtomicInteger seq = seqOf(context);
            StringBuilder block = new StringBuilder();
            block.append("\n\n【知识库预检索】以下是知识库中与问题相关的资料：回答时基于这些资料，")
                 .append("并在对应句子末尾使用 [cN] 编号标注来源；如与问题无关可忽略。\n\n");
            List<Map<String, Object>> sources = new java.util.ArrayList<>();
            int injected = 0;
            for (Map<String, Object> chunk : chunks) {
                if (block.length() > INJECT_MAX_CHARS) break;
                String handle = "c" + seq.incrementAndGet();
                chunk.put("handle", handle);
                Object fileId = chunk.get("file_id");
                Map<String, Object> ref = new java.util.LinkedHashMap<>();
                ref.put("kbId", chunk.getOrDefault("kb_id", ""));
                ref.put("fileId", fileId);
                ref.put("chunkIndex", chunk.get("chunk_index") instanceof Number n ? n.intValue() : 0);
                handleMap.put(handle, ref);

                block.append("[").append(handle).append("] ")
                     .append(chunk.get("source") != null ? chunk.get("source") : "（未知来源）");
                if (chunk.get("score") instanceof Number score) {
                    block.append(" (相关性: ").append(String.format("%.2f", score.doubleValue())).append(")");
                }
                block.append(" | ").append(chunk.getOrDefault("content", "")).append("\n\n");

                Map<String, Object> src = new java.util.LinkedHashMap<>();
                src.put("fileName", chunk.get("source"));
                src.put("score", chunk.get("score"));
                src.put("fileId", fileId);
                src.put("kbId", ref.get("kbId"));
                src.put("chunkIndex", ref.get("chunkIndex"));
                src.put("content", chunk.get("content"));
                sources.add(src);
                injected++;
            }
            if (injected == 0) {
                return context;
            }
            for (ChatMessage msg : messages) {
                if ("system".equals(msg.getRole())) {
                    msg.setContent((msg.getContent() == null ? "" : msg.getContent()) + block);
                    break;
                }
            }
            if (context.getRuntimeState() != null) {
                context.getRuntimeState().put(PRE_RETRIEVAL_SOURCES_KEY, sources);
                context.getRuntimeState().putIfAbsent(KB_HANDLE_MAP_KEY, handleMap);
                context.getRuntimeState().putIfAbsent(KB_HANDLE_SEQ_KEY, seq);
            }
            log.info("[PreRetrieval] injected {} chunks ({} chars) for '{}'", injected, block.length(), query);
        } catch (Exception e) {
            log.warn("[PreRetrieval] failed, skip injection: {}", e.getMessage());
        }
        return context;
    }

    /** 当轮用户查询 = 消息列表中最后一条非空 user 消息 */
    private String extractUserQuery(List<ChatMessage> messages) {
        String query = null;
        for (ChatMessage msg : messages) {
            if ("user".equals(msg.getRole()) && msg.getContent() != null && !msg.getContent().isBlank()) {
                query = msg.getContent();
            }
        }
        return query;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleMapOf(BaseContext context) {
        if (context.getRuntimeState() == null) return new java.util.LinkedHashMap<>();
        Object m = context.getRuntimeState().get(KB_HANDLE_MAP_KEY);
        return m instanceof Map ? (Map<String, Object>) m : new java.util.LinkedHashMap<>();
    }

    private AtomicInteger seqOf(BaseContext context) {
        if (context.getRuntimeState() == null) return new AtomicInteger();
        Object s = context.getRuntimeState().get(KB_HANDLE_SEQ_KEY);
        return s instanceof AtomicInteger ? (AtomicInteger) s : new AtomicInteger();
    }
}
