package com.fastrag.module.application.service.preset;

import com.fastrag.common.enums.KBRole;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.session.SessionRequestStateStore;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * S1 会话预设解析矩阵（tdd/new-conversation.md S1；contract new-conversation.DialogPresetResolver）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DialogPresetResolverTest {

    @Mock private AppMapper appMapper;
    @Mock private AppConfigMapper configMapper;
    @Mock private AppBasicConfigMapper basicConfigMapper;
    @Mock private AppGlobalPolicyMapper globalPolicyMapper;
    @Mock private AppKbBindingMapper kbBindingMapper;
    @Mock private AppToolBindingMapper toolBindingMapper;
    @Mock private AppSkillBindingMapper skillBindingMapper;
    @Mock private AppMcpBindingMapper mcpBindingMapper;
    @Mock private AppDbBindingMapper dbBindingMapper;
    @Mock private ModelRecordMapper modelRecordMapper;
    @Mock private KbAccessChecker kbAccessChecker;
    @Mock private SessionRequestStateStore requestStateStore;

    @InjectMocks
    private DialogPresetResolver resolver;

    private final LoginUser user = LoginUser.builder().userId("u1").username("u1").orgId("org1").build();

    private AppConversation conversation(String appId) {
        AppConversation conv = new AppConversation();
        conv.setId("conv-1");
        conv.setAppId(appId);
        conv.setUserId("u1");
        return conv;
    }

    private App app(String id) {
        App app = new App();
        app.setId(id);
        app.setOwner("u1");
        app.setOrgId("org1");
        return app;
    }

    private void mockOnlineModel() {
        ModelRecord record = new ModelRecord();
        record.setCode("any");
        record.setStatus("online");
        record.setApiUrl("http://model-gateway");
        record.setApiKeyRef("key-ref");
        record.setEnableThinking(Boolean.TRUE);
        when(modelRecordMapper.selectOne(any())).thenReturn(record);
    }

    // ===== 缺省 =====

    @Test
    void emptyDirectChatYieldsBuiltInDefaults() {
        // [缺省] 空请求态直聊产出内置缺省配置
        EffectiveDialogConfig cfg = resolver.resolve(conversation(null), new ConversationRequestState(), user);

        assertThat(cfg.getKbScope()).isEmpty();
        assertThat(cfg.getSystemPrompt()).isEqualTo(DialogDefaults.SYSTEM_PROMPT);
        assertThat(cfg.getTemperature()).isEqualTo(0.7);
        assertThat(cfg.getMaxTokens()).isEqualTo(2048);
        assertThat(cfg.getMemoryRounds()).isEqualTo(5);
        assertThat(cfg.isSafetyEnabled()).isTrue();
        assertThat(cfg.getGreeting()).isNull();
        assertThat(cfg.getModelRoute().getModel()).isEqualTo("default");
        assertThat(cfg.getSource()).isEqualTo(EffectiveDialogConfig.PresetSource.DIRECT_CHAT);
        assertThat(cfg.getBindings().getTools()).isEmpty();
    }

    @Test
    void defaultsAreSingleSourceOfTruth() {
        // [缺省] 常量与现行编排器缺省逐字节一致（单一事实源护栏）
        assertThat(DialogDefaults.SYSTEM_PROMPT)
                .isEqualTo("你是一个智能助手，请根据用户的问题提供帮助。");
        assertThat(DialogDefaults.TEMPERATURE).isEqualTo(0.7);
        assertThat(DialogDefaults.MAX_TOKENS).isEqualTo(2048);
        assertThat(DialogDefaults.MEMORY_ROUNDS).isEqualTo(5);
        assertThat(DialogDefaults.DEFAULT_MODEL).isEqualTo("default");
    }

    // ===== 模型五级回退 =====

    @Test
    void turnStateModelWinsOverAppConfigModel() {
        // [回退] C05×D01 交界：请求态模型优先于应用配置模型
        when(appMapper.selectById("app1")).thenReturn(app("app1"));
        AppConfig config = new AppConfig();
        config.setModel("A");
        when(configMapper.selectOne(any())).thenReturn(config);
        mockOnlineModel();

        ConversationRequestState turn = new ConversationRequestState();
        turn.setAppId("app1");
        turn.setModelId("B");

        EffectiveDialogConfig cfg = resolver.resolve(conversation("app1"), turn, user);

        assertThat(cfg.getModelRoute().getModel()).isEqualTo("B");
        assertThat(cfg.getModelRoute().getApiUrl()).isEqualTo("http://model-gateway");
        assertThat(cfg.getSource()).isEqualTo(EffectiveDialogConfig.PresetSource.APP_PRESET);
    }

    @Test
    void advancedOptionsModelUsedWhenConfigModelBlank() {
        // [回退] 第三级：basic.advancedOptions.model
        when(appMapper.selectById("app1")).thenReturn(app("app1"));
        AppConfig config = new AppConfig();
        when(configMapper.selectOne(any())).thenReturn(config);
        AppBasicConfig basic = new AppBasicConfig();
        basic.setAdvancedOptions("{\"model\":\"C\",\"other\":1}");
        when(basicConfigMapper.selectOne(any())).thenReturn(basic);
        mockOnlineModel();

        ConversationRequestState turn = new ConversationRequestState();
        turn.setAppId("app1");

        assertThat(resolver.resolve(conversation("app1"), turn, user).getModelRoute().getModel())
                .isEqualTo("C");
    }

    @Test
    void allModelSourcesBlankFallsBackToDefaultGateway() {
        // [回退] 终点：默认网关
        when(appMapper.selectById("app1")).thenReturn(app("app1"));

        ConversationRequestState turn = new ConversationRequestState();
        turn.setAppId("app1");

        assertThat(resolver.resolve(conversation("app1"), turn, user).getModelRoute().getModel())
                .isEqualTo("default");
    }

    // ===== D02 互斥 =====

    @Test
    void mountedAppIgnoresTurnStateKbIds() {
        // [互斥] 挂载应用不消费请求态知识库范围（结构化互斥）
        when(appMapper.selectById("app1")).thenReturn(app("app1"));
        AppKbBinding binding = new AppKbBinding();
        binding.setAppId("app1");
        binding.setEnabled(1);
        binding.setKbId("kb-1");
        when(kbBindingMapper.selectList(any())).thenReturn(List.of(binding));
        when(kbAccessChecker.resolveRole(eq("kb-1"), any())).thenReturn(KBRole.viewer);

        ConversationRequestState turn = new ConversationRequestState();
        turn.setAppId("app1");
        turn.setKbIds(List.of("kb-2"));

        EffectiveDialogConfig cfg = resolver.resolve(conversation("app1"), turn, user);

        assertThat(cfg.getKbScope()).containsExactly("kb-1");
        verify(kbAccessChecker, never()).resolveRole(eq("kb-2"), any());
    }

    @Test
    void directChatTakesTurnStateKbIds() {
        // [互斥] 直聊知识库范围取请求态
        ConversationRequestState turn = new ConversationRequestState();
        turn.setKbIds(List.of("kb-2"));
        when(kbAccessChecker.resolveRole(eq("kb-2"), any())).thenReturn(KBRole.viewer);

        EffectiveDialogConfig cfg = resolver.resolve(conversation(null), turn, user);

        assertThat(cfg.getKbScope()).containsExactly("kb-2");
    }

    // ===== B03 ACL =====

    @Test
    void inaccessibleKbInTurnStateSkippedWithWarning() {
        // [ACL] 请求态源无权 KB 跳过（viewer 即可，null 拒绝）
        ConversationRequestState turn = new ConversationRequestState();
        turn.setKbIds(List.of("kb-ok", "kb-denied"));
        when(kbAccessChecker.resolveRole(eq("kb-ok"), any())).thenReturn(KBRole.viewer);

        EffectiveDialogConfig cfg = resolver.resolve(conversation(null), turn, user);

        assertThat(cfg.getKbScope()).containsExactly("kb-ok");
        verify(kbAccessChecker).resolveRole(eq("kb-denied"), any());
    }

    @Test
    void appBoundKbsAlsoFilteredInNewChannel() {
        // [ACL] 应用绑定源同样过滤（仅新通道；PRESET_OLD 不过滤见哨兵等价用例）
        when(appMapper.selectById("app1")).thenReturn(app("app1"));
        AppKbBinding ok = new AppKbBinding();
        ok.setAppId("app1");
        ok.setEnabled(1);
        ok.setKbId("kb-ok");
        AppKbBinding denied = new AppKbBinding();
        denied.setAppId("app1");
        denied.setEnabled(1);
        denied.setKbId("kb-denied");
        when(kbBindingMapper.selectList(any())).thenReturn(List.of(ok, denied));
        when(kbAccessChecker.resolveRole(eq("kb-ok"), any())).thenReturn(KBRole.viewer);

        ConversationRequestState turn = new ConversationRequestState();
        turn.setAppId("app1");

        assertThat(resolver.resolve(conversation("app1"), turn, user).getKbScope())
                .containsExactly("kb-ok");
    }

    // ===== B02 死字段 =====

    @Test
    void directChatConsumesToolMcpSkillIdsIntoBindings() {
        // [B02 演进] 直聊消费工具/MCP/技能 ID（装配 Bindings，与绑定同路径；db 恒空）
        ConversationRequestState turn = new ConversationRequestState();
        turn.setToolIds(List.of("t-1"));
        turn.setMcpIds(List.of("m-1"));
        turn.setSkillIds(List.of("s-1"));

        EffectiveDialogConfig cfg = resolver.resolve(conversation(null), turn, user);

        assertThat(cfg.getBindings().getTools()).containsExactly("t-1");
        assertThat(cfg.getBindings().getMcps()).containsExactly("m-1");
        assertThat(cfg.getBindings().getSkills()).containsExactly("s-1");
        assertThat(cfg.getBindings().getDatabases()).isEmpty();
    }

    @Test
    void webSearchEnabledNeverAppearsInEffectiveConfig() {
        // [死字段] webSearchEnabled 是唯一死字段（B02 演进后），形状反射断言
        ConversationRequestState turn = new ConversationRequestState();
        turn.setWebSearchEnabled(Boolean.TRUE);

        EffectiveDialogConfig cfg = resolver.resolve(conversation(null), turn, user);

        java.util.Set<String> fieldNames = Arrays.stream(EffectiveDialogConfig.class.getDeclaredFields())
                .map(Field::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("websearch"));
        assertThat(cfg.getBindings().getTools()).isEmpty();
    }

    // ===== 哨兵与门禁 =====

    @Test
    void presetOnlySentinelReadsAppPresetEvenWithNullTurnStateFields() {
        // [降级/哨兵] PRESET_ONLY 走会话归属应用的全套应用配置（存量会话 D03 同路径）
        when(appMapper.selectById("app1")).thenReturn(app("app1"));
        AppConfig config = new AppConfig();
        config.setModel("A");
        config.setPrompt("P");
        config.setTemperature(new java.math.BigDecimal("0.50"));
        config.setMaxTokens(100);
        config.setSummaryThreshold(8000);
        config.setRetryTimes(2);
        config.setMaxSteps(15);
        config.setSummaryPrompt("SP");
        when(configMapper.selectOne(any())).thenReturn(config);
        AppBasicConfig basic = new AppBasicConfig();
        basic.setMemoryRounds(2);
        basic.setGreeting("G");
        when(basicConfigMapper.selectOne(any())).thenReturn(basic);
        AppGlobalPolicy policy = new AppGlobalPolicy();
        policy.setSafetyEnabled(0);
        when(globalPolicyMapper.selectOne(any())).thenReturn(policy);
        AppKbBinding binding = new AppKbBinding();
        binding.setAppId("app1");
        binding.setEnabled(1);
        binding.setKbId("kb-denied");
        when(kbBindingMapper.selectList(any())).thenReturn(List.of(binding));

        EffectiveDialogConfig cfg = resolver.resolve(
                conversation("app1"), ConversationRequestState.PRESET_ONLY, user);

        // G01：PRESET_ONLY 产物 = 现行内联读取语义（含 ACL 不过滤）
        assertThat(cfg.getModelRoute().getModel()).isEqualTo("A");
        assertThat(cfg.getSystemPrompt()).isEqualTo("P");
        assertThat(cfg.getTemperature()).isEqualTo(0.5);
        assertThat(cfg.getMaxTokens()).isEqualTo(100);
        assertThat(cfg.getMemoryRounds()).isEqualTo(2);
        assertThat(cfg.getSummaryThreshold()).isEqualTo(8000);
        assertThat(cfg.getModelRetryTimes()).isEqualTo(2);
        assertThat(cfg.getMaxSteps()).isEqualTo(15);
        assertThat(cfg.getSummaryPrompt()).isEqualTo("SP");
        assertThat(cfg.isSafetyEnabled()).isFalse();
        assertThat(cfg.getGreeting()).isEqualTo("G");
        assertThat(cfg.getKbScope()).containsExactly("kb-denied");
        verifyNoInteractions(kbAccessChecker);
    }

    @Test
    void invisibleMountedAppRejected() {
        // [门禁] 挂载应用不可见 → forbidden；不存在 → notFound
        App invisible = app("app1");
        invisible.setOwner("someone-else");
        invisible.setOrgId("other-org");
        when(appMapper.selectById("app1")).thenReturn(invisible);

        ConversationRequestState turn = new ConversationRequestState();
        turn.setAppId("app1");

        assertThatThrownBy(() -> resolver.resolve(conversation("app1"), turn, user))
                .isInstanceOf(BusinessException.class);

        when(appMapper.selectById("appX")).thenReturn(null);
        ConversationRequestState turn2 = new ConversationRequestState();
        turn2.setAppId("appX");
        assertThatThrownBy(() -> resolver.resolve(conversation("appX"), turn2, user))
                .isInstanceOf(BusinessException.class);
    }
}
