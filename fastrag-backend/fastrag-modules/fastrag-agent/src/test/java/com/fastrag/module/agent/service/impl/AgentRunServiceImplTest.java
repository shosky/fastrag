package com.fastrag.module.agent.service.impl;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ContextBuilder;
import com.fastrag.module.agent.entity.AgentRun;
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

import java.util.Map;

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

    private AgentRunServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentRunServiceImpl(agentRunMapper, agentService, agentEngine, contextBuilder);
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
}
