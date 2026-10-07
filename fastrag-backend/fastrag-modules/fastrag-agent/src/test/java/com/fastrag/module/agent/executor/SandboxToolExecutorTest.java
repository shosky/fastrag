package com.fastrag.module.agent.executor;

import com.fastrag.module.agent.middleware.SkillsMiddleware;
import com.fastrag.module.agent.sandbox.SandboxExecutionService;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SandboxToolExecutor 用例（F4 技能运行期凭据通道：execute 动作把 runtimeState.skillEnv
 * 经 env 重载传给执行门面——skill-feature-batch.md F4，2026-10-07）。
 */
@ExtendWith(MockitoExtension.class)
class SandboxToolExecutorTest {

    @Mock
    private SandboxExecutionService facade;

    private SandboxToolExecutor executor;

    private ToolDefinition executeTool() {
        return ToolDefinition.builder()
                .toolId("t1")
                .name("execute")
                .type("sandbox")
                .config(Map.of("action", "execute"))
                .build();
    }

    @Test
    void execute_ctx携带skillEnv扩展时_经env重载传给门面() {
        executor = new SandboxToolExecutor(facade);
        when(facade.execute(anyString(), anyString(), any(), anyString(), any(), any(), anyMap()))
                .thenReturn("done");
        ToolContext ctx = new ToolContext();
        ctx.setUserId("u1");
        ctx.setExtension("fileThreadId", "s1");
        ctx.setExtension(SkillsMiddleware.SKILL_ENV_KEY, Map.of("APP_TOKEN", "v1"));

        ToolResult result = executor.execute(executeTool(),
                Map.of("command", "python run.py"), ctx);

        assertTrue(result.isSuccess());
        verify(facade).execute(eq("u1"), eq("s1"), isNull(), eq("python run.py"),
                isNull(), isNull(), eq(Map.of("APP_TOKEN", "v1")));
    }

    @Test
    void execute_无skillEnv扩展时_传空env_兼容无技能链路() {
        executor = new SandboxToolExecutor(facade);
        when(facade.execute(anyString(), anyString(), any(), anyString(), any(), any(), anyMap()))
                .thenReturn("done");
        ToolContext ctx = new ToolContext();
        ctx.setUserId("u1");

        executor.execute(executeTool(), Map.of("command", "ls"), ctx);

        verify(facade).execute(eq("u1"), eq("u1"), isNull(), eq("ls"),
                isNull(), isNull(), eq(Map.of()));
    }

    @Test
    void execute_skillEnv非Map形态时_安全忽略按空env() {
        executor = new SandboxToolExecutor(facade);
        when(facade.execute(anyString(), anyString(), any(), anyString(), any(), any(), anyMap()))
                .thenReturn("done");
        ToolContext ctx = new ToolContext();
        ctx.setUserId("u1");
        ctx.setExtension(SkillsMiddleware.SKILL_ENV_KEY, "not-a-map");

        ToolResult result = executor.execute(executeTool(), Map.of("command", "ls"), ctx);

        assertTrue(result.isSuccess());
        verify(facade).execute(eq("u1"), eq("u1"), isNull(), eq("ls"),
                isNull(), isNull(), eq(Map.of()));
    }

    @Test
    void execute_超时与cwd参数_随env重载一并透传() {
        executor = new SandboxToolExecutor(facade);
        when(facade.execute(anyString(), anyString(), any(), anyString(), any(), any(), anyMap()))
                .thenReturn("done");
        ToolContext ctx = new ToolContext();
        ctx.setUserId("u1");

        executor.execute(executeTool(),
                new HashMap<>(Map.of("command", "x", "timeout", 30, "cwd", "/workspace/sub")), ctx);

        verify(facade).execute(eq("u1"), eq("u1"), isNull(), eq("x"),
                eq(30), eq("/workspace/sub"), eq(Map.of()));
    }

    @Test
    void execute_无action配置时_报错不触门面() {
        executor = new SandboxToolExecutor(facade);
        ToolContext ctx = new ToolContext();
        ctx.setUserId("u1");

        ToolResult result = executor.execute(
                ToolDefinition.builder().toolId("t2").type("sandbox").build(),
                Map.of("command", "ls"), ctx);

        assertFalse(result.isSuccess());
        verifyNoInteractions(facade);
    }

    @Test
    void read_file动作_不携带env重载_行为不变() {
        executor = new SandboxToolExecutor(facade);
        when(facade.readFile(anyString(), anyString(), any(), anyString(), any(), any()))
                .thenReturn("content");
        ToolContext ctx = new ToolContext();
        ctx.setUserId("u1");
        ctx.setExtension(SkillsMiddleware.SKILL_ENV_KEY, Map.of("APP_TOKEN", "v1"));

        ToolResult result = executor.execute(
                ToolDefinition.builder().toolId("t3").type("sandbox")
                        .config(Map.of("action", "read_file")).build(),
                Map.of("file_path", "/workspace/a.txt"), ctx);

        assertTrue(result.isSuccess());
        verify(facade).readFile("u1", "u1", null, "/workspace/a.txt", null, null);
        verify(facade, never()).execute(anyString(), anyString(), any(), anyString(),
                any(), any(), anyMap());
    }
}
