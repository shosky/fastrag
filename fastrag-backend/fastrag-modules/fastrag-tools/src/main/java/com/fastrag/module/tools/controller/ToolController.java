package com.fastrag.module.tools.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.tools.entity.ToolHttpConfig;
import com.fastrag.module.tools.service.ToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

@Slf4j
@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {
    private final ToolService svc;
    private final WebClient.Builder webClientBuilder;

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String type) {
        return ApiResponse.success(svc.list(keyword, type));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        return ApiResponse.success(svc.get(id));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> f) {
        return ApiResponse.success(svc.create(f));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Map<String, Object> f) {
        return ApiResponse.success(svc.update(id, f));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        svc.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<?> toggle(@PathVariable String id) {
        svc.toggleEnabled(id);
        return ApiResponse.success();
    }

    // ===== 工具测试代理 =====

    /** 前端测试工具通过此接口转发 HTTP 请求，避免 CORS */
    @PostMapping("/test-proxy")
    public ApiResponse<?> testProxy(@RequestBody Map<String, Object> req) {
        String method = (String) req.getOrDefault("method", "GET");
        String url = (String) req.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "url is required");
        }

        // 请求头
        Map<String, String> reqHeaders = new HashMap<>();
        if (req.get("headers") instanceof Map) {
            ((Map<String, Object>) req.get("headers")).forEach((k, v) -> reqHeaders.put(k, String.valueOf(v)));
        }

        int timeoutMs = 30000;
        if (req.get("timeout") instanceof Number) {
            timeoutMs = ((Number) req.get("timeout")).intValue();
        }

        String body = null;
        if (req.get("body") instanceof String) {
            body = (String) req.get("body");
        }

        log.info("[test-proxy] >>> {} {} headers={} body={}", method, url, reqHeaders,
                body != null ? body.substring(0, Math.min(200, body.length())) : null);

        long start = System.currentTimeMillis();

        try {
            WebClient.RequestBodySpec spec = webClientBuilder.build()
                    .method(HttpMethod.valueOf(method.toUpperCase()))
                    .uri(URI.create(url));
            reqHeaders.forEach(spec::header);

            // 使用 exchangeToMono 捕获完整响应（包括 4xx/5xx）
            WebClient.RequestHeadersSpec<?> headersSpec = spec;
            if (body != null && !body.isBlank()
                    && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) {
                headersSpec = spec.bodyValue(body);
            }
            Map<String, Object> resultMap = headersSpec
                    .exchangeToMono(response -> {
                        long dur = System.currentTimeMillis() - start;
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(responseBody -> {
                                    Map<String, String> respHeaders = new LinkedHashMap<>();
                                    response.headers().asHttpHeaders().forEach((k, v) -> {
                                        if (!v.isEmpty()) respHeaders.put(k, String.join(", ", v));
                                    });
                                    Map<String, Object> r = new LinkedHashMap<>();
                                    r.put("status", response.statusCode().value());
                                    r.put("statusText", response.statusCode().toString());
                                    r.put("headers", respHeaders);
                                    r.put("body", responseBody);
                                    r.put("duration", dur);
                                    return r;
                                });
                    })
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block(Duration.ofMillis(timeoutMs + 5000));

            if (resultMap == null) {
                log.warn("[test-proxy] No response (null)");
                return ApiResponse.success(Map.of("status", 0, "statusText", "No Response", "headers", Map.of(), "body", "", "duration", System.currentTimeMillis() - start));
            }

            int status = ((Number) resultMap.get("status")).intValue();
            String respBody = (String) resultMap.getOrDefault("body", "");
            if (status >= 400) {
                log.warn("[test-proxy] <<< {} {} → {} body={}", method, url, status,
                        respBody.substring(0, Math.min(500, respBody.length())));
            } else {
                log.info("[test-proxy] <<< {} {} → {} ({}ms) body={}", method, url, status, resultMap.get("duration"),
                        respBody.substring(0, Math.min(200, respBody.length())));
            }
            return ApiResponse.success(resultMap);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[test-proxy] FAILED {} {} ({}ms): ", method, url, duration, e);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", 0);
            result.put("statusText", "Error");
            result.put("headers", Map.of());
            result.put("body", "");
            result.put("duration", duration);

            // 提取详细错误信息
            String errorMsg = e.getMessage();
            if (e instanceof WebClientResponseException wcre) {
                errorMsg = "HTTP " + wcre.getStatusCode() + "\n" + wcre.getResponseBodyAsString();
            }
            result.put("error", errorMsg);
            return ApiResponse.success(result);
        }
    }

    // ===== M4 API插件配置 =====
    @GetMapping("/{id}/api-config")
    public ApiResponse<?> getApiConfig(@PathVariable String id) {
        return ApiResponse.success(svc.getApiConfig(id));
    }

    @PutMapping("/{id}/api-config")
    public ApiResponse<?> saveApiConfig(@PathVariable String id, @RequestBody ToolHttpConfig config) {
        return ApiResponse.success(svc.saveApiConfig(id, config));
    }

    // ===== 插件管理 - 上传插件 =====
    @PostMapping("/upload")
    public ApiResponse<?> uploadPlugin(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name, @RequestParam(required = false) String description) {
        return ApiResponse.success(svc.uploadPlugin(file, name, description));
    }

    // ===== 插件管理 - JSON导入 =====
    @PostMapping("/import-json")
    public ApiResponse<?> importJson(@RequestBody List<Map<String, Object>> plugins) {
        return ApiResponse.success(svc.importFromJson(plugins));
    }
}
