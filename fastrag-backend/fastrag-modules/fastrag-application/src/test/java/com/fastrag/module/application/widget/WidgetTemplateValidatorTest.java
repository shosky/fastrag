package com.fastrag.module.application.widget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1 三档保存校验（tdd/widget.md S1；REQ-WG-A02）：
 * 逐档取样 + 行级聚合 + 体积/名称边界。纯函数直测，无 mock。
 */
class WidgetTemplateValidatorTest {

    private final WidgetTemplateValidator validator = new WidgetTemplateValidator();

    private static final String GOOD_TEMPLATE = """
            {"type":"Card","props":{},"children":[
              {"type":"Title","props":{"value":"标题 {{title}}"}},
              {"type":"Button","props":{"label":"确认","action":{"type":"sys.submit"}}}
            ]}""";
    private static final String GOOD_SCHEMA = """
            {"type":"object","properties":{"title":{"type":"string"}},"required":["title"]}""";

    private List<WidgetTemplateValidator.Error> validate(String template, String schema, String def) {
        return validator.validate("订单确认卡", template, schema, def);
    }

    @Test
    @DisplayName("[校验] 模板坏 JSON 时 [第一档拒绝]")
    void rejectsBadTemplateJson() {
        var errors = validate("{\"type\":\"Card\"", GOOD_SCHEMA, null);
        assertThat(errors).anyMatch(e -> e.piece().equals("template") && e.message().contains("JSON"));
    }

    @Test
    @DisplayName("[校验] 白名单外组件时 [第二档拒绝]")
    void rejectsUnknownComponent() {
        var errors = validate("""
                {"type":"Card","props":{},"children":[{"type":"Chart","props":{}}]}""", GOOD_SCHEMA, null);
        assertThat(errors).anyMatch(e -> e.message().contains("未知组件") && e.message().contains("Chart"));
    }

    @Test
    @DisplayName("[校验] 必填属性缺失时 [第二档拒绝]")
    void rejectsMissingRequiredProp() {
        var errors = validate("""
                {"type":"Card","props":{},"children":[{"type":"Button","props":{}}]}""", GOOD_SCHEMA, null);
        assertThat(errors).anyMatch(e -> e.message().contains("Button") && e.message().contains("label"));
    }

    @Test
    @DisplayName("[校验] 变量未声明时 [第三档拒绝]")
    void rejectsUndeclaredVariable() {
        var errors = validate(GOOD_TEMPLATE,
                "{\"type\":\"object\",\"properties\":{}}", null);
        assertThat(errors).anyMatch(e -> e.message().contains("title"));
    }

    @Test
    @DisplayName("[校验] default 不合 schema 时 [第三档拒绝]")
    void rejectsBadDefaultType() {
        var errors = validate(GOOD_TEMPLATE, GOOD_SCHEMA, "{\"title\": 123}");
        assertThat(errors).anyMatch(e -> e.piece().equals("defaultJson") && e.message().contains("title"));
    }

    @Test
    @DisplayName("[校验] 三处同坏时 [行级聚合]")
    void aggregatesAllPieceErrors() {
        var errors = validator.validate("  ", "{bad", "{bad", "{bad");
        assertThat(errors).extracting(WidgetTemplateValidator.Error::piece)
                .contains("name", "template", "schemaJson", "defaultJson");
    }

    @Test
    @DisplayName("[校验] 单件超 64KB 时 [拒绝]")
    void rejectsOversizePiece() {
        String big = "{\"type\":\"Text\",\"props\":{\"value\":\"" + "字".repeat(40000) + "\"}}";
        var errors = validate(big, GOOD_SCHEMA, null);
        assertThat(errors).anyMatch(e -> e.piece().equals("template") && e.message().contains("64KB"));
    }

    @Test
    @DisplayName("[校验] name 空白时 [拒绝]")
    void rejectsBlankName() {
        var errors = validator.validate(" ", GOOD_TEMPLATE, GOOD_SCHEMA, null);
        assertThat(errors).anyMatch(e -> e.piece().equals("name"));
    }

    @Test
    @DisplayName("[校验] 最小合法骨架时 [通过]")
    void acceptsMinimalSkeleton() {
        assertThat(validate(GOOD_TEMPLATE, GOOD_SCHEMA, "{\"title\": \"你好\"}")).isEmpty();
    }

    // ===== 运行时数据校验（S6 路径共用） =====

    @Test
    @DisplayName("[数据] 缺必填变量时 [报错并指名]")
    void dataValidationReportsMissingRequired() {
        var errors = validator.validateData(GOOD_SCHEMA, Map.of());
        assertThat(errors).anyMatch(e -> e.message().contains("title"));
    }

    @Test
    @DisplayName("[数据] 类型不符时 [报错并指名]")
    void dataValidationReportsTypeMismatch() {
        var errors = validator.validateData(GOOD_SCHEMA, Map.of("title", 123));
        assertThat(errors).anyMatch(e -> e.message().contains("title") && e.message().contains("string"));
    }

    @Test
    @DisplayName("[数据] 未声明字段时 [报错]")
    void dataValidationReportsUndeclaredField() {
        var errors = validator.validateData(GOOD_SCHEMA, Map.of("title", "t", "extra", "x"));
        assertThat(errors).anyMatch(e -> e.message().contains("extra"));
    }
}
