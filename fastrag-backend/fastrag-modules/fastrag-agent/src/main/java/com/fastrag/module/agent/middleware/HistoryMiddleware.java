package com.fastrag.module.agent.middleware;

import cn.hutool.core.util.StrUtil;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 会话历史注入中间件（R1/P0-6 历史注入最小恢复版）。
 *
 * <p><b>背景</b>：App 对话链路改为全 agentic 化后，引擎只带当前 query 单消息进入模型，
 * 多轮会话失忆。本中间件是<b>最小恢复版</b>：把 AppServiceImpl 预填进
 * {@code BaseContext.runtimeState} 的最近 N 轮历史（key={@link #HISTORY_KEY}，
 * 条目为 {@code {role, content}} 投影）作为 system prompt 附加块注入，供模型参考上下文。</p>
 *
 * <p><b>契约</b>：</p>
 * <ul>
 *   <li>runtimeState 无该键 / 空列表 / 条目缺 content 时，不改动消息（直聊、其他引擎入口零影响）</li>
 *   <li>同一 run 只注入一次：注入后置 runtimeState 哨兵 {@link #INJECTED_FLAG}，
 *       工具循环多轮模型调用不重复追加</li>
 *   <li>执行顺序 order=12：避开既有 0-11 区间，排链尾追加（不干扰 0-11 各中间件的注入语义）</li>
 * </ul>
 *
 * <p><b>Deviation 登记</b>：真多轮 messages 注入（user/assistant 消息序列回放）需引擎侧
 * 消息构建立项，本期以 system 附加块替代；历史块仅供上下文参考，不参与摘要压缩。</p>
 */
@Slf4j
@Component
public class HistoryMiddleware implements AgentMiddleware {

    /** runtimeState 中会话历史列表的键（AppServiceImpl 预填，条目 {role, content}） */
    public static final String HISTORY_KEY = "conversationHistory";

    /** 同一 run 注入一次的哨兵键 */
    public static final String INJECTED_FLAG = "conversationHistoryInjected";

    private static final String HISTORY_HEADER = "\n\n以下是本会话历史，仅供上下文参考：\n";

    @Override
    public int getOrder() { return 12; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        if (context == null || messages == null || messages.isEmpty()) {
            return context;
        }
        Map<String, Object> state = context.getRuntimeState();
        if (state.get(INJECTED_FLAG) != null) {
            return context; // 工具循环多轮：只注入一次
        }
        String block = renderHistory(state.get(HISTORY_KEY));
        if (block == null) {
            return context;
        }
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                msg.setContent(StrUtil.nullToEmpty(msg.getContent()) + block);
                state.put(INJECTED_FLAG, Boolean.TRUE);
                log.debug("[HistoryMiddleware] Injected conversation history block ({} chars)", block.length());
                break;
            }
        }
        return context;
    }

    /**
     * 渲染历史附加块；无有效历史（非 List / 空 / 全部条目无 content）返回 null 表示不注入。
     * role 投影保持原值（user / assistant），格式 "role: content"，与 AppServiceImpl 投影约定一致。
     */
    @SuppressWarnings("unchecked")
    private String renderHistory(Object raw) {
        if (!(raw instanceof List<?> history) || history.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(HISTORY_HEADER);
        int used = 0;
        for (Object entry : history) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Object content = ((Map<String, Object>) entry).get("content");
            if (content == null || content.toString().isBlank()) {
                continue;
            }
            Object role = ((Map<String, Object>) entry).get("role");
            sb.append(role == null ? "user" : role).append(": ").append(content).append('\n');
            used++;
        }
        if (used == 0) {
            return null;
        }
        return sb.toString();
    }
}
