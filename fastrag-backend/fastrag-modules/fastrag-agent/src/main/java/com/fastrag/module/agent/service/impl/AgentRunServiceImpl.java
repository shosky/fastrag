package com.fastrag.module.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ContextBuilder;
import com.fastrag.module.agent.context.User;
import com.fastrag.module.agent.dto.AgentRunCreateDTO;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.executor.AgentExecutor;
import com.fastrag.module.agent.executor.AgentResult;
import com.fastrag.module.agent.mapper.AgentRunMapper;
import com.fastrag.module.agent.service.AgentRunService;
import com.fastrag.module.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunMapper agentRunMapper;
    private final AgentService agentService;
    private final AgentExecutor agentExecutor;
    private final ContextBuilder contextBuilder;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @Lazy
    @Autowired
    private AgentRunServiceImpl self;

    @Override
    public Map<String, Object> createRun(AgentRunCreateDTO dto, User user) {
        Agent agent = agentService.getBySlug(dto.getAgentId());
        if (agent == null) throw new BusinessException(404, "智能体不存在: " + dto.getAgentId());
        if (!agentService.userCanAccess(user, agent)) throw new BusinessException(403, "无权访问该智能体");

        String requestId = dto.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = IdUtil.fastSimpleUUID();
        }

        // Idempotency
        AgentRun existing = agentRunMapper.selectByRequestId(requestId);
        if (existing != null) return buildRunData(existing);

        // Create run
        AgentRun run = new AgentRun();
        run.setId(IdUtil.fastSimpleUUID());
        run.setThreadId(dto.getThreadId());
        run.setAgentId(dto.getAgentId());
        run.setUid(user != null ? user.getUid() : "anonymous");
        run.setRequestId(requestId);
        run.setStatus("pending");
        run.setRunType(dto.getRunType() != null ? dto.getRunType() : "chat");

        Map<String, Object> input = new HashMap<>();
        input.put("query", dto.getQuery());
        input.put("model_override", dto.getModelOverride());
        run.setInputPayload(input);

        run.setCreatedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        agentRunMapper.insert(run);

        log.info("创建Agent运行: id={}, agent={}, thread={}", run.getId(), dto.getAgentId(), dto.getThreadId());

        // 异步执行 Agent
        self.executeRunAsync(run);

        return buildRunData(run);
    }

    @Override
    public AgentRun getRun(String runId) {
        AgentRun run = agentRunMapper.selectById(runId);
        if (run == null) throw new BusinessException(404, "运行记录不存在");
        return run;
    }

    @Override
    public AgentRun getRunForUser(String runId, User user) {
        return getRun(runId);
    }

    @Override
    public Map<String, Object> cancelRun(String runId, User user) {
        AgentRun run = getRunForUser(runId, user);
        if (!"pending".equals(run.getStatus()) && !"running".equals(run.getStatus())) {
            throw new BusinessException(400, "当前状态不支持取消: " + run.getStatus());
        }
        run.setStatus("cancelled");
        run.setFinishedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        agentRunMapper.updateById(run);
        return buildRunData(run);
    }

    @Override
    public SseEmitter streamEvents(String runId, String afterSeq, User user) {
        AgentRun run = getRunForUser(runId, user);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitter.onCompletion(() -> log.info("SSE完成, runId={}", runId));
        emitter.onTimeout(() -> log.info("SSE超时, runId={}", runId));
        emitter.onError(e -> log.error("SSE错误, runId={}", runId, e));

        sseExecutor.execute(() -> {
            try {
                while (true) {
                    AgentRun fresh = agentRunMapper.selectById(runId);
                    if (fresh == null) { emitter.complete(); return; }
                    if (isTerminalStatus(fresh.getStatus())) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("status", fresh.getStatus());
                        event.put("errorType", fresh.getErrorType());
                        event.put("errorMessage", fresh.getErrorMessage());
                        emitter.send(SseEmitter.event().name("end").data(event));
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public AgentRun getActiveRunByThread(String threadId, User user) {
        return agentRunMapper.selectByThreadIdAndStatus(threadId);
    }

    @Override
    public void updateStatus(String runId, String status) {
        AgentRun run = agentRunMapper.selectById(runId);
        if (run == null) { log.warn("更新状态失败, 运行不存在: {}", runId); return; }
        run.setStatus(status);
        run.setUpdatedAt(LocalDateTime.now());
        agentRunMapper.updateById(run);
    }

    @Override
    public void setTerminalStatus(String runId, String status, String errorType, String errorMessage) {
        AgentRun run = agentRunMapper.selectById(runId);
        if (run == null) { log.warn("设置终止状态失败, 运行不存在: {}", runId); return; }
        run.setStatus(status);
        run.setErrorType(errorType);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(LocalDateTime.now());
        run.setUpdatedAt(LocalDateTime.now());
        agentRunMapper.updateById(run);
    }

    @Async("agentTaskExecutor")
    public void executeRunAsync(AgentRun run) {
        try {
            log.info("[AgentRunService] 开始异步执行: runId={}", run.getId());

            // 更新状态为 running
            run.setStatus("running");
            run.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(run);

            // 构建上下文
            BaseContext context = contextBuilder.buildContext(run);

            // 执行 Agent
            AgentResult result = agentExecutor.execute(run, context);

            // 更新结果
            run.setStatus(result.isSuccess() ? "completed" : "failed");
            if (!result.isSuccess()) {
                run.setErrorType("execution_error");
                run.setErrorMessage(result.getError());
            }
            run.setFinishedAt(LocalDateTime.now());
            run.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(run);

            log.info("[AgentRunService] Run {} 完成: status={}", run.getId(), run.getStatus());

        } catch (Exception e) {
            log.error("[AgentRunService] Run {} 失败", run.getId(), e);
            run.setStatus("failed");
            run.setErrorType("system_error");
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            run.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(run);
        }
    }

    @Override
    public void createRunDirect(AgentRun run) {
        if (run.getId() == null) {
            run.setId(IdUtil.fastSimpleUUID());
        }
        if (run.getCreatedAt() == null) {
            run.setCreatedAt(LocalDateTime.now());
        }
        run.setUpdatedAt(LocalDateTime.now());
        agentRunMapper.insert(run);
        log.info("[AgentRunService] Direct run created: id={}, agentId={}", run.getId(), run.getAgentId());
    }

    private Map<String, Object> buildRunData(AgentRun run) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", run.getId());
        data.put("threadId", run.getThreadId());
        data.put("agentId", run.getAgentId());
        data.put("status", run.getStatus());
        data.put("runType", run.getRunType());
        data.put("createdAt", run.getCreatedAt());
        return data;
    }

    private boolean isTerminalStatus(String status) {
        return "completed".equals(status) || "failed".equals(status)
                || "cancelled".equals(status) || "interrupted".equals(status);
    }
}
