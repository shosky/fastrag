package com.fastrag.module.agent.middleware;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.entity.McpTool;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import com.fastrag.module.tools.mapper.McpToolMapper;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MCP 工具级勾选回归（2026-10-20）：runtimeState.mcpSelectedTools = 服务 id → 工具白名单；
 * 白名单按 name/tool_id 任一命中即注册；缺服务键 = 全量注册（存量 NULL 语义）。
 */
@ExtendWith(MockitoExtension.class)
class McpMiddlewareTest {

    @Mock
    private McpToolMapper mcpToolMapper;
    @Mock
    private McpServiceMapper mcpServiceMapper;

    private McpMiddleware middleware;

    @BeforeEach
    void setUp() {
        middleware = new McpMiddleware(mcpToolMapper, mcpServiceMapper);
    }

    private McpTool tool(String name, String toolId) {
        McpTool t = new McpTool();
        t.setServiceId("svc-1");
        t.setName(name);
        t.setToolId(toolId);
        t.setEnabled(1);
        return t;
    }

    private McpService service() {
        McpService s = new McpService();
        s.setId("svc-1");
        s.setName("SvcA");
        s.setTransport("sse");
        return s;
    }

    /** 执行中间件并返回注册成功的工具名（按模型可见名） */
    private List<String> run(Map<String, List<String>> selection, List<McpTool> tools) {
        when(mcpToolMapper.selectList(any())).thenReturn(tools);
        when(mcpServiceMapper.selectById("svc-1")).thenReturn(service());
        BaseContext context = new BaseContext();
        context.setMcps(new ArrayList<>(List.of("svc-1")));
        if (selection != null) {
            context.getRuntimeState().put("mcpSelectedTools", selection);
        }
        List<ToolDefinition> registered = new ArrayList<>();
        middleware.beforeModelCall(context,
                new ArrayList<>(List.of(new ChatMessage("user", "q"))), registered);
        return registered.stream().map(ToolDefinition::getName).toList();
    }

    @Test
    void selectionFiltersTools_byName() {
        List<String> names = run(Map.of("svc-1", List.of("search")),
                List.of(tool("search", null), tool("exec", "svc1_exec")));
        assertEquals(List.of("search"), names);
    }

    @Test
    void selectionMatchesByToolIdToo() {
        List<String> names = run(Map.of("svc-1", List.of("svc1_exec")),
                List.of(tool("search", null), tool("exec", "svc1_exec")));
        assertEquals(List.of("svc1_exec"), names);
    }

    @Test
    void missingServiceKey_registersAll() {
        // 勾选映射存在但缺本服务键 → 本服务全量注册（存量 NULL 语义）
        List<String> names = run(new HashMap<>(),
                List.of(tool("search", null), tool("exec", "svc1_exec")));
        assertEquals(List.of("search", "svc1_exec"), names);
    }

    @Test
    void noSelection_registersAll() {
        List<String> names = run(null,
                List.of(tool("search", null), tool("exec", "svc1_exec")));
        assertEquals(List.of("search", "svc1_exec"), names);
    }
}
