package com.fastrag.module.agent.state;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Agent运行基础状态基类。
 *
 * <p>表示系统中所有Agent运行状态的基类，包含运行过程中产生的artifact（产物）列表。
 * 提供mergeArtifacts方法用于合并新的artifact到已有列表中，使用LinkedHashSet去重，
 * 保持插入顺序。被 {@link ChatBotState} 和 {@link SubAgentRunState} 等状态类继承。</p>
 */
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
