package com.fastrag.module.tools.executor;

/**
 * 工具执行结果 —— 统一封装所有工具执行器的返回值。
 *
 * <p>包含执行状态（成功/失败）、文本输出、结构化数据、耗时和错误信息，
 * 通过静态工厂方法 {@link #success} 和 {@link #error} 快速构建实例。</p>
 *
 * <p>核心字段：</p>
 * <ul>
 *   <li>{@code success} - 执行是否成功</li>
 *   <li>{@code output} - 文本输出（LLM 可直接消费）</li>
 *   <li>{@code structured} - 结构化数据（可选，供下游解析）</li>
 *   <li>{@code durationMs} - 执行耗时（毫秒）</li>
 *   <li>{@code error} - 错误信息（失败时填写）</li>
 * </ul>
 */
import lombok.Data;

import java.util.Map;

@Data
public class ToolResult {
    private boolean success;
    private String output;
    private Map<String, Object> structured;
    private int durationMs;
    private String error;

    public static ToolResult success(String output, int durationMs) {
        ToolResult r = new ToolResult();
        r.setSuccess(true);
        r.setOutput(output);
        r.setDurationMs(durationMs);
        return r;
    }

    public static ToolResult error(String error, int durationMs) {
        ToolResult r = new ToolResult();
        r.setSuccess(false);
        r.setError(error);
        r.setDurationMs(durationMs);
        return r;
    }
}
