package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillToolExecutor 单元测试。
 * 测试 read_skill / activate 操作和边界情况。
 */
@ExtendWith(MockitoExtension.class)
class SkillToolExecutorTest {

    private SkillToolExecutor executor;
    private ToolDefinition toolDef;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        executor = new SkillToolExecutor();
        toolDef = new ToolDefinition();
        toolDef.setToolId("skill_123");
        toolDef.setName("web-search");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("skillId", "123");
        config.put("slug", "web-search");
        config.put("name", "联网搜索");
        config.put("description", "搜索引擎工具");
        config.put("content", "# Web Search\n\n搜索互联网获取实时信息。");
        config.put("dependencies", List.of("tool:tavily_search"));
        toolDef.setConfig(config);

        ctx = new ToolContext();
        ctx.setUserId("user1");
        ctx.setQuery("test query");
    }

    @Test
    void testGetType() {
        assertEquals("skill", executor.getType());
    }

    @Test
    void testReadSkillAction() {
        Map<String, Object> args = Map.of("action", "read_skill");
        ToolResult result = executor.execute(toolDef, args, ctx);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("联网搜索"));
        assertTrue(result.getOutput().contains("web-search"));
        assertTrue(result.getOutput().contains("搜索引擎工具"));
        assertTrue(result.getOutput().contains("Web Search"));
        assertTrue(result.getDurationMs() >= 0);
    }

    @Test
    void testActivateAction() {
        Map<String, Object> args = Map.of("action", "activate");
        ToolResult result = executor.execute(toolDef, args, ctx);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("联网搜索"));
        assertTrue(result.getOutput().contains("已激活"));
        assertTrue(result.getOutput().contains("tavily_search"));
    }

    @Test
    void testMissingAction() {
        Map<String, Object> args = Map.of();
        ToolResult result = executor.execute(toolDef, args, ctx);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    void testUnknownAction() {
        Map<String, Object> args = Map.of("action", "invalid_action");
        ToolResult result = executor.execute(toolDef, args, ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("未知操作"));
    }

    @Test
    void testNullArguments() {
        ToolResult result = executor.execute(toolDef, null, ctx);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    void testNullConfig() {
        ToolDefinition noConfig = new ToolDefinition();
        noConfig.setToolId("skill_999");
        noConfig.setName("empty");

        Map<String, Object> args = Map.of("action", "activate");
        ToolResult result = executor.execute(noConfig, args, ctx);

        assertTrue(result.isSuccess());  // 应该正常返回，只是名称显示 Unknown
        assertTrue(result.getOutput().contains("Unknown"));
    }
}
