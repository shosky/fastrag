package com.fastrag.module.tools.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP (Model Context Protocol) 客户端。
 * <p>
 * 使用 Java 内置 HttpClient 发送 JSON-RPC over HTTP (Streamable HTTP)，
 * 适用于 Firecrawl 等支持直接 POST 的 MCP 服务。
 * <p>
 * 生命周期: new → connect() → listTools() / callTool() → close()
 */
@Slf4j
public class McpProtocolClient implements AutoCloseable {

    private static final String MCP_PROTOCOL_VERSION = "2025-11-25";
    private static final ObjectMapper om = new ObjectMapper();

    private final String transport;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final String mcpUrl;
    private final Map<String, String> headers;
    private final int timeoutSeconds;

    private HttpClient httpClient;
    private boolean connected = false;
    private final AtomicInteger requestId = new AtomicInteger(1);
    /** MCP 会话 ID，从 initialize 响应的 mcp-session-id 头获取 */
    private String sessionId;

    // ======================== 构造函数 ========================

    public McpProtocolClient(String transport, String command, List<String> args,
                              Map<String, String> env, String mcpUrl,
                              Map<String, String> headers, int timeoutSeconds) {
        this.transport = transport != null ? transport : "default";
        this.command = command;
        this.args = args != null ? args : List.of();
        this.env = env != null ? env : Map.of();
        this.mcpUrl = mcpUrl;
        this.headers = headers != null ? headers : Map.of();
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
    }

    // ======================== 连接 ========================

    /**
     * 连接到 MCP 服务器，完成 initialize 握手。
     * 使用 Java HttpClient 直接 POST JSON-RPC，无需 SSE 握手。
     */
    public McpProtocolClient connect() {
        if (connected) return this;

        log.info("[McpClient] ====== MCP Connection Start =====");

        if ("stdio".equals(transport)) {
            throw new UnsupportedOperationException("stdio transport is not supported in this implementation");
        }

        if (mcpUrl == null || mcpUrl.isBlank()) {
            throw new IllegalArgumentException("MCP URL is required");
        }

        log.info("[McpClient] transport={}, url={}", transport, mcpUrl);
        log.info("[McpClient] headers count={}, keys={}", headers.size(),
                String.join(",", headers.keySet()));

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // 1. Send initialize request
        log.info("[McpClient] Sending initialize...");
        Map<String, Object> initParams = new LinkedHashMap<>();
        initParams.put("protocolVersion", MCP_PROTOCOL_VERSION);
        initParams.put("capabilities", Map.of());
        initParams.put("clientInfo", Map.of("name", "fastrag", "version", "1.0.0"));

        JsonRpcResponse initResponse = sendJsonRpcWithHeaders("initialize", initParams);
        log.info("[McpClient] Initialize response: server={}",
                initResponse.result().path("serverInfo").path("name").asText("unknown"));

        // 提取 mcp-session-id（Streamable HTTP 协议要求后续请求带上此头）
        List<String> sessionHeaders = initResponse.headers().get("mcp-session-id");
        if (sessionHeaders != null && !sessionHeaders.isEmpty()) {
            sessionId = sessionHeaders.get(0);
            log.info("[McpClient] Got sessionId: {}", sessionId);
        } else {
            // 某些 MCP 服务将 sessionId 放在响应体的 result 中
            String resultSessionId = initResponse.result().path("sessionId").asText("");
            if (!resultSessionId.isEmpty()) {
                sessionId = resultSessionId;
                log.info("[McpClient] Got sessionId from body: {}", sessionId);
            } else {
                log.info("[McpClient] No sessionId in response (stateless MCP server)");
            }
        }

        // 2. Send initialized notification (fire-and-forget)
        sendNotification("notifications/initialized", Map.of());

        this.connected = true;
        log.info("[McpClient] Connected successfully, isInitialized=true");
        log.info("[McpClient] ====== MCP Connection End =====");
        return this;
    }

    // ======================== 公共 API ========================

    /**
     * 调用 MCP tools/list 获取可用工具列表。
     */
    public List<McpToolInfo> listTools() {
        ensureConnected();
        log.info("[McpClient] Calling listTools()...");
        long t0 = System.currentTimeMillis();

        try {
            JsonNode result = sendJsonRpc("tools/list", Map.of());
            long elapsed = System.currentTimeMillis() - t0;

            List<McpToolInfo> tools = new ArrayList<>();
            JsonNode toolsNode = result.get("tools");
            if (toolsNode != null && toolsNode.isArray()) {
                for (JsonNode t : toolsNode) {
                    McpToolInfo info = new McpToolInfo();
                    info.setName(t.path("name").asText());
                    info.setDescription(t.path("description").asText(""));
                    if (t.has("inputSchema")) {
                        info.setInputSchema(om.convertValue(t.get("inputSchema"), Map.class));
                    }
                    tools.add(info);
                }
            }

            log.info("[McpClient] listTools() completed in {}ms, tools count={}", elapsed, tools.size());
            for (McpToolInfo t : tools) {
                log.debug("[McpClient]   tool: name={}, desc={}", t.getName(),
                        t.getDescription().length() > 50 ? t.getDescription().substring(0, 50) + "..." : t.getDescription());
            }
            return tools;
        } catch (Exception e) {
            log.error("[McpClient] listTools() FAILED after {}ms: {}", System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }

    /**
     * 调用 MCP tools/call 执行工具。
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        ensureConnected();
        log.info("[McpClient] Calling tool: {}", toolName);
        long t0 = System.currentTimeMillis();

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("name", toolName);
            if (arguments != null && !arguments.isEmpty()) {
                params.put("arguments", arguments);
            }

            JsonNode result = sendJsonRpc("tools/call", params);
            long elapsed = System.currentTimeMillis() - t0;

            StringBuilder sb = new StringBuilder();
            JsonNode content = result.get("content");
            if (content != null && content.isArray()) {
                for (JsonNode item : content) {
                    if ("text".equals(item.path("type").asText())) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(item.path("text").asText());
                    }
                }
            }

            String output = sb.length() > 0 ? sb.toString() : result.path("text").asText("[no text content]");
            log.info("[McpClient] callTool() completed in {}ms, outputLen={}", elapsed, output.length());
            return output;
        } catch (Exception e) {
            log.error("[McpClient] callTool() FAILED after {}ms: {}", System.currentTimeMillis() - t0, e.getMessage());
            throw e;
        }
    }

    // ======================== JSON-RPC 核心 ========================

    /**
     * 发送 JSON-RPC 请求并等待响应。
     */
    private JsonNode sendJsonRpc(String method, Map<String, Object> params) {
        return sendJsonRpcWithHeaders(method, params).result();
    }

    /** JSON-RPC 响应，包含 result 和 HTTP 响应头 */
    private record JsonRpcResponse(JsonNode result, Map<String, List<String>> headers) {}

    /**
     * 发送 JSON-RPC 请求并等待响应，同时返回 HTTP 响应头。
     */
    private JsonRpcResponse sendJsonRpcWithHeaders(String method, Map<String, Object> params) {
        try {
            int id = requestId.getAndIncrement();
            ObjectNode request = om.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.set("params", om.valueToTree(params));
            }

            String jsonStr = om.writeValueAsString(request);
            log.info("[McpClient] Send: method={}, id={}, body={}", method, id, jsonStr);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(mcpUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonStr, StandardCharsets.UTF_8));

            // 添加自定义请求头
            headers.forEach(reqBuilder::header);

            // 如果有 session ID，添加到请求头
            if (sessionId != null && !sessionId.isEmpty()) {
                reqBuilder.header("mcp-session-id", sessionId);
            }

            HttpResponse<String> response = httpClient.send(
                    reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String responseBody = response.body();
            log.debug("[McpClient] Response: status={}, body={}",
                    response.statusCode(),
                    responseBody != null ? responseBody.substring(0, Math.min(300, responseBody.length())) : "");

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + response.statusCode()
                        + " from POST " + mcpUrl
                        + (responseBody != null ? ": " + responseBody : ""));
            }

            if (responseBody == null || responseBody.isBlank()) {
                throw new RuntimeException("Empty response from server (HTTP " + response.statusCode() + ")");
            }

            // Firecrawl 返回 SSE 格式的响应体，需要先提取 data: 行中的 JSON
            String responseJson = extractJsonFromSse(responseBody);

            JsonNode json = om.readTree(responseJson);

            // 检查是否有 error 字段
            if (json.has("error") && !json.get("error").isNull()) {
                JsonNode err = json.get("error");
                String msg = err.path("message").asText("Unknown error");
                throw new RuntimeException("JSON-RPC error: " + msg);
            }

            // 返回 result 节点
            if (!json.has("result")) {
                throw new RuntimeException("JSON-RPC response missing 'result' field: " + responseBody);
            }

            return new JsonRpcResponse(json.get("result"), response.headers().map());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("MCP request failed: method=" + method, e);
        }
    }

    /**
     * 从 SSE 格式的响应体中提取 JSON 字符串。
     * Firecrawl 等服务返回 SSE 格式（event: / data: 行），而不是纯 JSON。
     */
    private String extractJsonFromSse(String responseBody) {
        String trimmed = responseBody.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        StringBuilder json = new StringBuilder();
        String[] lines = trimmed.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("data:")) {
                String dataValue = trimmedLine.substring(5).trim();
                if (dataValue.isEmpty() || dataValue.startsWith(":")) {
                    continue;
                }
                json.append(dataValue);
            }
        }

        String result = json.toString().trim();
        if (result.isEmpty()) {
            log.warn("[McpClient] Could not extract JSON from SSE response, trying raw: {}",
                    trimmed.substring(0, Math.min(100, trimmed.length())));
            return trimmed;
        }
        log.debug("[McpClient] Extracted JSON from SSE response: {}",
                result.substring(0, Math.min(200, result.length())));
        return result;
    }

    /**
     * 发送 JSON-RPC 通知（无需响应）。
     */
    private void sendNotification(String method, Map<String, Object> params) {
        try {
            ObjectNode request = om.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("method", method);
            if (params != null) {
                request.set("params", om.valueToTree(params));
            }

            String jsonStr = om.writeValueAsString(request);
            log.info("[McpClient] Notification: method={}, body={}", method, jsonStr);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(mcpUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonStr, StandardCharsets.UTF_8));

            headers.forEach(reqBuilder::header);

            // 通知也需要带 session ID
            if (sessionId != null && !sessionId.isEmpty()) {
                reqBuilder.header("mcp-session-id", sessionId);
            }

            // 同步发送，确保服务端处理完后再发后续请求
            httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            log.warn("[McpClient] Failed to send notification: method={}", method, e);
        }
    }

    // ======================== 工具方法 ========================

    private void ensureConnected() {
        if (!connected || httpClient == null) {
            throw new IllegalStateException("McpProtocolClient not connected. Call connect() first.");
        }
    }

    // ======================== 生命周期 ========================

    @Override
    public void close() {
        // HttpClient in Java 17 does not implement AutoCloseable.
        // Just release the reference for GC.
        httpClient = null;
        connected = false;
        log.info("[McpClient] Closed");
    }

    // ======================== 内部数据类 ========================

    @lombok.Data
    public static class McpToolInfo {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }
}
