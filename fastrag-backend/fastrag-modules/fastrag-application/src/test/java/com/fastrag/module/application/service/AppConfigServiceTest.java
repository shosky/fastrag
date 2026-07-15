package com.fastrag.module.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.application.entity.AppConfig;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.impl.AppConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AppConfigServiceImpl} — Phase 1: savePrompt, saveSummary,
 * saveMaxSteps, saveRetryTimes, saveMaxTokens, getConfig.
 *
 * <p>Uses @Mock for all 16 constructor dependencies so we can isolate the service logic
 * without a Spring context.</p>
 */
@ExtendWith(MockitoExtension.class)
class AppConfigServiceTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        // Constructor injection — follows field declaration order in AppConfigServiceImpl
        service = new AppConfigServiceImpl(
                basicMapper,
                dialogMapper,
                triggerMapper,
                policyMapper,
                varMapper,
                kbMapper,
                configMapper,
                dbMapper,
                pubMapper,
                testMapper,
                optMapper,
                autoKbMapper,
                convMapper,
                convMsgMapper,
                objectMapper,
                skillBindMapper,
                toolBindMapper,
                mcpBindMapper
        );
    }

    // ========== testGetConfig ==========

    @Test
    void testGetConfig() {
        AppConfig expected = new AppConfig();
        expected.setAppId("app-1");
        expected.setPrompt("Existing prompt");

        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(expected);

        AppConfig result = service.getConfig("app-1");

        assertNotNull(result);
        assertEquals("app-1", result.getAppId());
        assertEquals("Existing prompt", result.getPrompt());
        verify(configMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    // ========== savePrompt — create new ==========

    @Test
    void testSavePromptCreateNew() {
        // getConfig returns null → should INSERT
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppConfig result = service.savePrompt("app-new", "You are a helpful assistant.");

        assertNotNull(result);
        assertEquals("app-new", result.getAppId());
        assertEquals("You are a helpful assistant.", result.getPrompt());
        // Verify insert was called (new record)
        verify(configMapper).insert(any(AppConfig.class));
        verify(configMapper, never()).updateById(any(AppConfig.class));
    }

    // ========== savePrompt — update existing ==========

    @Test
    void testSavePromptUpdateExisting() {
        AppConfig existing = new AppConfig();
        existing.setId("existing-id");
        existing.setAppId("app-1");
        existing.setPrompt("Old prompt");

        when(configMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existing);

        AppConfig result = service.savePrompt("app-1", "Updated prompt");

        assertNotNull(result);
        assertEquals("Updated prompt", result.getPrompt());
        // Verify updateById was called (existing record)
        verify(configMapper).updateById(any(AppConfig.class));
        verify(configMapper, never()).insert(any(AppConfig.class));
    }

    // ========== saveSummary ==========

    @Test
    void testSaveSummary() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppConfig result = service.saveSummary("app-1", Map.of(
                "summaryThreshold", 12000,
                "summaryPrompt", "Summarize the conversation concisely."
        ));

        assertNotNull(result);
        assertEquals(12000, result.getSummaryThreshold());
        assertEquals("Summarize the conversation concisely.", result.getSummaryPrompt());
        verify(configMapper).insert(any(AppConfig.class));
    }

    // ========== saveMaxSteps ==========

    @Test
    void testSaveMaxSteps() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppConfig result = service.saveMaxSteps("app-1", 20);

        assertNotNull(result);
        assertEquals(20, result.getMaxSteps());
        verify(configMapper).insert(any(AppConfig.class));
    }

    // ========== saveRetryTimes ==========

    @Test
    void testSaveRetryTimes() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppConfig result = service.saveRetryTimes("app-1", 5);

        assertNotNull(result);
        assertEquals(5, result.getRetryTimes());
        verify(configMapper).insert(any(AppConfig.class));
    }

    // ========== saveMaxTokens ==========

    @Test
    void testSaveMaxTokens() {
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppConfig result = service.saveMaxTokens("app-1", 4096);

        assertNotNull(result);
        assertEquals(4096, result.getMaxTokens());
        verify(configMapper).insert(any(AppConfig.class));
    }
}
