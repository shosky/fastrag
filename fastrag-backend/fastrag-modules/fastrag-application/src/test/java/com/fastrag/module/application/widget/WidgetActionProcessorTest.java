package com.fastrag.module.application.widget;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.widget.dto.WidgetActionPayload;
import com.fastrag.module.application.widget.dto.WidgetTurnPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S7 回传推进（tdd/widget.md S7；REQ-WG-E01~E03）：纯函数直测。
 */
class WidgetActionProcessorTest {

    private final WidgetActionProcessorImpl processor = new WidgetActionProcessorImpl();

    private WidgetActionPayload payload(String action, Map<String, Object> form) {
        WidgetActionPayload p = new WidgetActionPayload();
        p.setWidgetId("w1");
        p.setWidgetKey("order-confirm");
        p.setAction(action);
        p.setPayload(form);
        return p;
    }

    @Test
    @DisplayName("[回传] 合法 submit 时 [合成可见消息与标注块]")
    void preparesTurnPlan() {
        WidgetTurnPlan plan = processor.prepare(payload("sys.submit", Map.of("dine_in", true, "note", "少辣")));
        assertThat(plan.visibleMessageText()).contains("order-confirm").contains("dine_in").contains("少辣");
        assertThat(plan.contextBlock()).startsWith("[WIDGET_ACTION]")
                .contains("widgetId=w1").contains("sys.submit").contains("\"note\"");
    }

    @Test
    @DisplayName("[回传] 非 sys.submit 时 [服务端防御拒绝]")
    void rejectsNonSubmitAction() {
        assertThatThrownBy(() -> processor.prepare(payload("sys.go_to_url", Map.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("sys.submit");
    }

    @Test
    @DisplayName("[回传] 缺标识时 [拒绝]")
    void rejectsMissingIdentifiers() {
        WidgetActionPayload p = payload("sys.submit", Map.of());
        p.setWidgetId(" ");
        assertThatThrownBy(() -> processor.prepare(p)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[回传] payload 超 16KB 时 [拒绝不推进]")
    void rejectsOversizePayload() {
        assertThatThrownBy(() -> processor.prepare(payload("sys.submit", Map.of("blob", "字".repeat(12000)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("16KB");
    }

    @Test
    @DisplayName("[回传] 空 payload 时 [容忍并合成]")
    void toleratesEmptyPayload() {
        WidgetTurnPlan plan = processor.prepare(payload("sys.submit", Map.of()));
        assertThat(plan.visibleMessageText()).contains("无表单数据");
    }
}
