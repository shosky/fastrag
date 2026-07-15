package com.fastrag.module.tools.executor;

import com.fastrag.common.util.TemplateEngine;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class HttpToolExecutor implements ToolExecutor {
    @Override
    public String getType() { return "http"; }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx) {
        long t0 = System.currentTimeMillis();
        try {
            Map<String, Object> config = tool.getConfig();
            if (config == null) {
                return fail("HTTP tool has no config");
            }

            // 1. Build variable map
            Map<String, Object> vars = new LinkedHashMap<>();
            Map<String, Object> args = arguments != null ? arguments : Map.of();
            vars.put("inputs", args);
            // 同时将参数直接放到根级别，支持 ${city} 和 ${inputs.city} 两种写法
            args.forEach(vars::put);
            vars.put("config", config);
            Map<String, Object> ctxMap = new LinkedHashMap<>();
            ctxMap.put("userId", ctx.getUserId());
            ctxMap.put("appId", ctx.getAppId());
            ctxMap.put("runId", ctx.getRunId());
            ctxMap.put("query", ctx.getQuery());
            vars.put("context", ctxMap);

            // 2. Template substitution
            String url = TemplateEngine.render(getString(config, "url"), vars);
            String method = getString(config, "method");
            if (method == null) method = "GET";
            String bodyType = getString(config, "bodyType");
            String body = TemplateEngine.render(getString(config, "body"), vars);

            // 3. Build headers
            HttpHeaders headers = new HttpHeaders();
            Map<String, String> rawHeaders = getMap(config, "headers");
            Map<String, String> renderedHeaders = TemplateEngine.renderMap(rawHeaders, vars);
            renderedHeaders.forEach(headers::add);

            // 4. Build query params
            Map<String, String> rawParams = getMap(config, "params");
            Map<String, String> renderedParams = TemplateEngine.renderMap(rawParams, vars);

            // 5. Build full URL with query params
            String fullUrl = url;
            if (!renderedParams.isEmpty()) {
                String query = renderedParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .reduce((a, b) -> a + "&" + b).orElse("");
                fullUrl = url + (url.contains("?") ? "&" : "?") + query;
            }

            // 6. Execute HTTP request via WebClient
            log.info("[HttpTool] Executing: {} {}", method, fullUrl);

            WebClient client = WebClient.builder()
                .exchangeStrategies(org.springframework.web.reactive.function.client.ExchangeStrategies.builder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                    .build())
                .build();

            WebClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(method))
                .uri(fullUrl)
                .headers(h -> headers.forEach(h::addAll));

            Mono<String> responseMono;
            if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) && body != null && !body.isEmpty()) {
                String contentType = "application/json";
                if ("form-data".equals(bodyType)) contentType = "multipart/form-data";
                else if ("x-www-form-urlencoded".equals(bodyType)) contentType = "application/x-www-form-urlencoded";
                else if ("xml".equals(bodyType)) contentType = "application/xml";
                spec = spec.header(HttpHeaders.CONTENT_TYPE, contentType);
                responseMono = spec.bodyValue(body).retrieve().bodyToMono(String.class);
            } else {
                responseMono = spec.retrieve().bodyToMono(String.class);
            }

            String responseBody = responseMono.block(Duration.ofSeconds(60));
            int duration = (int)(System.currentTimeMillis() - t0);

            log.info("[HttpTool] Response: status=200, duration={}ms, length={}", duration, responseBody != null ? responseBody.length() : 0);

            ToolResult result = new ToolResult();
            result.setSuccess(true);
            result.setOutput(responseBody != null ? responseBody : "");
            result.setDurationMs(duration);

            // Apply output mapping if defined
            if (tool.getOutputMapping() != null && !tool.getOutputMapping().isEmpty() && responseBody != null) {
                result.setOutput(extractByPath(responseBody, tool.getOutputMapping()));
            }

            return result;
        } catch (Exception e) {
            log.error("[HttpTool] Execution failed: {}", e.getMessage(), e);
            ToolResult result = new ToolResult();
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setDurationMs((int)(System.currentTimeMillis() - t0));
            return result;
        }
    }

    private String extractByPath(String json, String path) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(json);
            // Simple $.xxx.yyy path support
            String cleanPath = path.replace("$.", "").replace("$.", "");
            String[] parts = cleanPath.split("\\.");
            com.fasterxml.jackson.databind.JsonNode node = root;
            for (String part : parts) {
                if (node == null) return json;
                node = node.path(part);
            }
            return node.isValueNode() ? node.asText() : node.toString();
        } catch (Exception e) {
            return json;  // fallback to raw response
        }
    }

    private ToolResult fail(String error) {
        ToolResult r = new ToolResult();
        r.setSuccess(false);
        r.setError(error);
        return r;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map) return (Map<String, String>) v;
        return Map.of();
    }
}
