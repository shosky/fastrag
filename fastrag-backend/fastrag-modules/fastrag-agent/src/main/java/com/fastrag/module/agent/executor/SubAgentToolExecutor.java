package com.fastrag.module.agent.executor;

import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolExecutor;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 子智能体工具执行器。
 * 当 SubAgentMiddleware 未拦截 task 工具调用时的降级方案。
 *
 * <p>type = "subagent"。</p>
 */
@Slf4j
@Component
public class SubAgentToolExecutor implements ToolExecutor {

    @Override
    public String getType() {
        return "subagent";
    }

    @Override
    public ToolResult execute(ToolDefinition tool, Map<String, Object> args, ToolContext ctx) {
        // 正常情况下 task 工具应由 SubAgentMiddleware.interceptToolCall() 拦截处理
        // 此处作为降级方案
        return ToolResult.error(
                "SubAgent execution requires SubAgentMiddleware. " +
                "Please ensure SubAgentMiddleware is registered in the middleware chain.", 0);
    }
}
