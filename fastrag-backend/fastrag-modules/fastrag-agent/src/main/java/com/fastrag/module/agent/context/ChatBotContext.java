package com.fastrag.module.agent.context;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChatBotContext extends BaseContext {

    @ConfigField(type = "list", kind = "subagents")
    private List<String> subagents;

    private transient String parentThreadId;

    private transient String fileThreadId;

    private transient String skillsThreadId;

    private transient boolean subagentRuntime;
}
