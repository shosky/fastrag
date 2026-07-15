package com.fastrag.module.tools.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.McpService;
import com.fastrag.module.tools.mapper.McpServiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpBuiltinSeederTest {

    @Mock
    private McpServiceMapper mapper;

    private McpBuiltinSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new McpBuiltinSeeder(mapper);
    }

    @Test
    void testRun_CreatesNewServices() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        seeder.run();

        verify(mapper, times(3)).insert(any(McpService.class));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void testRun_UpdatesExistingServices() {
        McpService fs = new McpService(); fs.setId("1"); fs.setSlug("filesystem"); fs.setName("文件系统"); fs.setEnabled(1);
        McpService fetch = new McpService(); fetch.setId("2"); fetch.setSlug("fetch"); fetch.setName("网页抓取"); fetch.setEnabled(1);
        McpService st = new McpService(); st.setId("3"); st.setSlug("sequential-thinking"); st.setName("链式思维"); st.setEnabled(1);

        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(fs)
            .thenReturn(fetch)
            .thenReturn(st);

        seeder.run();

        verify(mapper, never()).insert(any());
        verify(mapper, times(3)).updateById(any());
    }

    @Test
    void testRun_PreservesEnabledStateOnUpdate() {
        McpService fs = new McpService(); fs.setId("1"); fs.setSlug("filesystem"); fs.setName("原有名称"); fs.setEnabled(0);
        McpService fetch = new McpService(); fetch.setId("2"); fetch.setSlug("fetch"); fetch.setName("网页抓取"); fetch.setEnabled(1);
        McpService st = new McpService(); st.setId("3"); st.setSlug("sequential-thinking"); st.setName("链式思维"); st.setEnabled(1);

        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(fs)
            .thenReturn(fetch)
            .thenReturn(st);

        seeder.run();

        ArgumentCaptor<McpService> captor = ArgumentCaptor.forClass(McpService.class);
        verify(mapper, times(3)).updateById(captor.capture());

        McpService updated = captor.getAllValues().stream()
            .filter(s -> "filesystem".equals(s.getSlug()))
            .findFirst().orElse(null);
        assertNotNull(updated);
        assertEquals("文件系统", updated.getName());  // name 应该被更新
        assertNotEquals("原有名称", updated.getName()); // 确认更新了
        // enabled 应该保留用户设置（0）
        assertEquals(0, updated.getEnabled().intValue());
    }

    @Test
    void testRun_MixedNewAndExisting() {
        McpService fetch = new McpService(); fetch.setSlug("fetch");
        McpService st = new McpService(); st.setSlug("sequential-thinking");

        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
            .thenReturn(null)   // filesystem - 新
            .thenReturn(fetch)  // fetch - 已有
            .thenReturn(st);    // sequential-thinking - 已有

        seeder.run();

        verify(mapper, times(1)).insert(any());  // 1 个新服务
        verify(mapper, times(2)).updateById(any());  // 2 个更新
    }
}
