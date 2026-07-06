package com.fastrag.module.agent.state;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.List;

@Data
public class BaseState {

    private List<String> artifacts;

    public void mergeArtifacts(List<String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        if (this.artifacts == null) {
            this.artifacts = incoming;
            return;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>(this.artifacts);
        set.addAll(incoming);
        this.artifacts = List.copyOf(set);
    }
}
