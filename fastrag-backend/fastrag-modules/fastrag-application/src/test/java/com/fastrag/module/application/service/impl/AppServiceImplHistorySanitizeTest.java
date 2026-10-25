package com.fastrag.module.application.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 回归（2026-10-21 诊断）：部分模型（Qwen3-8B@SiliconFlow 实测）偶发把工具调用以内联
 * JSON 文本输出（未走正式 tool_calls 通道），引擎将其当正文落库，随 projectHistory 混入
 * 会话历史块——污染后续所有轮的模型上下文（生产样本：同一问题三连问，前两轮 assistant
 * 正文即 `"arguments": {"query": ...}}` 残片）。
 *
 * <p>判据必须保守：讲解 JSON 工具调用格式的正常回答不得误伤。</p>
 */
class AppServiceImplHistorySanitizeTest {

    @Test
    void productionIncidentFragment_detected() {
        // 生产历史块中的真实残片（缺 {"name":... 前缀的截断片段）
        assertTrue(AppServiceImpl.isRawToolCallFragment(
                " \"arguments\": {\"query\": \"今天长沙天气\"}}"));
    }

    @Test
    void completeInlineToolCall_detected() {
        assertTrue(AppServiceImpl.isRawToolCallFragment(
                "{\"name\": \"web_search\", \"arguments\": {\"query\": \"今天长沙天气\", \"limit\": 5}}"));
    }

    @Test
    void fragmentWithLooseSpacing_detected() {
        assertTrue(AppServiceImpl.isRawToolCallFragment("{ \"arguments\" : {\"query\": \"x\"}}"));
        assertTrue(AppServiceImpl.isRawToolCallFragment("{\"name\":\n\"query_kb\", \"arguments\": {}}"));
    }

    @Test
    void proseAnswer_notFlagged() {
        assertFalse(AppServiceImpl.isRawToolCallFragment(
                "今天长沙以降雨为主，气温 17.6℃ 到 19.9℃ [c1]。"));
        assertFalse(AppServiceImpl.isRawToolCallFragment(
                "工具调用的格式是 {\"name\": \"x\", \"arguments\": {}}，其中 name 为工具名。"));
        assertFalse(AppServiceImpl.isRawToolCallFragment("查询结果如下：\n{\"a\": 1}"));
    }

    @Test
    void arbitraryJsonAnswer_notFlagged() {
        // 只拦工具调用形态（name/arguments 键开头），任意 JSON 回答不受影响
        assertFalse(AppServiceImpl.isRawToolCallFragment("{\"query\": \"输出成图表\"}"));
        assertFalse(AppServiceImpl.isRawToolCallFragment("[{\"category\": \"A\", \"value\": 10}]"));
    }

    @Test
    void nullAndDegenerateInput_safe() {
        assertFalse(AppServiceImpl.isRawToolCallFragment(null));
        assertFalse(AppServiceImpl.isRawToolCallFragment(""));
        assertFalse(AppServiceImpl.isRawToolCallFragment("}"));
        assertFalse(AppServiceImpl.isRawToolCallFragment("{\"name\": \"x\"")); // 未闭合不拦
    }
}
