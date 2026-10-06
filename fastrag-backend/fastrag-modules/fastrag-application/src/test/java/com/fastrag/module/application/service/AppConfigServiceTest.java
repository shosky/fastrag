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
    @Mock private com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper knowledgeMapper;
    @Mock private com.fastrag.module.tools.mapper.ToolMapper toolResMapper;
    @Mock private com.fastrag.module.tools.mapper.SkillMapper skillResMapper;
    @Mock private com.fastrag.module.tools.mapper.McpServiceMapper mcpResMapper;
    @Mock private com.fastrag.module.tools.mapper.DbInstanceMapper dbResMapper;

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
                skillBindMapper,
                toolBindMapper,
                mcpBindMapper,
                knowledgeMapper,
                toolResMapper,
                skillResMapper,
                mcpResMapper,
                dbResMapper,
                objectMapper
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

    // ========== M2-6（D8）：bindSkill 技能挂载预算（MAX_SKILL_BINDINGS = 20） ==========

    @Test
    void testBindSkill_RejectsWhenEnabledBindingsReachLimit() {
        when(skillBindMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(20L);

        com.fastrag.module.application.entity.AppSkillBinding b =
                new com.fastrag.module.application.entity.AppSkillBinding();
        com.fastrag.common.exception.BusinessException e = assertThrows(
                com.fastrag.common.exception.BusinessException.class,
                () -> service.bindSkill("app-1", b));
        assertTrue(e.getMessage().contains("20"), "拒绝文案应含上限值: " + e.getMessage());
        verify(skillBindMapper, never()).insert(any(com.fastrag.module.application.entity.AppSkillBinding.class));
    }

    @Test
    void testBindSkill_ProceedsBelowLimit() {
        when(skillBindMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(19L);
        when(skillResMapper.selectById("s1")).thenReturn(enabledSkill("s1"));

        com.fastrag.module.application.entity.AppSkillBinding b =
                new com.fastrag.module.application.entity.AppSkillBinding();
        b.setSkillId("s1");

        com.fastrag.module.application.entity.AppSkillBinding result = service.bindSkill("app-1", b);

        verify(skillBindMapper).insert(any(com.fastrag.module.application.entity.AppSkillBinding.class));
        assertEquals("app-1", result.getAppId());
        assertEquals(1, result.getEnabled());
    }

    // ========== M3-5（D11）：bindSkill 校验技能存在且启用（否则 BusinessException） ==========

    @Test
    void testBindSkill_RejectsWhenSkillNotExists() {
        when(skillBindMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillResMapper.selectById("ghost")).thenReturn(null);

        com.fastrag.module.application.entity.AppSkillBinding b =
                new com.fastrag.module.application.entity.AppSkillBinding();
        b.setSkillId("ghost");

        com.fastrag.common.exception.BusinessException e = assertThrows(
                com.fastrag.common.exception.BusinessException.class,
                () -> service.bindSkill("app-1", b));
        assertTrue(e.getMessage().contains("ghost"), "拒绝文案应含技能 id: " + e.getMessage());
        verify(skillBindMapper, never()).insert(any(com.fastrag.module.application.entity.AppSkillBinding.class));
    }

    @Test
    void testBindSkill_RejectsWhenSkillDisabled() {
        when(skillBindMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(skillResMapper.selectById("s-off")).thenReturn(disabledSkill("s-off"));

        com.fastrag.module.application.entity.AppSkillBinding b =
                new com.fastrag.module.application.entity.AppSkillBinding();
        b.setSkillId("s-off");

        com.fastrag.common.exception.BusinessException e = assertThrows(
                com.fastrag.common.exception.BusinessException.class,
                () -> service.bindSkill("app-1", b));
        assertTrue(e.getMessage().contains("停用"), "拒绝文案应说明技能已停用: " + e.getMessage());
        verify(skillBindMapper, never()).insert(any(com.fastrag.module.application.entity.AppSkillBinding.class));
    }

    /** 构造 enabled=1 的技能（通过校验路径） */
    private com.fastrag.module.tools.entity.Skill enabledSkill(String id) {
        return skillWithEnabled(id, 1);
    }

    /** 构造 enabled=0 的技能（停用拒绝路径） */
    private com.fastrag.module.tools.entity.Skill disabledSkill(String id) {
        return skillWithEnabled(id, 0);
    }

    private com.fastrag.module.tools.entity.Skill skillWithEnabled(String id, int enabled) {
        com.fastrag.module.tools.entity.Skill s = new com.fastrag.module.tools.entity.Skill();
        s.setId(id);
        s.setSlug("skill-" + id);
        s.setName("技能" + id);
        s.setEnabled(enabled);
        return s;
    }
}
