package com.fastrag.module.agent.state;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ChatBot Agent运行状态类。
 *
 * <p>继承 {@link BaseState}，表示ChatBot Agent对话过程中的运行时状态。
 * 除了artifact外，还包含子Agent运行状态的列表，用于跟踪多Agent协作过程中的
 * 各子Agent运行进度和结果。</p>
 *
 * <p>职责：</p>
 * <ul>
 *   <li>mergeSubAgentRuns - 合并新的子Agent运行状态，按childThreadId匹配进行增量更新，
 *       不覆盖已有字段的空值</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatBotState extends BaseState {

    private List<SubAgentRunState> subagentRuns;

    public void mergeSubAgentRuns(List<SubAgentRunState> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        if (this.subagentRuns == null) {
            this.subagentRuns = new ArrayList<>(incoming);
            return;
        }
        Map<String, SubAgentRunState> existingMap = this.subagentRuns.stream()
                .collect(Collectors.toMap(SubAgentRunState::getChildThreadId, Function.identity(),
                        (a, b) -> a));
        for (SubAgentRunState state : incoming) {
            String key = state.getChildThreadId();
            if (existingMap.containsKey(key)) {
                SubAgentRunState existing = existingMap.get(key);
                if (state.getId() != null) {
                    existing.setId(state.getId());
                }
                if (state.getSubagentType() != null) {
                    existing.setSubagentType(state.getSubagentType());
                }
                if (state.getSubagentName() != null) {
                    existing.setSubagentName(state.getSubagentName());
                }
                if (state.getDescription() != null) {
                    existing.setDescription(state.getDescription());
                }
                if (state.getStatus() != null) {
                    existing.setStatus(state.getStatus());
                }
                if (state.getCompletedAt() != null) {
                    existing.setCompletedAt(state.getCompletedAt());
                }
                if (state.getResultPreview() != null) {
                    existing.setResultPreview(state.getResultPreview());
                }
                if (state.getError() != null) {
                    existing.setError(state.getError());
                }
                if (state.getArtifacts() != null) {
                    existing.setArtifacts(state.getArtifacts());
                }
                if (state.getCreatedAt() != null) {
                    existing.setCreatedAt(state.getCreatedAt());
                }
            } else {
                this.subagentRuns.add(state);
            }
        }
    }
}
