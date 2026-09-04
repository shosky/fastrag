package com.fastrag.module.bpm.executor.impl;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** HttpNodeExecutor:java.net.http 封装 + 成功/失败/超时 */
@ExtendWith(MockitoExtension.class)
class HttpNodeExecutorTest {

    @Mock HttpClient httpClient;
    private final SpelEvaluator spel = new SpelEvaluator();

    private HttpNodeExecutor newExWithMock() {
        ObjectProviderStub provider = new ObjectProviderStub(httpClient);
        return new HttpNodeExecutor(provider, spel);
    }

    private HttpNodeExecutor newExWithoutBean() {
        ObjectProviderStub provider = new ObjectProviderStub(null);
        return new HttpNodeExecutor(provider, spel);
    }

    private BpmFlowNode node(String config) {
        BpmFlowNode n = new BpmFlowNode();
        n.setNodeType("http");
        n.setNodeKey("h1");
        n.setConfig(config);
        return n;
    }

    private ExecutionContext ctxEmpty(BpmFlowNode n) {
        ExecutionContext c = new ExecutionContext();
        c.setFlowDefId("f1");
        c.setCurrentNode(n);
        c.setVariables(new HashMap<>());
        return c;
    }

    private HttpResponse<String> mockResp(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<String> resp = org.mockito.Mockito.mock(HttpResponse.class);
        lenient().when(resp.statusCode()).thenReturn(status);
        lenient().when(resp.body()).thenReturn(body);
        lenient().when(resp.headers()).thenReturn(HttpHeaders.of(headers == null ? Map.of() : headers, (a, b) -> true));
        return resp;
    }

    @Test
    @DisplayName("200 + JSON:outputs.response 解析为 JsonNode")
    void successJson() throws Exception {
        doReturn(mockResp(200, "{\"ok\":true,\"v\":42}", Map.of()))
                .when(httpClient).send(any(HttpRequest.class), any());
        HttpNodeExecutor ex = newExWithMock();
        String cfg = "{\"method\":\"get\",\"url\":\"'http://x/y'\",\"responseMode\":\"json\"}";
        NodeExecutionResult r = ex.execute(ctxEmpty(node(cfg)));
        assertEquals(200, r.getOutputs().get("status"));
        assertNotNull(r.getOutputs().get("response"));
        assertEquals("http://x/y", r.getOutputs().get("url"));
    }

    @Test
    @DisplayName("responseMode=text 时 response 为原文")
    void responseText() throws Exception {
        doReturn(mockResp(200, "plain text", Map.of()))
                .when(httpClient).send(any(HttpRequest.class), any());
        HttpNodeExecutor ex = newExWithMock();
        String cfg = "{\"method\":\"get\",\"url\":\"'http://x'\",\"responseMode\":\"text\"}";
        NodeExecutionResult r = ex.execute(ctxEmpty(node(cfg)));
        assertEquals("plain text", r.getOutputs().get("response"));
    }

    @Test
    @DisplayName("responseMode=status_code 时 response 为状态码")
    void responseStatus() throws Exception {
        doReturn(mockResp(204, "", Map.of()))
                .when(httpClient).send(any(HttpRequest.class), any());
        HttpNodeExecutor ex = newExWithMock();
        String cfg = "{\"method\":\"get\",\"url\":\"'http://x'\",\"responseMode\":\"status_code\"}";
        NodeExecutionResult r = ex.execute(ctxEmpty(node(cfg)));
        assertEquals(204, r.getOutputs().get("response"));
    }

    @Test
    @DisplayName("500 抛 EXECUTION_FAILED(40041)")
    void serverError() throws Exception {
        doReturn(mockResp(500, "oops", Map.of()))
                .when(httpClient).send(any(HttpRequest.class), any());
        HttpNodeExecutor ex = newExWithMock();
        String cfg = "{\"method\":\"get\",\"url\":\"'http://x'\"}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctxEmpty(node(cfg))));
        assertEquals(40041, be.getCode());
        assertTrue(be.getMessage().contains("500"));
    }

    @Test
    @DisplayName("自定义 successStatusRange 接受 200 与 302")
    void customSuccessRange() throws Exception {
        doReturn(mockResp(302, "", Map.of()))
                .when(httpClient).send(any(HttpRequest.class), any());
        HttpNodeExecutor ex = newExWithMock();
        String cfg = "{\"method\":\"get\",\"url\":\"'http://x'\",\"successStatusRange\":[200,302]}";
        NodeExecutionResult r = ex.execute(ctxEmpty(node(cfg)));
        assertEquals(302, r.getOutputs().get("status"));
    }

    @Test
    @DisplayName("连接异常抛 EXECUTION_FAILED")
    void sendThrows() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new java.net.ConnectException("connection refused"));
        HttpNodeExecutor ex = newExWithMock();
        String cfg = "{\"method\":\"get\",\"url\":\"'http://x'\"}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctxEmpty(node(cfg))));
        assertEquals(40041, be.getCode());
    }

    @Test
    @DisplayName("validateConfig:method 不支持抛 40021")
    void validateBadMethod() {
        HttpNodeExecutor ex = newExWithoutBean();
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.validateConfig(Map.of("method", "trace", "url", "'x'")));
        assertEquals(40021, be.getCode());
    }

    @Test
    @DisplayName("validateConfig:url 缺失抛 40021")
    void validateMissingUrl() {
        HttpNodeExecutor ex = newExWithoutBean();
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.validateConfig(Map.of("method", "get")));
        assertEquals(40021, be.getCode());
    }

    /** 轻量 ObjectProvider stub,生产用 spring 的 ObjectProvider */
    static class ObjectProviderStub implements org.springframework.beans.factory.ObjectProvider<HttpClient> {
        private final HttpClient v;
        ObjectProviderStub(HttpClient v) { this.v = v; }
        @Override public HttpClient getIfAvailable() { return v; }
        @Override public HttpClient getIfAvailable(java.util.function.Supplier<HttpClient> defaultSupplier) {
            return v != null ? v : defaultSupplier.get();
        }
        // 不需要实现的方法全部抛 UnsupportedOperationException
        @Override public HttpClient getObject() { throw new UnsupportedOperationException(); }
        @Override public HttpClient getObject(Object... args) { throw new UnsupportedOperationException(); }
        @Override public HttpClient getIfUnique() { throw new UnsupportedOperationException(); }
        @Override public HttpClient getIfUnique(java.util.function.Supplier<HttpClient> defaultSupplier) { throw new UnsupportedOperationException(); }
        @Override public java.util.stream.Stream<HttpClient> stream() { throw new UnsupportedOperationException(); }
        @Override public java.util.stream.Stream<HttpClient> orderedStream() { throw new UnsupportedOperationException(); }
    }
}