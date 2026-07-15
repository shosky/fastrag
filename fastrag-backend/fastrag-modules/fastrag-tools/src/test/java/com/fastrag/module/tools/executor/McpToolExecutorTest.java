package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.mapper.McpCallLogMapper;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpToolExecutor 单元测试。
 * 测试 config 解析和错误处理逻辑。
 */
@ExtendWith(MockitoExtension.class)
class McpToolExecutorTest {

    @Mock
    private McpCallLogMapper callLogMapper;

    @Mock
    private McpServiceMapper serviceMapper;

    private McpToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new McpToolExecutor(callLogMapper, serviceMapper);
    }

    @Test
    void testGetType() {
        assertEquals("mcp", executor.getType());
    }

    @Test
    void testExecuteWithNullConfig() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("test-tool");
        tool.setType("mcp");
        tool.setConfig(null);

        ToolResult result = executor.execute(tool, Map.of(), new ToolContext());

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("no config"));
    }

    @Test
    void testExecuteWithEmptyConfig() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("test-tool");
        tool.setType("mcp");
        tool.setConfig(Map.of());

        ToolResult result = executor.execute(tool, Map.of(), new ToolContext());

        assertFalse(result.isSuccess());
        // Should fail because transport defaults to stdio but no command provided
        assertNotNull(result.getError());
    }

    @Test
    void testExecuteWithNoTransportConfig() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("test-tool");
        tool.setType("mcp");
        tool.setConfig(Map.of("mcpServiceId", "s1", "mcpServiceName", "test"));

        ToolResult result = executor.execute(tool, Map.of(), new ToolContext());

        assertFalse(result.isSuccess());
        // stdio transport defaults but no command -> error
        assertNotNull(result.getError());
    }

    @Test
    void testExecuteWithInvalidTransport() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("test-tool");
        tool.setType("mcp");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("transport", "invalid");
        tool.setConfig(config);

        ToolResult result = executor.execute(tool, Map.of(), new ToolContext());

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }
}
