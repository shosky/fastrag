package com.fastrag.module.knowledge.controller;

import com.fastrag.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略模板四分类现状断言（REQ-H12，tdd §5.2 S11）。
 *
 * excel_row 不新增模板：模板列表保持通用文档/PDF 专用/PPT 专用/音视频四项（REQ-B02 决议口径）。
 */
class ParseStrategyTemplateControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void 模板维持四分类且不含excel_row() {
        ApiResponse<?> resp = new ParseStrategyTemplateController().list();

        List<Map<String, Object>> templates = (List<Map<String, Object>>) resp.getData();
        assertEquals(4, templates.size(), "模板恰为四分类");
        List<Object> keys = templates.stream().map(t -> t.get("key")).toList();
        assertTrue(keys.containsAll(List.of("auto", "pdf", "pptx", "media")), "四分类 key 完整");
        templates.forEach(t -> {
            assertNotNull(t.get("name"));
            assertNotNull(t.get("extensions"));
            assertNotNull(t.get("parseMethod"));
            assertNotEquals("excel_row", t.get("key"), "不新增 excel_row 模板");
        });
    }
}
