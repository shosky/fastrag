package com.fastrag.module.bpm.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 节点:调用外部 HTTP 接口,用于系统集成、Webhook、第三方 API 调用等。
 *
 * config:
 *  - method:           必填 get/post/put/delete/patch(大小写不敏感)
 *  - url:              必填,SpEL 字符串
 *  - headers:          可选 Map<String,String>,value 支持 SpEL 渲染
 *  - body:             可选 String,仅在 post/put/patch 生效,SpEL 渲染
 *  - timeoutMs:        可选,默认 30000
 *  - responseMode:     可选 json(默认)|text|status_code
 *  - successStatusRange: 可选 List<Integer>,默认 [200, 299];2xx 算成功
 *
 * outputs: { status, response(mode 决定含义), body, headers, durationMs, url }
 *
 * 失败语义:超时 / 连接异常 / 状态码不在 successStatusRange 都抛 EXECUTION_FAILED。
 *
 * 依赖注入:HttpClient 通过 ObjectProvider 注入,生产无 HttpClient bean 时自动 new 一个
 * (connectTimeout=10s, followRedirects=NORMAL),便于测试时 mock。
 */
@Slf4j
@Component
public class HttpNodeExecutor implements NodeExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final SpelEvaluator spel;

    public HttpNodeExecutor(ObjectProvider<HttpClient> httpClientProvider, SpelEvaluator spel) {
        this.httpClient = httpClientProvider.getIfAvailable(() ->
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
        this.spel = spel;
    }

    @Override public String type() { return "http"; }
    @Override public String category() { return "execute"; }

    @Override
    @SuppressWarnings("unchecked")
    public void validateConfig(Map<String, Object> config) {
        if (config == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("http 节点 config 不能为空");
        if (StrUtil.isBlank((String) config.get("url")))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("http 节点 url 必填");
        String m = ((String) config.getOrDefault("method", "")).toLowerCase();
        if (!List.of("get", "post", "put", "delete", "patch").contains(m))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("http 节点 method 不支持: " + m);
        Object headers = config.get("headers");
        if (headers != null && !(headers instanceof Map))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("http 节点 headers 必须是 Map");
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(ExecutionContext ctx) {
        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        String method = ((String) cfg.getOrDefault("method", "get")).toLowerCase();
        long timeoutMs = cfg.get("timeoutMs") instanceof Number n ? n.longValue() : 30000L;
        String responseMode = ((String) cfg.getOrDefault("responseMode", "json")).toLowerCase();

        Map<String, Object> vars = new HashMap<>(ctx.getVariables() == null ? Map.of() : ctx.getVariables());
        if (ctx.getNodeInputs() != null) vars.putAll(ctx.getNodeInputs());

        String url = String.valueOf(spel.eval((String) cfg.get("url"), vars));

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs));

        // headers
        Map<String, String> headers = new LinkedHashMap<>();
        Object headersCfg = cfg.get("headers");
        if (headersCfg instanceof Map<?, ?> hm) {
            for (Map.Entry<?, ?> e : hm.entrySet()) {
                if (!(e.getKey() instanceof String k)) continue;
                Object v = e.getValue();
                String rendered = v == null ? "" : (v instanceof String s ? String.valueOf(spel.eval(s, vars)) : String.valueOf(v));
                headers.put(k, rendered);
            }
        }
        // 默认 Content-Type
        if (List.of("post", "put", "patch").contains(method) && !headers.containsKey("Content-Type") && !headers.containsKey("content-type")) {
            headers.put("Content-Type", "application/json");
        }
        headers.forEach(reqBuilder::header);

        // body
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (List.of("post", "put", "patch").contains(method)) {
            Object bodyExpr = cfg.get("body");
            String body = bodyExpr == null ? "" : String.valueOf(spel.eval(String.valueOf(bodyExpr), vars));
            publisher = HttpRequest.BodyPublishers.ofString(body);
        }
        reqBuilder.method(method.toUpperCase(), publisher);

        log.debug("HttpNode: method={}, url={}, timeoutMs={}", method, url, timeoutMs);

        long t0 = System.currentTimeMillis();
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw BpmErrorCode.EXECUTION_FAILED.of("http 调用失败: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
        }
        long dur = System.currentTimeMillis() - t0;

        int status = resp.statusCode();
        if (!isSuccess(status, cfg)) {
            String body = resp.body() == null ? "" : resp.body();
            throw BpmErrorCode.EXECUTION_FAILED.of("http 状态码 " + status + " 非成功: " + StrUtil.maxLength(body, 200));
        }

        NodeExecutionResult r = new NodeExecutionResult();
        r.getOutputs().put("status", status);
        r.getOutputs().put("url", url);
        r.getOutputs().put("durationMs", dur);

        Map<String, String> respHeaders = new LinkedHashMap<>();
        resp.headers().map().forEach((k, v) -> respHeaders.put(k, String.join(",", v)));
        r.getOutputs().put("headers", respHeaders);

        String body = resp.body() == null ? "" : resp.body();
        r.getOutputs().put("body", body);

        switch (responseMode) {
            case "text" -> r.getOutputs().put("response", body);
            case "status_code" -> r.getOutputs().put("response", status);
            default -> {
                Object parsed;
                try { parsed = MAPPER.readTree(body); }
                catch (Exception e) { parsed = body; }
                r.getOutputs().put("response", parsed);
            }
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private boolean isSuccess(int status, Map<String, Object> cfg) {
        Object range = cfg.get("successStatusRange");
        if (range instanceof List<?> list && !list.isEmpty()) {
            for (Object o : list) {
                if (o instanceof Number n && n.intValue() == status) return true;
            }
            return false;
        }
        return status >= 200 && status < 300;
    }
}