package com.fastrag.module.agent.state;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
