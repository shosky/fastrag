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
 * Agent运行管理REST控制器，提供Agent运行的生命周期管理和事件流接口。
 *
 * <p>提供的REST API端点：
 * <ul>
 *   <li>POST /runs - 创建新的Agent运行任务</li>
 *   <li>GET /runs/{runId} - 获取指定运行的详情</li>
 *   <li>POST /runs/{runId}/cancel - 取消正在运行的Agent任务</li>
 *   <li>GET /runs/{runId}/events - 通过SSE（Server-Sent Events）流式推送运行事件，支持afterSeq参数断点续传</li>
 *   <li>GET /thread/{threadId}/active_run - 获取指定会话线程中当前活跃的运行任务</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>所有接口通过@CurrentUser注解获取当前用户，进行权限校验</li>
 *   <li>createRun创建运行后通过异步线程池执行（agentTaskExecutor），立即返回运行数据</li>
 *   <li>streamRunEvents返回SseEmitter，客户端通过EventSource API实时接收Agent执行过程中的各类事件</li>
 *   <li>cancelRun通过AgentRunService向运行中的任务发送取消信号</li>
 *   <li>getActiveRun用于前端判断某个会话线程中是否已有正在执行的Agent任务，避免重复提交</li>
 * </ul></p>
 *
 * @see AgentRunService Agent运行业务服务
 * @see AgentRunCreateDTO 运行创建请求DTO
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
