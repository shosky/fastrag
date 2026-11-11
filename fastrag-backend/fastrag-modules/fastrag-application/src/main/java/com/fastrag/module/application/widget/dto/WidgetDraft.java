package com.fastrag.module.application.widget.dto;

/**
 * 三件套草稿（IF-004.validateDraft 入参；HTTP 保存体 WidgetSaveRequest 的服务侧投影）。
 */
public record WidgetDraft(String templateJson, String schemaJson, String defaultJson) {}
