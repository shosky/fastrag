package com.fastrag.module.application.widget.dto;

import lombok.Data;

/**
 * Widget 保存请求体（API-003/004，interfaces.md WidgetSaveRequest）。
 */
@Data
public class WidgetSaveRequest {
    private String name;
    private String description;
    private String icon;
    private String template;
    private String schemaJson;
    private String defaultJson;
}
