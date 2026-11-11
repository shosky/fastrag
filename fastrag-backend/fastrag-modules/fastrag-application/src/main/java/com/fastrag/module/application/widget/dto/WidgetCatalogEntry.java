package com.fastrag.module.application.widget.dto;

import java.util.Map;

/**
 * 注入清单条目（IF-004.buildCatalog 产出，WidgetMiddleware 渲染 system 块的消费物）。
 */
public record WidgetCatalogEntry(
        String id,
        String name,
        String description,
        String schemaJson,
        String defaultJson) {

    /** 清单通道载荷形态（List<Map>，键集与 WidgetMiddleware.WIDGET_CATALOG_KEY 契约一致） */
    public Map<String, Object> toMap() {
        return Map.of("id", id, "name", name,
                "description", description == null ? "" : description,
                "schemaJson", schemaJson == null ? "" : schemaJson,
                "defaultJson", defaultJson == null ? "" : defaultJson);
    }
}
