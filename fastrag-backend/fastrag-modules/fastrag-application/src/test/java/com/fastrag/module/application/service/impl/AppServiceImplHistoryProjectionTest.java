package com.fastrag.module.application.service.impl;

import com.fastrag.module.application.entity.AppConversationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R1（P0-6）历史注入最小恢复版：AppServiceImpl 侧历史投影。
 * <ul>
 *   <li>历史消息（旧→新）投影为 [{role,content}] 列表</li>
 *   <li>当前轮用户消息（列表末尾，streamChat 存库后查出的那条）不进入历史投影</li>
 *   <li>字符预算（默认 6000）：超限裁最旧，保留最近轮次</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AppServiceImplHistoryProjectionTest {

    @InjectMocks
    private AppServiceImpl service;

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> project(List<AppConversationMessage> msgs, int budget) {
        return (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service, "projectHistory", msgs, budget);
    }

    private AppConversationMessage message(String role, String content) {
        AppConversationMessage m = new AppConversationMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    void projectHistory_returnsRoleContentPairsInChronologicalOrder() {
        // 列表末尾为 assistant 时无当前轮剔除，全量投影
        List<AppConversationMessage> msgs = List.of(
                message("user", "年假政策是什么"),
                message("assistant", "每年 10 天。"),
                message("user", "病假呢"),
                message("assistant", "病假按医嘱批准。"));

        List<Map<String, Object>> projected = project(msgs, 6000);

        assertThat(projected).hasSize(4);
        assertThat(projected.get(0).get("role")).isEqualTo("user");
        assertThat(projected.get(0).get("content")).isEqualTo("年假政策是什么");
        assertThat(projected.get(1).get("role")).isEqualTo("assistant");
        assertThat(projected.get(2).get("content")).isEqualTo("病假呢");
        assertThat(projected.get(3).get("content")).isEqualTo("病假按医嘱批准。");
    }

    @Test
    void projectHistory_excludesCurrentTurnUserMessageAtTail() {
        List<AppConversationMessage> msgs = new ArrayList<>(List.of(
                message("user", "上一轮问题"),
                message("assistant", "上一轮回答"),
                message("user", "本轮刚存库的当前问题")));

        List<Map<String, Object>> projected = project(msgs, 6000);

        assertThat(projected).hasSize(2);
        // 当前轮消息由引擎作为 user 消息构建，不得在历史块重复
        assertThat(projected).noneMatch(m -> "本轮刚存库的当前问题".equals(m.get("content")));
    }

    @Test
    void projectHistory_overBudget_trimsOldestFirst() {
        List<AppConversationMessage> msgs = List.of(
                message("user", "最老的一轮，应该被裁掉"),
                message("assistant", "中间轮回答"),
                message("user", "最新一轮问题"),
                message("assistant", "最新一轮回答"));

        // 预算按渲染长度（role + ": " + content + "\n"）计，只够装下最新一问一答
        int budget = "user: 最新一轮问题\nassistant: 最新一轮回答\n".length();

        List<Map<String, Object>> projected = project(msgs, budget);

        assertThat(projected).hasSize(2);
        assertThat(projected.get(0).get("content")).isEqualTo("最新一轮问题");
        assertThat(projected.get(1).get("content")).isEqualTo("最新一轮回答");
    }

    @Test
    void projectHistory_emptyOrBlankEntries_skipped() {
        // 末尾为 assistant，无当前轮剔除
        List<AppConversationMessage> msgs = List.of(
                message("assistant", "   "),
                message("user", "有效消息"),
                message("assistant", "已回复"));

        List<Map<String, Object>> projected = project(msgs, 6000);

        assertThat(projected).hasSize(2);
        assertThat(projected.get(0).get("content")).isEqualTo("有效消息");
        assertThat(projected.get(1).get("content")).isEqualTo("已回复");
    }
}
