package com.fastrag.module.agent.state;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 子Agent运行状态对象。
 *
 * <p>表示一个子Agent的完整运行状态，包含运行ID、子Agent类型和名称、
 * 子线程ID、描述、运行状态、创建时间、完成时间、结果预览、错误信息和产物列表。
 * 作为 {@link ChatBotState} 的子状态，记录多Agent协作中每个子Agent的执行情况。</p>
 */
@Data
public class SubAgentRunState {

    private String id;

    private String subagentType;

    private String subagentName;

    private String childThreadId;

    private String description;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private String resultPreview;

    private String error;

    private List<String> artifacts;
}
