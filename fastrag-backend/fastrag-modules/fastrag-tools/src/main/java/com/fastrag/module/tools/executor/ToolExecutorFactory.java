package com.fastrag.module.tools.executor;

/**
 * 工具执行器工厂 —— 根据 tool type 路由到对应的 {@link ToolExecutor} 实现。
 *
 * <p>Spring 自动注入所有 {@link ToolExecutor} 实现到 {@code executors} 列表，
 * {@link #getExecutor(String)} 方法遍历列表匹配 {@code type}，返回对应的执行器。</p>
 *
 * <p>已注册的执行器类型包括：</p>
 * <ul>
 *   <li>{@code http} - {@link HttpToolExecutor}，自定义 HTTP API 工具</li>
 *   <li>{@code mcp} - {@link McpToolExecutor}，MCP 协议工具（stdio/sse）</li>
 *   <li>{@code skill} - {@link SkillToolExecutor}，技能读取/激活</li>
 *   <li>{@code knowledge} - {@link KnowledgeToolExecutor}，知识库查询</li>
 *   <li>{@code builtin} - {@link InstallSkillToolExecutor}，内置安装技能</li>
 * </ul>
 */
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutorFactory {
    private final List<ToolExecutor> executors;

    public ToolExecutor getExecutor(String type) {
        return executors.stream()
            .filter(e -> e.getType().equals(type))
            .findFirst()
            .orElse(null);
    }
}
