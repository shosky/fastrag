package com.fastrag.module.tools.service;

import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.entity.McpTool;
import com.fastrag.module.tools.mapper.McpCallLogMapper;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import com.fastrag.module.tools.mapper.McpToolMapper;
import com.fastrag.module.tools.service.impl.McpServiceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * McpServiceService 单元测试。
 * 使用 Mockito 模拟 Mapper 层，测试 Service 的业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class McpServiceServiceTest {

    @Mock
    private McpServiceMapper mapper;

    @Mock
    private McpToolMapper toolMapper;

    @Mock
    private McpCallLogMapper callLogMapper;

    private McpServiceServiceImpl service;

    @BeforeEach
    void setUp() {
        // 创建 service 实例并注入 mock
        service = new McpServiceServiceImpl(mapper, toolMapper, callLogMapper);
    }

    @Test
    void testListWithKeyword() {
        String keyword = "test";
        when(mapper.selectList(any())).thenReturn(List.of(createService("1", "test-service")));
        when(toolMapper.selectCount(any())).thenReturn(0L);

        List<Map<String, Object>> result = service.list(keyword);

        assertEquals(1, result.size());
        assertEquals("test-service", result.get(0).get("name"));
        verify(mapper).selectList(any());
    }

    @Test
    void testListWithoutKeyword() {
        when(mapper.selectList(any())).thenReturn(List.of(
                createService("1", "svc1"),
                createService("2", "svc2")
        ));
        when(toolMapper.selectCount(any())).thenReturn(0L);

        List<Map<String, Object>> result = service.list(null);

        assertEquals(2, result.size());
        verify(mapper).selectList(any());
    }

    @Test
    void testGetById() {
        McpService svc = createService("42", "test-service");
        when(mapper.selectById("42")).thenReturn(svc);

        McpService result = service.get("42");

        assertNotNull(result);
        assertEquals("42", result.getId());
        assertEquals("test-service", result.getName());
    }

    @Test
    void testGetBySlug() {
        McpService svc = createService("1", "test-service");
        svc.setSlug("my-slug");
        when(mapper.selectBySlug("my-slug")).thenReturn(svc);

        McpService result = service.getBySlug("my-slug");

        assertNotNull(result);
        assertEquals("my-slug", result.getSlug());
    }

    @Test
    void testCreate() {
        when(mapper.insert(any())).thenReturn(1);

        java.util.Map<String, Object> form = new java.util.LinkedHashMap<>();
        form.put("name", "new-service");
        form.put("transport", "sse");
        form.put("mcpUrl", "https://example.com/mcp");
        form.put("authType", "none");

        McpService result = service.create(form);

        assertNotNull(result);
        assertEquals("new-service", result.getName());
        assertEquals("sse", result.getTransport());
        assertEquals("https://example.com/mcp", result.getMcpUrl());
        assertEquals(1, result.getEnabled().intValue());
        assertEquals("offline", result.getStatus());
        verify(mapper).insert(any());
    }

    @Test
    void testToggleEnabled() {
        McpService svc = createService("1", "test");
        svc.setEnabled(1);
        when(mapper.selectById("1")).thenReturn(svc);

        service.toggleEnabled("1");

        assertEquals(0, svc.getEnabled().intValue());
        verify(mapper).updateById(svc);
    }

    @Test
    void testDeleteNonBuiltin() {
        McpService svc = createService("1", "test");
        svc.setIsBuiltin(0);
        when(mapper.selectById("1")).thenReturn(svc);

        service.delete("1");

        verify(mapper).deleteById((java.io.Serializable) "1");
    }

    @Test
    void testDeleteBuiltin_ShouldThrow() {
        McpService svc = createService("1", "builtin");
        svc.setIsBuiltin(1);
        when(mapper.selectById("1")).thenReturn(svc);

        assertThrows(RuntimeException.class, () -> service.delete("1"));
        verify(mapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void testToggleTool() {
        McpTool tool = new McpTool();
        tool.setId(1L);
        tool.setEnabled(1);
        when(toolMapper.selectById(1L)).thenReturn(tool);

        service.toggleTool(1L);

        assertEquals(0, tool.getEnabled().intValue());
        verify(toolMapper).updateById(tool);
    }

    @Test
    void testListTools() {
        McpTool tool1 = new McpTool();
        tool1.setId(1L);
        tool1.setServiceId("s1");
        McpTool tool2 = new McpTool();
        tool2.setId(2L);
        tool2.setServiceId("s1");

        when(toolMapper.selectList(any())).thenReturn(List.of(tool1, tool2));

        List<McpTool> tools = service.listTools("s1");

        assertEquals(2, tools.size());
        verify(toolMapper).selectList(any());
    }

    @Test
    void testExistsBySlug() {
        when(mapper.existsBySlug("my-slug")).thenReturn(true);

        assertTrue(service.existsBySlug("my-slug"));
        assertFalse(service.existsBySlug("non-existent"));
    }

    @Test
    void testAddTool() {
        McpService svc = createService("s1", "test-service");
        when(mapper.selectById("s1")).thenReturn(svc);

        java.util.Map<String, Object> form = new java.util.LinkedHashMap<>();
        form.put("name", "my-tool");
        form.put("description", "A test tool");

        when(toolMapper.insert(any())).thenReturn(1);

        McpTool tool = service.addTool("s1", form);

        assertNotNull(tool);
        assertEquals("s1", tool.getServiceId());
        assertEquals("my-tool", tool.getName());
        assertTrue(tool.getToolId().startsWith("mcp__"));
        assertEquals(1, tool.getEnabled().intValue());
    }

    @Test
    void testAddTool_ServiceNotFound() {
        when(mapper.selectById("nonexistent")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.addTool("nonexistent", Map.of()));
    }

    @Test
    void testUpdateTool() {
        McpTool existing = new McpTool();
        existing.setId(1L);
        existing.setName("old-name");
        existing.setEnabled(1);
        existing.setServiceId("s1");
        when(toolMapper.selectById(1L)).thenReturn(existing);
        when(mapper.selectById("s1")).thenReturn(createService("s1", "test-service"));

        java.util.Map<String, Object> form = new java.util.LinkedHashMap<>();
        form.put("name", "new-name");
        form.put("enabled", 0);

        service.updateTool(1L, form);

        assertEquals("new-name", existing.getName());
        assertEquals(0, existing.getEnabled().intValue());
        verify(toolMapper).updateById(existing);
    }

    @Test
    void testUpdateTool_NotFound() {
        when(toolMapper.selectById(999L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.updateTool(999L, Map.of()));
    }

    @Test
    void testDeleteTool() {
        service.deleteTool(1L);
        verify(toolMapper).deleteById(1L);
    }

    // --- 辅助方法 ---

    private McpService createService(String id, String name) {
        McpService s = new McpService();
        s.setId(id);
        s.setName(name);
        s.setEnabled(1);
        s.setIsBuiltin(0);
        s.setStatus("online");
        s.setTransport("sse");
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }
}
