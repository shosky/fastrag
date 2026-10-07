package com.fastrag.module.agent.service.impl;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ContextBuilder;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.event.RunEventPublisher;
import com.fastrag.module.agent.executor.AgentEngine;
import com.fastrag.module.agent.executor.AgentResult;
import com.fastrag.module.agent.mapper.AgentRunMapper;
import com.fastrag.module.agent.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentRunServiceImpl 单元测试（M2-4，D12）：agent REST 路径切到 AgentEngine
 * （带完整中间件链），技能装配唯一归属 SkillsMiddleware，agent_run 生命周期不变。
 */
@ExtendWith(MockitoExtension.class)
class AgentRunServiceImplTest {

    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentService agentService;
    @Mock
    private AgentEngine agentEngine;
    @Mock
    private ContextBuilder contextBuilder;
    @Mock
    private RunEventPublisher eventPublisher;

    private AgentRunServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentRunServiceImpl(agentRunMapper, agentService, agentEngine,
                contextBuilder, eventPublisher);
        ReflectionTestUtils.setField(service, "streamPollIntervalMs", 10L);
    }

    private AgentRun newRun() {
        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setAgentId("agent-1");
        run.setUid("u1");
        run.setStatus("pending");
        run.setInputPayload(Map.of("query", "hi"));
        return run;
    }

    @Test
    void executeRunAsync_delegatesToAgentEngineAndCompletesRun() throws Exception {
        AgentRun run = newRun();
        when(contextBuilder.buildContext(run)).thenReturn(new BaseContext());
        when(agentEngine.executeSync(eq(run), any(BaseContext.class)))
                .thenReturn(AgentResult.success("done"));

        service.executeRunAsync(run);

        verify(agentEngine).executeSync(eq(run), any(BaseContext.class));
        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunMapper, times(2)).updateById(captor.capture());
        assertEquals("completed", captor.getAllValues().get(1).getStatus());
    }

    @Test
    void executeRunAsync_engineFailureMarksRunFailed() throws Exception {
        AgentRun run = newRun();
        when(contextBuilder.buildContext(run)).thenReturn(new BaseContext());
        when(agentEngine.executeSync(eq(run), any(BaseContext.class)))
                .thenReturn(AgentResult.error("boom"));

        service.executeRunAsync(run);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunMapper, times(2)).updateById(captor.capture());
        AgentRun finalState = captor.getAllValues().get(1);
        assertEquals("failed", finalState.getStatus());
        assertEquals("execution_error", finalState.getErrorType());
        assertEquals("boom", finalState.getErrorMessage());
    }

    @Test
    void executeRunAsync_contextBuildFailureMarksRunSystemError() throws Exception {
        AgentRun run = newRun();
        when(contextBuilder.buildContext(run)).thenThrow(new RuntimeException("agent not found"));

        service.executeRunAsync(run);

        verify(agentEngine, never()).executeSync(any(), any());
        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunMapper, times(2)).updateById(captor.capture());
        AgentRun finalState = captor.getAllValues().get(1);
        assertEquals("failed", finalState.getStatus());
        assertEquals("system_error", finalState.getErrorType());
    }

    // ===== QF-5（idd-review 快修）：终态回写前查状态，已取消的 run 不被引擎结果覆盖 =====

    @Test
    void executeRunAsync_preservesCancelledStatus_whenRunCancelledDuringExecution() throws Exception {
        AgentRun run = newRun();
        when(contextBuilder.buildContext(run)).thenReturn(new BaseContext());
        when(agentEngine.executeSync(eq(run), any(BaseContext.class)))
                .thenReturn(AgentResult.success("late answer"));
        // DB 预置：引擎执行期间用户已取消
        AgentRun dbState = new AgentRun();
        dbState.setId("run-1");
        dbState.setStatus("cancelled");
        when(agentRunMapper.selectById("run-1")).thenReturn(dbState);

        service.executeRunAsync(run);

        // 只有开头置 running 那一次回写，completed 不得覆盖 cancelled
        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunMapper, times(1)).updateById(captor.capture());
        assertEquals("running", captor.getAllValues().get(0).getStatus());
        assertEquals("cancelled", agentRunMapper.selectById("run-1").getStatus());
    }

    @Test
    void executeRunAsync_stillRunning_normalCompletedPathUnchanged() throws Exception {
        AgentRun run = newRun();
        when(contextBuilder.buildContext(run)).thenReturn(new BaseContext());
        when(agentEngine.executeSync(eq(run), any(BaseContext.class)))
                .thenReturn(AgentResult.success("done"));
        AgentRun dbState = new AgentRun();
        dbState.setId("run-1");
        dbState.setStatus("running");
        when(agentRunMapper.selectById("run-1")).thenReturn(dbState);

        service.executeRunAsync(run);

        ArgumentCaptor<AgentRun> captor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunMapper, times(2)).updateById(captor.capture());
        assertEquals("completed", captor.getAllValues().get(1).getStatus());
    }

    // ===== I2（P0-3）：streamEvents 优先按 afterSeq 增量拉事件推 SSE，run 表轮询仅作终态兜底 =====

    private static Map<String, Object> event(long seq) {
        return Map.of("runId", "run-1", "eventType", "tool_call",
                "payload", Map.of("n", seq), "seq", seq);
    }

    @Test
    void streamEvents_replaysFromAfterSeqThenPullsIncrementally_untilTerminal() throws Exception {
        when(agentRunMapper.selectById("run-1")).thenAnswer(inv -> {
            AgentRun r = newRun();
            r.setStatus("running");
            return r;
        });
        // 首次全量拉取返回 seq 1、2；之后增量（afterSeq=2）为空
        when(eventPublisher.getEvents(eq("run-1"), eq(0L)))
                .thenReturn(List.of(event(1), event(2)));
        when(eventPublisher.getEvents(eq("run-1"), eq(2L))).thenReturn(List.of());

        service.streamEvents("run-1", null, null);

        // 首拉 offset=0（afterSeq 为空 → 0），其后按 lastSeq=2 增量
        verify(eventPublisher, timeout(5000)).getEvents("run-1", 0L);
        verify(eventPublisher, timeout(5000).atLeast(2)).getEvents("run-1", 2L);
        // 事件通道拉取频次应远高于 run 表轮询（兜底语义）
        verify(eventPublisher, timeout(5000).atLeast(5)).getEvents(eq("run-1"), anyLong());
    }

    @Test
    void streamEvents_parsesAfterSeqStringAsNumericOffset() throws Exception {
        when(agentRunMapper.selectById("run-1")).thenAnswer(inv -> {
            AgentRun r = newRun();
            r.setStatus("running");
            return r;
        });
        when(eventPublisher.getEvents(eq("run-1"), eq(7L))).thenReturn(List.of());

        service.streamEvents("run-1", "7", null);

        // 500ms 轮询在满载机器上会多次进入循环——断言「以数字偏移 7L 调用过」即可，不锁次数
        verify(eventPublisher, timeout(5000).atLeastOnce()).getEvents("run-1", 7L);
    }

    @Test
    void streamEvents_runTableTerminal_isFallbackTermination() throws Exception {
        // 事件通道为空，run 表第一次终态检查即 completed
        when(agentRunMapper.selectById("run-1")).thenAnswer(inv -> {
            AgentRun r = newRun();
            r.setStatus("completed");
            return r;
        });
        when(eventPublisher.getEvents(eq("run-1"), eq(0L))).thenReturn(List.of());

        service.streamEvents("run-1", null, null);

        // getRun 同步一次 + 异步循环 tick0 兜底一次（≥2）；不严格限定次数，防抖动
        verify(agentRunMapper, timeout(5000).atLeast(2)).selectById("run-1");
        verify(eventPublisher, timeout(5000)).getEvents("run-1", 0L);
    }

    @Test
    void executeRunAsync_terminalWrite_clearsEventStream() throws Exception {
        AgentRun run = newRun();
        when(contextBuilder.buildContext(run)).thenReturn(new BaseContext());
        when(agentEngine.executeSync(eq(run), any(BaseContext.class)))
                .thenReturn(AgentResult.success("done"));
        AgentRun dbState = new AgentRun();
        dbState.setId("run-1");
        dbState.setStatus("running");
        when(agentRunMapper.selectById("run-1")).thenReturn(dbState);

        service.executeRunAsync(run);

        verify(eventPublisher).clearStream("run-1");
    }
}
