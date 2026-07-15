package com.fastrag.module.tools.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.entity.McpTool;
import com.fastrag.module.tools.service.McpServiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * McpController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class McpControllerTest {

    @Mock
    private McpServiceService svc;

    @InjectMocks
    private McpController controller;

    @Test
    void testList() {
        when(svc.list(null)).thenReturn(List.of(Map.of("id", "1", "name", "s1")));

        ApiResponse<?> resp = controller.list(null);

        assertEquals(200, resp.getCode());
        assertNotNull(resp.getData());
    }

    @Test
    void testListWithKeyword() {
        when(svc.list("test")).thenReturn(List.of(Map.of("id", "1", "name", "test-svc")));

        ApiResponse<?> resp = controller.list("test");

        assertEquals(200, resp.getCode());
    }

    @Test
    void testGet() {
        when(svc.getWithTools("1")).thenReturn(Map.of("id", "1", "name", "s1"));

        ApiResponse<?> resp = controller.get("1");

        assertEquals(200, resp.getCode());
    }

    @Test
    void testCreate() {
        McpService created = createService("new-id", "new-svc");
        when(svc.create(any())).thenReturn(created);

        ApiResponse<?> resp = controller.create(Map.of("name", "new-svc"));

        assertEquals(200, resp.getCode());
        assertNotNull(resp.getData());
    }

    @Test
    void testDelete() {
        doNothing().when(svc).delete("1");

        ApiResponse<?> resp = controller.delete("1");

        assertEquals(200, resp.getCode());
        verify(svc).delete("1");
    }

    @Test
    void testToggle() {
        doNothing().when(svc).toggleEnabled("1");

        ApiResponse<?> resp = controller.toggle("1");

        assertEquals(200, resp.getCode());
        verify(svc).toggleEnabled("1");
    }

    @Test
    void testTools() {
        McpTool tool = new McpTool();
        tool.setId(1L);
        tool.setName("test-tool");
        when(svc.listTools("1")).thenReturn(List.of(tool));

        ApiResponse<?> resp = controller.tools("1");

        assertEquals(200, resp.getCode());
    }

    @Test
    void testRefresh() {
        McpService refreshed = createService("1", "refreshed");
        when(svc.refresh("1")).thenReturn(refreshed);

        ApiResponse<?> resp = controller.refresh("1");

        assertEquals(200, resp.getCode());
        verify(svc).refresh("1");
    }

    @Test
    void testListEnabled() {
        when(svc.listEnabled()).thenReturn(List.of(Map.of("id", "1", "name", "enabled-svc")));

        ApiResponse<?> resp = controller.listEnabled();

        assertEquals(200, resp.getCode());
    }

    @Test
    void testGetBySlug() {
        when(svc.getBySlug("my-slug")).thenReturn(createService("1", "slug-svc"));

        ApiResponse<?> resp = controller.getBySlug("my-slug");

        assertEquals(200, resp.getCode());
    }

    @Test
    void testAddTool() {
        McpTool tool = new McpTool();
        tool.setId(99L);
        tool.setName("new-tool");
        when(svc.addTool(any(), any())).thenReturn(tool);

        ApiResponse<?> resp = controller.addTool("1", Map.of("name", "new-tool"));

        assertEquals(200, resp.getCode());
        assertNotNull(resp.getData());
    }

    @Test
    void testUpdateTool() {
        McpTool tool = new McpTool();
        tool.setId(1L);
        tool.setName("updated-tool");
        when(svc.updateTool(any(), any())).thenReturn(tool);

        ApiResponse<?> resp = controller.updateTool(1L, Map.of("name", "updated-tool"));

        assertEquals(200, resp.getCode());
    }

    @Test
    void testDeleteTool() {
        doNothing().when(svc).deleteTool(1L);

        ApiResponse<?> resp = controller.deleteTool(1L);

        assertEquals(200, resp.getCode());
        verify(svc).deleteTool(1L);
    }

    // --- 辅助 ---

    private McpService createService(String id, String name) {
        McpService s = new McpService();
        s.setId(id);
        s.setName(name);
        s.setEnabled(1);
        s.setStatus("online");
        s.setTransport("sse");
        s.setCreatedAt(LocalDateTime.now());
        return s;
    }
}
