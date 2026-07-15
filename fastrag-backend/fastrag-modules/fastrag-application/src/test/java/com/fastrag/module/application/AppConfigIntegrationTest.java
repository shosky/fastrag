package com.fastrag.module.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.application.entity.AppConfig;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.impl.AppConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 集成测试：验证 AppConfig 新字段的保存→读取完整链路。
 */
@ExtendWith(MockitoExtension.class)
class AppConfigIntegrationTest {

    @Mock private AppBasicConfigMapper basicMapper;
    @Mock private AppDialogConfigMapper dialogMapper;
    @Mock private AppTriggerMapper triggerMapper;
    @Mock private AppGlobalPolicyMapper policyMapper;
    @Mock private AppVariableMapper varMapper;
    @Mock private AppKbBindingMapper kbMapper;
    @Mock private AppConfigMapper configMapper;
    @Mock private AppDbBindingMapper dbMapper;
    @Mock private AppPublishRecordMapper pubMapper;
    @Mock private AppDialogTestMapper testMapper;
    @Mock private AppOptimizationMapper optMapper;
    @Mock private AppKbAutoUpdateConfigMapper autoKbMapper;
    @Mock private AppConversationMapper convMapper;
    @Mock private AppConversationMessageMapper convMsgMapper;
    @Mock private AppSkillBindingMapper skillBindMapper;
    @Mock private AppToolBindingMapper toolBindMapper;
    @Mock private AppMcpBindingMapper mcpBindMapper;

    @Captor private ArgumentCaptor<AppConfig> insertCaptor;
    @Captor private ArgumentCaptor<AppConfig> updateCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AppConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppConfigServiceImpl(
                basicMapper, dialogMapper, triggerMapper,
                policyMapper, varMapper, kbMapper,
                configMapper, dbMapper, pubMapper,
                testMapper, optMapper, autoKbMapper,
                convMapper, convMsgMapper,
                objectMapper,
                skillBindMapper, toolBindMapper, mcpBindMapper
        );
    }

    @Test
    void testSavePrompt_CreateNew() {
        String appId = "app-1";
        when(configMapper.selectOne(any())).thenReturn(null);

        service.savePrompt(appId, "你是智能助手");

        verify(configMapper, times(1)).insert(insertCaptor.capture());
        assertEquals("你是智能助手", insertCaptor.getValue().getPrompt());
    }

    @Test
    void testSaveSummary_CreateNew() {
        String appId = "app-2";
        when(configMapper.selectOne(any())).thenReturn(null);

        service.saveSummary(appId, Map.of("summaryThreshold", 5000, "summaryPrompt", "短摘要"));

        verify(configMapper, times(1)).insert(insertCaptor.capture());
        assertEquals(5000, insertCaptor.getValue().getSummaryThreshold());
        assertEquals("短摘要", insertCaptor.getValue().getSummaryPrompt());
    }

    @Test
    void testSaveMaxSteps_CreateNew() {
        String appId = "app-3";
        when(configMapper.selectOne(any())).thenReturn(null);

        service.saveMaxSteps(appId, 20);

        verify(configMapper, times(1)).insert(insertCaptor.capture());
        assertEquals(20, insertCaptor.getValue().getMaxSteps());
        assertEquals(8000, insertCaptor.getValue().getSummaryThreshold()); // 默认值
        assertEquals(2, insertCaptor.getValue().getRetryTimes());        // 默认值
    }

    @Test
    void testSaveRetryTimes_CreateNew() {
        String appId = "app-4";
        when(configMapper.selectOne(any())).thenReturn(null);

        service.saveRetryTimes(appId, 3);

        verify(configMapper, times(1)).insert(insertCaptor.capture());
        assertEquals(3, insertCaptor.getValue().getRetryTimes());
    }

    @Test
    void testSaveMaxTokens_CreateNew() {
        String appId = "app-5";
        when(configMapper.selectOne(any())).thenReturn(null);

        service.saveMaxTokens(appId, 4096);

        verify(configMapper, times(1)).insert(insertCaptor.capture());
        assertEquals(4096, insertCaptor.getValue().getMaxTokens());
        assertEquals(15, insertCaptor.getValue().getMaxSteps()); // 默认值
    }

    @Test
    void testUpdateExistingConfig() {
        String appId = "app-6";
        AppConfig existing = new AppConfig();
        existing.setId("cfg-1");
        existing.setAppId(appId);
        existing.setMaxSteps(15);
        existing.setMaxTokens(2048);

        // 第一次 selectOne 返回 null（创建），后续返回 existing（更新）
        when(configMapper.selectOne(any())).thenReturn(null).thenReturn(existing);

        // 创建新配置
        service.saveMaxSteps(appId, 20);

        // 验证 insert 调用
        verify(configMapper, times(1)).insert(any());

        // 更新现有配置
        service.saveMaxTokens(appId, 8192);

        verify(configMapper, times(1)).updateById(updateCaptor.capture());
        assertEquals(8192, updateCaptor.getValue().getMaxTokens());
    }

    @Test
    void testGetConfigReturnsAllNewFields() {
        String appId = "app-7";

        AppConfig existing = new AppConfig();
        existing.setId("cfg-2");
        existing.setAppId(appId);
        existing.setPrompt("test");
        existing.setSummaryThreshold(5000);
        existing.setSummaryPrompt("短摘要");
        existing.setMaxSteps(10);
        existing.setRetryTimes(1);
        existing.setMaxTokens(1024);

        when(configMapper.selectOne(any())).thenReturn(existing);

        AppConfig result = service.getConfig(appId);

        assertNotNull(result);
        assertEquals(5000, result.getSummaryThreshold());
        assertEquals("短摘要", result.getSummaryPrompt());
        assertEquals(10, result.getMaxSteps());
        assertEquals(1, result.getRetryTimes());
        assertEquals(1024, result.getMaxTokens());
    }
}
