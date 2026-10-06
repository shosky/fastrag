package com.fastrag.module.application.service.session;

import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.AppConversation;
import com.fastrag.module.application.mapper.AppConversationMapper;
import com.fastrag.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S5 会话请求态形状属主（tdd/new-conversation.md S5；data.md ConversationRequestState）。
 */
@ExtendWith(MockitoExtension.class)
class SessionRequestStateStoreTest {

    @Mock
    private AppConversationMapper convMapper;

    @InjectMocks
    private SessionRequestStateStore store;

    @Test
    void nullAndMalformedJsonDegradeToEmptyState() {
        // [容错] NULL 与坏 JSON 均还原空请求态，不抛
        assertThat(store.parse(null).getKbIds()).isEmpty();
        assertThat(store.parse("").getModelId()).isNull();
        assertThat(store.parse("not-json")).isNotNull();
        assertThat(store.parse("{\"kbIds\":").getKbIds()).isEmpty();
    }

    @Test
    void loadReturnsEmptyStateForMissingConversation() {
        // 存量/已删会话降级（REQ-G03）
        when(convMapper.selectById("missing")).thenReturn(null);

        ConversationRequestState state = store.load("missing");

        assertThat(state).isNotNull();
        assertThat(state.isPresetOnly()).isFalse();
        assertThat(state.getKbIds()).isEmpty();
    }

    @Test
    void persistThenLoadRoundTripsFullShapeIncludingDeadFields() {
        // [对称] persist→load 往返等值，死字段保留（B02「唯一证词」断言）
        when(convMapper.selectById("c1")).thenReturn(new AppConversation());
        ConversationRequestState state = new ConversationRequestState();
        state.setKbIds(List.of("kb-1", "kb-2"));
        state.setModelId("model-x");
        state.setToolIds(List.of("t-1"));
        state.setMcpIds(List.of("m-1"));
        state.setSkillIds(List.of("s-1"));
        state.setWebSearchEnabled(Boolean.TRUE);

        store.persistOnSuccess("c1", state);

        ArgumentCaptor<AppConversation> captor = ArgumentCaptor.forClass(AppConversation.class);
        verify(convMapper).updateById(captor.capture());
        String json = captor.getValue().getRequestState();
        assertThat(json).contains("toolIds").contains("webSearchEnabled");

        ConversationRequestState reloaded = store.parse(json);
        assertThat(reloaded).isEqualTo(state);
    }

    @Test
    void persistRejectsKbIdsOverLimit() {
        // [形状] kbIds 上限 50 拒绝（不静默截断）
        ConversationRequestState state = new ConversationRequestState();
        state.setKbIds(java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "kb-" + i).toList());

        assertThatThrownBy(() -> store.persistOnSuccess("c1", state))
                .isInstanceOf(BusinessException.class);
        verify(convMapper, org.mockito.Mockito.never()).updateById(any(AppConversation.class));
    }

    @Test
    void persistSkipsSilentlyWhenConversationGone() {
        // 会话已被删：静默跳过（contract persistOnSuccess）
        when(convMapper.selectById("gone")).thenReturn(null);

        store.persistOnSuccess("gone", new ConversationRequestState());

        verify(convMapper, org.mockito.Mockito.never()).updateById(any(AppConversation.class));
    }
}
