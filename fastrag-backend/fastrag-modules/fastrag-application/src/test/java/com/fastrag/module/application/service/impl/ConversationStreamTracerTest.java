package com.fastrag.module.application.service.impl;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.agent.executor.AgentEngine;
import com.fastrag.module.agent.executor.AgentEngine.StreamCompleteCallback;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.AppConversation;
import com.fastrag.module.application.entity.AppConversationMessage;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.AppService;
import com.fastrag.module.application.service.preset.DialogDefaults;
import com.fastrag.module.application.service.preset.DialogPresetResolver;
import com.fastrag.module.application.service.preset.EffectiveDialogConfig;
import com.fastrag.module.application.service.session.SessionRequestStateStore;
import com.fastrag.module.application.service.session.StopRegistry;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.SensitiveWordService;
import com.fastrag.module.retrieval.service.RetrievalService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.security.filter.LoginUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S3 Tracer Bullet（tdd/new-conversation.md §4 首测）：空范围直聊全链——
 * 创建态会话 → 解析（mock 产物，S1 已深测）→ 零检索 → 引擎执行 → 落库+固化+引用空数组。
 * 贯穿解析器消费面、执行核、停止注册表、请求态属主与账本五处新代码。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationStreamTracerTest {

    @Mock private AppMapper appMapper;
    @Mock private AppConfigMapper configMapper;
    @Mock private AppTemplateMapper tplMapper;
    @Mock private AppConversationMapper convMapper;
    @Mock private AppConversationMessageMapper convMsgMapper;
    @Mock private AppKbBindingMapper kbBindingMapper;
    @Mock private AppBasicConfigMapper basicConfigMapper;
    @Mock private KnowledgeBaseMapper kbMapper;
    @Mock private ModelRecordMapper modelRecordMapper;
    @Mock private AppToolBindingMapper toolBindingMapper;
    @Mock private AppSkillBindingMapper skillBindingMapper;
    @Mock private AppMcpBindingMapper mcpBindingMapper;
    @Mock private AppDbBindingMapper dbMapper;
    @Mock private LlmService llmService;
    @Mock private RetrievalService retrievalService;
    @Mock private AgentEngine agentEngine;
    @Mock private SensitiveWordService sensitiveWordService;
    @Mock private AppGlobalPolicyMapper globalPolicyMapper;
    @Mock private DialogPresetResolver presetResolver;
    @Mock private StopRegistry stopRegistry;
    @Mock private SessionRequestStateStore requestStateStore;

    @InjectMocks
    private AppServiceImpl service;

    private final LoginUser user = LoginUser.builder().userId("u1").username("u1").orgId("org1").build();

    private AppConversation directConversation() {
        AppConversation conv = new AppConversation();
        conv.setId("conv-1");
        conv.setUserId("u1");
        conv.setAppId(null); // 直聊
        conv.setMessageCount(0);
        return conv;
    }

    private EffectiveDialogConfig directDefaults() {
        return EffectiveDialogConfig.builder()
                .kbScope(List.of())
                .bindings(EffectiveDialogConfig.Bindings.builder()
                        .tools(List.of()).skills(List.of()).mcps(List.of()).databases(List.of()).build())
                .modelRoute(EffectiveDialogConfig.ModelRoute.builder()
                        .model(DialogDefaults.DEFAULT_MODEL).build())
                .systemPrompt(DialogDefaults.SYSTEM_PROMPT)
                .temperature(DialogDefaults.TEMPERATURE)
                .maxTokens(DialogDefaults.MAX_TOKENS)
                .memoryRounds(DialogDefaults.MEMORY_ROUNDS)
                .safetyEnabled(true)
                .greeting(null)
                .source(EffectiveDialogConfig.PresetSource.DIRECT_CHAT)
                .build();
    }

    @Test
    void emptyScopeDirectChatSkipsRetrievalAndPersistsFullChain() {
        // [Tracer Bullet] 空 kbScope 直聊全链：零检索、两消息落库、引用空数组、请求态固化
        AppConversation conv = directConversation();
        when(convMapper.selectById("conv-1")).thenReturn(conv);
        ConversationRequestState turn = new ConversationRequestState(); // 空=直聊空范围
        when(requestStateStore.load("conv-1")).thenReturn(turn);
        when(presetResolver.resolve(eq(conv), eq(turn), eq(user))).thenReturn(directDefaults());
        when(sensitiveWordService.checkInput("知识图谱是什么")).thenReturn(Optional.empty());
        when(convMsgMapper.selectList(any())).thenReturn(List.of());
        doAnswer(inv -> {
            StreamCompleteCallback cb = inv.getArgument(4);
            cb.accept("知识图谱是实体关系网络。", null, null);
            return null;
        }).when(agentEngine).executeStream(any(), any(), any(), any(), any());

        service.runStreamByConversation("conv-1", "知识图谱是什么", turn, user);

        // 零检索（REQ-C01/C03）
        verify(retrievalService, never()).search(any());
        // 引擎恰一次（REQ-C02 复用）；异步线程执行，用 timeout 等待
        verify(agentEngine, timeout(3000).times(1)).executeStream(any(), any(), any(), any(), any());
        // 两消息落库：user + assistant（引用为空数组投影，REQ-F01）
        verify(convMsgMapper, timeout(3000).times(2)).insert(any(AppConversationMessage.class));
        ArgumentCaptor<AppConversationMessage> msgCaptor = ArgumentCaptor.forClass(AppConversationMessage.class);
        verify(convMsgMapper, timeout(3000).atLeast(2)).insert(msgCaptor.capture());
        AppConversationMessage assistant = msgCaptor.getAllValues().stream()
                .filter(m -> "assistant".equals(m.getRole())).findFirst().orElseThrow();
        assertThat(assistant.getContent()).isEqualTo("知识图谱是实体关系网络。");
        assertThat(assistant.getReferences()).isEqualTo("[]");
        // 请求态固化（REQ-B01：正常完成落库点）
        verify(requestStateStore, timeout(3000)).persistOnSuccess(eq("conv-1"), eq(turn));
        // 敏感词输入检查发生过（直聊安全缺省开，REQ-C04）
        verify(sensitiveWordService).checkInput("知识图谱是什么");
    }

    @Test
    void blockedInputShortCircuitsWithoutModelCall() {
        // [安全] 直聊缺省开拦截：命中 blockInput → 引擎零调用、无消息落库（REQ-C04/E03）
        AppConversation conv = directConversation();
        when(convMapper.selectById("conv-1")).thenReturn(conv);
        ConversationRequestState turn = new ConversationRequestState();
        when(requestStateStore.load("conv-1")).thenReturn(turn);
        when(presetResolver.resolve(eq(conv), eq(turn), eq(user))).thenReturn(directDefaults());
        when(sensitiveWordService.checkInput(any())).thenReturn(Optional.of("该输入包含违禁内容"));

        service.runStreamByConversation("conv-1", "违禁词", turn, user);

        verify(agentEngine, never()).executeStream(any(), any(), any(), any(), any());
        verify(convMsgMapper, never()).insert(any(AppConversationMessage.class));
    }

    @Test
    void mountedConversationRejectsForeignUser() {
        // [归属] 直聊会话非属主 → forbidden（主键定位不变量）
        AppConversation conv = directConversation();
        conv.setUserId("someone-else");
        when(convMapper.selectById("conv-1")).thenReturn(conv);

        try {
            service.runStreamByConversation("conv-1", "q", new ConversationRequestState(),
                    LoginUser.builder().userId("u1").build());
        } catch (BusinessException expected) {
            // 归属拒绝即目标行为
        }
        verify(agentEngine, never()).executeStream(any(), any(), any(), any(), any());
    }
}
