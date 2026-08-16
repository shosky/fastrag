package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;

import java.util.Map;

/**
 * 工具执行器接口。
 *
 * <p>定义所有Agent可调用工具的统一执行接口。每种工具类型（knowledge、http、skill、database等）
 * 都有自己的实现类，通过 getType() 返回类型标识，方法 {@code execute()} 执行具体工具操作。</p>
 *
 * <p>由 {@code ToolRegistry} 统一管理所有执行器实例，在Agent调用工具时按类型路由到对应执行器。</p>
 */
public interface ToolExecutor {
    String getType();
    ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx);
}
