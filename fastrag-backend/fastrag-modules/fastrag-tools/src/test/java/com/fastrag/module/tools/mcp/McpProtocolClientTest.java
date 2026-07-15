package com.fastrag.module.tools.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpProtocolClient 单元测试。
 * 测试 McpToolInfo 数据类和客户端构造逻辑。
 * JSON-RPC/SSE/HTTP 协议由 MCP SDK 内部处理，此处不测试。
 */
class McpProtocolClientTest {

    @Test
    void testMcpToolInfo() {
        McpProtocolClient.McpToolInfo info = new McpProtocolClient.McpToolInfo();
        info.setName("test_tool");
        info.setDescription("A test tool");
        info.setInputSchema(Map.of("type", "object"));

        assertEquals("test_tool", info.getName());
        assertEquals("A test tool", info.getDescription());
        assertEquals(Map.of("type", "object"), info.getInputSchema());
    }

    @Test
    void testConstructorDefaults() {
        McpProtocolClient client = new McpProtocolClient(
                "sse", null, null, null,
                "https://example.com/mcp", null, 0);

        assertNotNull(client);
    }

    @Test
    void testConstructorWithStdio() {
        McpProtocolClient client = new McpProtocolClient(
                "stdio", "npx", java.util.List.of("-y", "some-package"),
                Map.of(), null, null, 30);

        assertNotNull(client);
    }

    @Test
    void testConnectWithoutUrl_ShouldThrow() {
        McpProtocolClient client = new McpProtocolClient(
                "sse", null, null, null,
                null, null, 30);

        assertThrows(IllegalArgumentException.class, client::connect);
    }

    @Test
    void testListToolsWithoutConnect_ShouldThrow() {
        McpProtocolClient client = new McpProtocolClient(
                "sse", null, null, null,
                "https://example.com/mcp", null, 30);

        assertThrows(IllegalStateException.class, client::listTools);
    }

    @Test
    void testCallToolWithoutConnect_ShouldThrow() {
        McpProtocolClient client = new McpProtocolClient(
                "sse", null, null, null,
                "https://example.com/mcp", null, 30);

        assertThrows(IllegalStateException.class, () -> client.callTool("test", Map.of()));
    }
}
