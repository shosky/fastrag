package com.fastrag.module.application.widget.dto;

import lombok.Data;

import java.util.Map;

/**
 * Widget 回传负载（两通道请求体 widgetAction 字段，API-011）：
 * 仅 action='sys.submit' 进入推进链路；go_to_url/download 由前端本地处理。
 */
@Data
public class WidgetActionPayload {
    private String widgetId;
    private String widgetKey;
    private String action;
    private java.util.Map<String, Object> payload;

    /** JSON Map（请求体原始形态）→ 负载对象；非对象或字段缺失原样容忍（prepare 侧校验） */
    @SuppressWarnings("unchecked")
    public static WidgetActionPayload fromMap(java.util.Map<String, Object> raw) {
        WidgetActionPayload p = new WidgetActionPayload();
        if (raw == null) {
            return p;
        }
        p.setWidgetId((String) raw.get("widgetId"));
        p.setWidgetKey((String) raw.get("widgetKey"));
        Object action = raw.get("action");
        p.setAction(action == null ? null : String.valueOf(action));
        Object payload = raw.get("payload");
        if (payload instanceof Map<?, ?> m) {
            p.setPayload((Map<String, Object>) m);
        }
        return p;
    }
}
