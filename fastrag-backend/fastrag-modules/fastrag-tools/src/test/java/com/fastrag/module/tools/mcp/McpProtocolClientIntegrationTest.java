package com.fastrag.module.tools.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 协议客户端集成测试。
 * 启动本地 HTTP Server 模拟 MCP 服务，测试完整的协议交互。
 */
class McpProtocolClientIntegrationTest {

    private HttpServer server;
    private int port;

    // 记录收到的请求
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        requestCount.set(0);
        receivedAuth.set(null);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testMockServerWorks_WithRawHttpClient() throws Exception {
        server.createContext("/mcp", exchange -> {
            requestCount.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));

            String resp = "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer test-token")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("jsonrpc"));
        assertEquals("Bearer test-token", receivedAuth.get());
        assertEquals(1, requestCount.get());
    }

    @Test
    void testListTools_WithMcpProtocolClient() throws Exception {
        // Mock server: handles initialize + tools/list in sequence
        server.createContext("/mcp", exchange -> {
            requestCount.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            receivedAuth.set(auth);
            System.out.println("[MOCK] Request #" + requestCount.get() + " auth=" + auth);
            System.out.println("[MOCK] Body: " + body);

            String rawJson;
            if (body.contains("\"method\":\"initialize\"")) {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
            } else if (body.contains("\"method\":\"tools/list\"")) {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"bing_search\",\"description\":\"Search using Bing\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}}]}}";
            } else {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
            }

            // 以 SSE 格式返回，模拟 Firecrawl 的行为
            String resp = "id: sess_123\n"
                    + "data: \n"
                    + "\n"
                    + "event: message\n"
                    + "id: msg_001\n"
                    + "data: " + rawJson + "\n";

            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        try (McpProtocolClient client = new McpProtocolClient(
                "default", null, null, null,
                "http://localhost:" + port + "/mcp",
                Map.of("Authorization", "Bearer test-token-123"),
                15)) {

            List<McpProtocolClient.McpToolInfo> tools = client.connect().listTools();

            assertNotNull(tools);
            assertEquals(1, tools.size());
            assertEquals("bing_search", tools.get(0).getName());
            assertTrue(requestCount.get() >= 2, "Should have received at least 2 requests (initialize + tools/list)");
        }
    }

    @Test
    void testCallTool_WithMcpProtocolClient() throws Exception {
        server.createContext("/mcp", exchange -> {
            requestCount.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            String rawJson;
            if (body.contains("\"method\":\"initialize\"")) {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
            } else if (body.contains("\"method\":\"tools/list\"")) {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[{\"name\":\"greet\",\"description\":\"Greet someone\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}}]}}";
            } else if (body.contains("\"method\":\"tools/call\"")) {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Hello, World!\"}]}}";
            } else {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
            }

            String resp = "id: sess_456\n"
                    + "data: \n"
                    + "\n"
                    + "event: message\n"
                    + "id: msg_002\n"
                    + "data: " + rawJson + "\n";

            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        try (McpProtocolClient client = new McpProtocolClient(
                "default", null, null, null,
                "http://localhost:" + port + "/mcp",
                null, 15)) {

            client.connect().listTools();
            String result = client.callTool("greet", Map.of("name", "World"));
            assertEquals("Hello, World!", result);
        }
    }

    @Test
    void testHeadersAreForwarded() throws Exception {
        server.createContext("/mcp", exchange -> {
            requestCount.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));

            String rawJson;
            if (body.contains("\"method\":\"initialize\"")) {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
            } else {
                rawJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
            }

            String resp = "id: sess_789\n"
                    + "data: \n"
                    + "\n"
                    + "event: message\n"
                    + "id: msg_003\n"
                    + "data: " + rawJson + "\n";

            byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        // Use new client with custom headers - verify they reach the server
        try (McpProtocolClient client = new McpProtocolClient(
                "default", null, null, null,
                "http://localhost:" + port + "/mcp",
                Map.of("Authorization", "Bearer test-token-789"),
                5)) {

            try {
                client.connect();
            } catch (Exception ignored) {
                // Expected: tools/list might fail, but we just check headers were sent
            }
            assertEquals("Bearer test-token-789", receivedAuth.get());
        }
    }

    @Test
    void testWithoutInitialization_ShouldThrow() {
        McpProtocolClient client = new McpProtocolClient(
                "default", null, null, null,
                "http://localhost:1/nonexistent",
                null, 3);
        assertThrows(Exception.class, client::listTools);
    }
}
