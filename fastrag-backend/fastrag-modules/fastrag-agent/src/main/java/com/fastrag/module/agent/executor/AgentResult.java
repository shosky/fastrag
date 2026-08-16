package com.fastrag.module.agent.executor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent执行结果，封装单次Agent引擎运行的最终输出。
 *
 * <p>核心字段包括：
 * <ul>
 *   <li>success - 执行是否成功</li>
 *   <li>answer - LLM最终回答文本</li>
 *   <li>error - 错误信息（失败时非空）</li>
 *   <li>toolCallRecords - 本次执行中所有工具调用的详细记录</li>
 *   <li>totalDurationMs - 整体执行耗时（毫秒）</li>
 * </ul></p>
 *
 * <p>提供success()和error()两个静态工厂方法便于快速构建结果实例。
 * 内部嵌套类{@link ToolCallRecord}记录单次工具调用的工具名称、参数、执行结果及耗时。</p>
 */
@Data
public class AgentResult {
    private boolean success;
    private String answer;
    private String error;
    private List<ToolCallRecord> toolCallRecords = new ArrayList<>();
    private int totalDurationMs;

    public static AgentResult success(String answer) {
        AgentResult r = new AgentResult();
        r.setSuccess(true);
        r.setAnswer(answer);
        return r;
    }

    public static AgentResult error(String error) {
        AgentResult r = new AgentResult();
        r.setSuccess(false);
        r.setError(error);
        return r;
    }

    @Data
    public static class ToolCallRecord {
        private String toolName;
        private String toolId;
        private Map<String, Object> arguments;
        private boolean success;
        private String output;
        private String error;
        private int durationMs;
    }
}
