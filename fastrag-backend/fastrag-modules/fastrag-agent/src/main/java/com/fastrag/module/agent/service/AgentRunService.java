package com.fastrag.module.agent.service;

import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentRunCreateDTO;
import com.fastrag.module.agent.entity.AgentRun;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface AgentRunService {

    Map<String, Object> createRun(AgentRunCreateDTO dto, User user);
    AgentRun getRun(String runId);
    AgentRun getRunForUser(String runId, User user);
    Map<String, Object> cancelRun(String runId, User user);
    SseEmitter streamEvents(String runId, String afterSeq, User user);
    AgentRun getActiveRunByThread(String threadId, User user);
    void updateStatus(String runId, String status);
    void setTerminalStatus(String runId, String status, String errorType, String errorMessage);
}
