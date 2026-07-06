package com.fastrag.module.agent.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.agent.context.CurrentUser;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentRunCreateDTO;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.service.AgentRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * REST controller for agent run operations.
 * Provides endpoints to create runs, retrieve run status,
 * cancel runs, stream run events via SSE, and query active runs.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentRunController {

    private final AgentRunService agentRunService;

    /**
     * Create a new agent run.
     *
     * @param dto  the run creation DTO
     * @param user the current authenticated user
     * @return the created run data
     */
    @PostMapping("/runs")
    public ApiResponse<Map<String, Object>> createRun(
            @RequestBody @jakarta.validation.Valid AgentRunCreateDTO dto,
            @CurrentUser User user) {
        Map<String, Object> runData = agentRunService.createRun(dto, user);
        return ApiResponse.success(runData);
    }

    /**
     * Get a single run by its ID.
     *
     * @param runId the run ID
     * @param user  the current authenticated user
     * @return the agent run entity
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<AgentRun> getRun(
            @PathVariable String runId,
            @CurrentUser User user) {
        AgentRun run = agentRunService.getRunForUser(runId, user);
        return ApiResponse.success(run);
    }

    /**
     * Cancel a running agent run.
     *
     * @param runId the run ID
     * @param user  the current authenticated user
     * @return the updated run data
     */
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Map<String, Object>> cancelRun(
            @PathVariable String runId,
            @CurrentUser User user) {
        Map<String, Object> runData = agentRunService.cancelRun(runId, user);
        return ApiResponse.success(runData);
    }

    /**
     * Stream run events via Server-Sent Events (SSE).
     * The client can optionally specify an {@code afterSeq} parameter to
     * receive only events that occurred after the given sequence number.
     *
     * @param runId    the run ID
     * @param afterSeq optional sequence number to resume from
     * @param user     the current authenticated user
     * @return an SseEmitter for streaming events
     */
    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRunEvents(
            @PathVariable String runId,
            @RequestParam(required = false) String afterSeq,
            @CurrentUser User user) {
        return agentRunService.streamEvents(runId, afterSeq, user);
    }

    /**
     * Get the currently active (pending or running) run in a thread.
     *
     * @param threadId the thread ID
     * @param user     the current authenticated user
     * @return the active agent run, or null if none is active
     */
    @GetMapping("/thread/{threadId}/active_run")
    public ApiResponse<AgentRun> getActiveRun(
            @PathVariable String threadId,
            @CurrentUser User user) {
        AgentRun run = agentRunService.getActiveRunByThread(threadId, user);
        return ApiResponse.success(run);
    }
}
