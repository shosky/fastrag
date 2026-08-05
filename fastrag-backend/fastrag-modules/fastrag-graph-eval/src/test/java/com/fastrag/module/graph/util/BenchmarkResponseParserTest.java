package com.fastrag.module.graph.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基准问答对响应解析测试：标准 JSON / markdown 包裹 / 夹杂说明文本 /
 * 错误串与空响应（回归：曾因 LLM 超时返回错误串导致 "A JSONArray text must start with '['"）。
 */
class BenchmarkResponseParserTest {

    private static final String QA_JSON =
            "[{\"question\":\"小微ICT定义是什么？\",\"goldAnswer\":\"项目金额≤50万…\",\"goldChunks\":[\"c1_chunk_0\"]},"
            + "{\"question\":\"黄金四问包含哪四问？\",\"goldAnswer\":\"问感知、规模、用途、意向\",\"goldChunks\":[\"c1_chunk_1\"]}]";

    @Test
    void parseQuestions_plainJson_ok() {
        List<Map<String, Object>> list = BenchmarkResponseParser.parseQuestions(QA_JSON);
        assertEquals(2, list.size());
        assertEquals("小微ICT定义是什么？", list.get(0).get("question"));
        assertEquals(List.of("c1_chunk_0"), list.get(0).get("goldChunks"));
    }

    @Test
    void parseQuestions_markdownFencedJson_ok() {
        String wrapped = "```json\n" + QA_JSON + "\n```";
        assertEquals(2, BenchmarkResponseParser.parseQuestions(wrapped).size());
    }

    @Test
    void parseQuestions_proseWrappedJson_ok() {
        String wrapped = "好的，以下是生成的问答对：\n" + QA_JSON + "\n以上共 2 个。";
        assertEquals(2, BenchmarkResponseParser.parseQuestions(wrapped).size());
    }

    @Test
    void parseQuestions_errorMessage_returnsEmpty() {
        // 回归：非流式调用超时后 LlmService 曾返回错误串，被当作 JSON 解析而崩溃
        String error = "模型调用失败: Timeout on blocking read for 30000000000 NANOSECONDS";
        assertTrue(BenchmarkResponseParser.parseQuestions(error).isEmpty());
    }

    @Test
    void parseQuestions_blankOrNull_returnsEmpty() {
        assertTrue(BenchmarkResponseParser.parseQuestions(null).isEmpty());
        assertTrue(BenchmarkResponseParser.parseQuestions("").isEmpty());
        assertTrue(BenchmarkResponseParser.parseQuestions("   \n  ").isEmpty());
    }

    @Test
    void parseQuestions_truncatedJson_throws() {
        // 截断的 JSON（缺结尾 ]）应由调用方捕获并降级，而非静默吞掉
        String truncated = "[{\"question\":\"a\",\"goldAnswer\":\"b\",\"goldChunks\":[\"x\"]}";
        assertThrows(Exception.class, () -> BenchmarkResponseParser.parseQuestions(truncated));
    }
}
