package com.fastrag.module.application.widget;

import com.fastrag.module.application.widget.dto.WidgetSnapshot;
import com.fastrag.module.agent.middleware.WidgetMiddleware;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * S6 render_widget 执行链（tdd/widget.md S6；REQ-WG-D02~D05）：
 * 执行器真缝单测——allowed 集合取自 runtimeState 清单通道（List<Map>），深校验 mock IF-004。
 */
@ExtendWith(MockitoExtension.class)
class RenderWidgetToolExecutorTest {

    @Mock
    private WidgetAssetService widgetAssetService;

    @InjectMocks
    private RenderWidgetToolExecutor executor;

    private ToolContext ctxWithCatalog(String... ids) {
        ToolContext ctx = new ToolContext();
        // 通道载荷 = List<Map>（键集 id/name/...，与 WidgetMiddleware.WIDGET_CATALOG_KEY 契约一致）
        ctx.setExtension(WidgetMiddleware.WIDGET_CATALOG_KEY,
                java.util.Arrays.stream(ids).map(id -> Map.of("id", id, "name", "卡" + id)).toList());
        return ctx;
    }

    private Map<String, Object> args(String widgetId, String widgetKey, Map<String, Object> data) {
        return Map.of("widgetId", widgetId, "widgetKey", widgetKey, "data", data == null ? Map.of() : data);
    }

    @Test
    @DisplayName("[执行] 类型独立注册 [type=widget]")
    void typeIsWidget() {
        assertThat(executor.getType()).isEqualTo("widget");
    }

    @Test
    @DisplayName("[执行] 绑定集合内合法调用时 [快照五要素进 structured]")
    void successCarriesSnapshot() {
        when(widgetAssetService.resolveSnapshot(eq("w1"), eq("order-key"), any(), anySet()))
                .thenReturn(new WidgetSnapshot("w1", "order-key", "订单确认卡", "{}", Map.of("total", 42)));
        ToolResult result = executor.execute(null, args("w1", "order-key", Map.of("total", 42)), ctxWithCatalog("w1"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStructured()).containsEntry("widgetId", "w1")
                .containsEntry("widgetKey", "order-key")
                .containsEntry("name", "订单确认卡");
        assertThat(result.getOutput()).contains("订单确认卡");
    }

    @Test
    @DisplayName("[执行] 集合外 widgetId 时 [fail-closed 错误文本]")
    void rejectsOutsideBindingSet() {
        // 成员校验住在 IF-004（fail-closed）；执行器把业务异常原文转 Error 文本
        when(widgetAssetService.resolveSnapshot(eq("w_other"), eq("k"), any(), anySet()))
                .thenThrow(new com.fastrag.common.exception.BusinessException(404, "Error: 卡片未在本次对话绑定集合中，请勿调用未绑定的 widgetId"));
        ToolResult result = executor.execute(null, args("w_other", "k", Map.of()), ctxWithCatalog("w1"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("未在本次对话绑定集合");
    }

    @Test
    @DisplayName("[执行] 无清单上下文时 [拒绝]")
    void rejectsWithoutCatalog() {
        ToolResult result = executor.execute(null, args("w1", "k", Map.of()), new ToolContext());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("[执行] 缺 data 时 [错误文本]")
    void rejectsMissingData() {
        ToolResult result = executor.execute(null,
                Map.of("widgetId", "w1", "widgetKey", "k"), ctxWithCatalog("w1"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("data");
    }

    @Test
    @DisplayName("[执行] 业务校验失败时 [回灌自纠不抛断]")
    void convertsBusinessErrorToErrorText() {
        when(widgetAssetService.resolveSnapshot(any(), any(), any(), anySet()))
                .thenThrow(new com.fastrag.common.exception.BusinessException(400, "Error: 卡片数据校验失败：缺少必填变量 total"));
        ToolResult result = executor.execute(null, args("w1", "k", Map.of()), ctxWithCatalog("w1"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("total");
    }

    @Test
    @DisplayName("[执行] 意外异常时 [吞错转错误文本]")
    void convertsUnexpectedErrorToErrorText() {
        when(widgetAssetService.resolveSnapshot(any(), any(), any(), anySet()))
                .thenThrow(new RuntimeException("db down"));
        ToolResult result = executor.execute(null, args("w1", "k", Map.of()), ctxWithCatalog("w1"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("卡片生成失败");
    }
}
