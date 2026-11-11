package com.fastrag.module.application.widget;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Widget 组件白名单（一期 19 个，六类）——保存校验（后端）与前端渲染注册表的双份常量之一。
 *
 * <p>不变量（REQ-WG-A06）：本集合必须与前端 {@code fastrag-frontend/src/views/conversation/
 * components/widget/registry.ts} 键集双向相等，由 WidgetWhitelistParityTest 守护。
 * requiredProps 为第二档校验的必填属性表（值为非空白字符串即算满足）。</p>
 */
public final class WidgetComponentWhitelist {

    private WidgetComponentWhitelist() {}

    /** 一期组件全集（19 个） */
    public static final Set<String> COMPONENTS = Set.of(
            // 容器
            "Card", "ListView", "ListViewItem",
            // 布局
            "Row", "Col", "Divider", "Form",
            // 文本
            "Title", "Text", "Caption", "Markdown",
            // 内容
            "Badge", "Icon", "Image",
            // 交互
            "Button",
            // 表单
            "Input", "Select", "RadioGroup", "Checkbox");

    /** 第二档校验：组件 → 必填属性名集（缺属性或空白即拒） */
    public static final Map<String, Set<String>> REQUIRED_PROPS = Map.ofEntries(
            Map.entry("Title", Set.of("value")),
            Map.entry("Text", Set.of("value")),
            Map.entry("Caption", Set.of("value")),
            Map.entry("Markdown", Set.of("value")),
            Map.entry("Badge", Set.of("value")),
            Map.entry("Icon", Set.of("name")),
            Map.entry("Image", Set.of("src")),
            Map.entry("Button", Set.of("label")),
            Map.entry("Select", Set.of("name", "options")),
            Map.entry("RadioGroup", Set.of("name", "options")),
            Map.entry("Checkbox", Set.of("name", "label")),
            Map.entry("Input", Set.of("name")),
            Map.entry("Card", Set.of("children")),
            Map.entry("ListView", Set.of("children")),
            Map.entry("ListViewItem", Set.of("children")),
            Map.entry("Row", Set.of("children")),
            Map.entry("Col", Set.of("children")),
            Map.entry("Form", Set.of("children")),
            Map.entry("Divider", Set.of()));

    /** 三件套单件 JSON 体积上界（实体 TEXT 列 64KB） */
    public static final int MAX_PIECE_BYTES = 64 * 1024;

    /** 渲染数据体积上界（工具路径护栏，REQ-WG-D05） */
    public static final int MAX_DATA_BYTES = 32 * 1024;

    /** 回传负载体积上界（REQ-WG-E03 / REQ-WG-D05） */
    public static final int MAX_ACTION_PAYLOAD_BYTES = 16 * 1024;

    /** 清单注入文本截断预算（与 IF-003 TOOL_OUTPUT 同口径） */
    public static final int MAX_CATALOG_CHARS = 5000;

    /** 单应用绑定数上限（REQ-WG-C03，对齐 skillNames 先例） */
    public static final int MAX_BINDINGS_PER_APP = 10;

    /** Schema 支持的变量类型（JSON Schema 子集，CONTEXT.md「数据结构」条目） */
    public static final List<String> SCHEMA_TYPES = List.of("string", "number", "boolean", "object", "array");
}
