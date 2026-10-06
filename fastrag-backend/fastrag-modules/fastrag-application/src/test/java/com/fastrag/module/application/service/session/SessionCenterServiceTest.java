package com.fastrag.module.application.service.session;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.application.dto.ConversationDTO;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.App;
import com.fastrag.module.application.entity.AppConversation;
import com.fastrag.module.application.mapper.AppConversationMapper;
import com.fastrag.module.application.mapper.AppConversationMessageMapper;
import com.fastrag.module.application.mapper.AppMapper;
import com.fastrag.module.application.service.AppService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * S2 会话中心生命周期（tdd/new-conversation.md S2；contract new-conversation.SessionCenter）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionCenterServiceTest {

    @Mock private AppConversationMapper convMapper;
    @Mock private AppConversationMessageMapper convMsgMapper;
    @Mock private AppMapper appMapper;
    @Mock private LlmService llmService;
    @Mock private SessionRequestStateStore requestStateStore;
    @Mock private StopRegistry stopRegistry;
    @Mock private AppService appService;

    @InjectMocks
    private SessionCenterService service;

    private final LoginUser user = LoginUser.builder().userId("u1").username("u1").orgId("org1").build();
    private MockedStatic<SecurityUtil> securityUtil;

    @AfterEach
    void tearDown() {
        if (securityUtil != null) securityUtil.close();
    }

    private void login(LoginUser u) {
        securityUtil = mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getCurrentUser).thenReturn(u);
    }

    private AppConversation conversation(String id, String userId, String appId) {
        AppConversation conv = new AppConversation();
        conv.setId(id);
        conv.setUserId(userId);
        conv.setAppId(appId);
        conv.setTitle("会话");
        conv.setMessageCount(2);
        conv.setFirstQuestion("首问");
        conv.setAnswerSummary("摘要");
        conv.setIsPinned(0);
        return conv;
    }

    // ===== 创建 =====

    @Test
    void emptyFormCreatesDirectConversation() {
        // [创建] 空 form 建直聊会话：app_id=NULL、空请求态
        login(user);

        ConversationDTO dto = service.createConversation(Map.of());

        assertThat(dto.isDirect()).isTrue();
        assertThat(dto.getTitle()).isEqualTo("新对话");
        ArgumentCaptor<AppConversation> captor = ArgumentCaptor.forClass(AppConversation.class);
        verify(convMapper).insert(captor.capture());
        assertThat(captor.getValue().getAppId()).isNull();
        assertThat(captor.getValue().getRequestState()).contains("kbIds");
    }

    @Test
    void createWithInvisibleAppRejected() {
        // [门禁] 挂载不可见应用 → forbidden
        login(user);
        App invisible = new App();
        invisible.setId("app1");
        invisible.setOwner("someone-else");
        invisible.setOrgId("other-org");
        when(appMapper.selectById("app1")).thenReturn(invisible);

        assertThatThrownBy(() -> service.createConversation(Map.of("appId", "app1")))
                .isInstanceOf(BusinessException.class);
    }

    // ===== 列表 =====

    @Test
    void listMapsSourceLabelAndPageResultShape() {
        // [列表] 来源标记批量查询 + PageResult 形状（NFR-01）
        login(user);
        when(convMapper.selectCount(any())).thenReturn(2L);
        AppConversation direct = conversation("c1", "u1", null);
        AppConversation mounted = conversation("c2", "u1", "app1");
        when(convMapper.selectList(any())).thenReturn(List.of(direct, mounted));
        App app = new App();
        app.setId("app1");
        app.setName("应用A");
        when(appMapper.selectBatchIds(any())).thenReturn(List.of(app));

        PageResult<ConversationDTO> result = service.listConversations(1, 20);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getList()).hasSize(2);
        assertThat(result.getList().get(0).isDirect()).isTrue();
        assertThat(result.getList().get(1).getAppName()).isEqualTo("应用A");
        verify(appMapper, times(1)).selectBatchIds(any()); // 防每行一查（N+1）
    }

    // ===== 清空 / 批量删 =====

    @Test
    void clearMessagesDeletesAllAndResetsSummary() {
        // [清空] 全删（含软删）+ 计数归零 + firstQuestion/answerSummary 置空（REQ-E03）
        login(user);
        AppConversation conv = conversation("c1", "u1", null);
        when(convMapper.selectById("c1")).thenReturn(conv);

        service.clearMessages("c1");

        verify(convMsgMapper).delete(any());
        assertThat(conv.getMessageCount()).isEqualTo(0);
        assertThat(conv.getFirstQuestion()).isNull();
        assertThat(conv.getAnswerSummary()).isNull();
    }

    @Test
    void batchDeleteCascadesPerConversation() {
        // [批量] 逐会话级联删（同单删语义）
        login(user);
        when(convMapper.selectById("c1")).thenReturn(conversation("c1", "u1", null));
        when(convMapper.selectById("c2")).thenReturn(conversation("c2", "u1", null));

        service.deleteConversations(List.of("c1", "c2"));

        verify(convMapper).deleteById("c1");
        verify(convMapper).deleteById("c2");
        verify(convMsgMapper, times(2)).delete(any());
    }

    @Test
    void foreignConversationRejected() {
        // [归属] 非属主访问被拒
        login(user);
        when(convMapper.selectById("c1")).thenReturn(conversation("c1", "someone-else", null));

        assertThatThrownBy(() -> service.pin("c1", true))
                .isInstanceOf(BusinessException.class);
    }

    // ===== 起标题 =====

    @Test
    void generateTitleUsesModelAndTrims() {
        // [标题] 成功路径：模型取请求态 modelId，标题清洗后落库
        login(user);
        AppConversation conv = conversation("c1", "u1", null);
        conv.setFirstQuestion("什么是知识图谱？请详细解释");
        when(convMapper.selectById("c1")).thenReturn(conv);
        when(requestStateStore.loadOf(conv)).thenReturn(new ConversationRequestState());
        when(llmService.chat(eq("default"), any(String.class))).thenReturn("「知识图谱」简介。");

        String title = service.generateTitle("c1");

        assertThat(title).isEqualTo("知识图谱」简介");
        verify(convMapper).updateById(conv);
    }

    @Test
    void generateTitleFallsBackSilentlyOnFailure() {
        // [标题] 失败回退：不抛错、保持原标题
        login(user);
        AppConversation conv = conversation("c1", "u1", null);
        when(convMapper.selectById("c1")).thenReturn(conv);
        when(requestStateStore.loadOf(conv)).thenReturn(new ConversationRequestState());
        when(llmService.chat(any(String.class), any(String.class)))
                .thenThrow(new RuntimeException("gateway down"));

        String title = service.generateTitle("c1");

        assertThat(title).isEqualTo("会话");
        verify(convMapper, never()).updateById(any(AppConversation.class));
    }

    // ===== 停止 / 消息级委托 =====

    @Test
    void stopDelegatesToRegistry() {
        // [停止] 委托注册表取消
        login(user);
        when(convMapper.selectById("c1")).thenReturn(conversation("c1", "u1", null));
        when(stopRegistry.cancel("c1")).thenReturn(true);

        assertThat(service.stop("c1")).isTrue();
    }

    @Test
    void messageOpsDelegateToLedgerWithNullAppId() {
        // [消息级] 直聊消息操作委托账本（appId 参数遗留 no-op，债务 #4 补口）
        login(user);
        when(convMapper.selectById("c1")).thenReturn(conversation("c1", "u1", null));

        service.deleteMessage("c1", "m1");
        service.feedbackMessage("c1", "m1", "like");
        service.updateMessage("c1", "m1", "新内容");

        verify(appService).deleteMessage(null, "m1");
        verify(appService).feedbackMessage(null, "m1", "like");
        verify(appService).updateMessage(null, "m1", "新内容");
    }

    @Test
    void listPageSizeClamped() {
        // 分页边界：page<1 归 1、size 上限 100
        login(user);
        when(convMapper.selectCount(any())).thenReturn(0L);
        when(convMapper.selectList(any())).thenReturn(List.of());

        PageResult<ConversationDTO> r = service.listConversations(0, 999);

        assertThat(r.getPage()).isEqualTo(1);
        assertThat(r.getPageSize()).isEqualTo(100);
        assertThat(IntStream.of(1)).hasSize(1); // 占位断言保持 import 收敛
    }
}
