package com.fastrag.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * tool_choice 序列化契约（R4 首轮强制检索，2026-10-19）：
 * 字符串（auto/none/required）序列化为 JSON 字符串；指名函数形式（Map）序列化为 JSON 对象。
 * ChatRequest.toolChoice 已从 String 放宽为 Object 以承载两种形态。
 */
class ChatRequestToolChoiceSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stringToolChoice_serializesAsString() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setModel("test-model");
        req.setToolChoice("auto");

        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(req));

        assertTrue(node.get("tool_choice").isTextual(), "字符串策略应序列化为 JSON 字符串");
        assertEquals("auto", node.get("tool_choice").asText());
    }

    @Test
    void namedFunctionToolChoice_serializesAsObject() throws Exception {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("type", "function");
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", "query_kb");
        named.put("function", fn);

        ChatRequest req = new ChatRequest();
        req.setModel("test-model");
        req.setToolChoice(named);

        JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(req));

        JsonNode toolChoice = node.get("tool_choice");
        assertTrue(toolChoice.isObject(), "指名函数形式必须序列化为 JSON 对象（String 类型承载不了）");
        assertEquals("function", toolChoice.get("type").asText());
        assertEquals("query_kb", toolChoice.get("function").get("name").asText());
    }
}
