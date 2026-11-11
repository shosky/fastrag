package com.fastrag.module.application.widget.dto;

import java.util.Map;

/**
 * 卡片渲染快照（五要素，IF-004 产出）：emit 时刻定格，随 tool_result.data 透传前端并随
 * toolCalls 落库（ADR-0010）。历史回看按快照渲染，不依赖资产当前内容。
 */
public record WidgetSnapshot(
        String widgetId,
        String widgetKey,
        String name,
        String template,
        Map<String, Object> data) {

    public Map<String, Object> toMap() {
        return Map.of("widgetId", widgetId, "widgetKey", widgetKey, "name", name,
                "template", template, "data", data == null ? Map.of() : data);
    }
}
