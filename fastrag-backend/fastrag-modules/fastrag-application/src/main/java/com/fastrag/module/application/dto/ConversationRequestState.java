package com.fastrag.module.application.dto;

import com.fastrag.common.exception.BusinessException;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 会话请求态（new-conversation）：会话记住的最后一次对话输入范围。
 *
 * <p>单一形状两用（design §1 SessionRequestStateStoreModule）：既是对话请求体携带的
 * 本轮覆盖，也是持久化在 {@code app_conversation.request_state} 的形状；两者字段集恒等，
 * 禁止演化出第二套形状。toolIds/mcpIds/skillIds/webSearchEnabled 为 B02 死字段——仅存储
 * 不消费，解析器（DialogPresetResolver）禁止读取（data.md「死字段清单的唯一证词」）。
 * skillNames 为 F1 @提及技能活字段（2026-10-07 裁决）：消费点在会话执行核
 * （AppServiceImpl 写入 runtimeState.pinnedSkillNames → agent 侧 SkillsMiddleware 施加
 * must_use 强提示），解析器同样禁止读取。
 */
@Data
public class ConversationRequestState {

    /** 知识库范围上限（超出抛业务错误，不静默截断） */
    public static final int KB_IDS_MAX = 50;
    /** 工具/MCP/技能列表上限（仅存字段同样设界） */
    public static final int LIST_MAX = 100;
    /** @提及技能点名上限（F1 裁决：与 kbIds 同款契约校验——超出抛业务错误，不静默截断） */
    public static final int SKILL_NAMES_MAX = 10;

    /** 旧通道哨兵：仅应用预设源 + 内置缺省，忽略一切请求态字段（contract PRESET_ONLY） */
    public static final ConversationRequestState PRESET_ONLY = ofPresetOnly();

    /** 哨兵标记；正常请求恒为 false */
    private boolean presetOnly;
    /** 挂载应用（App 预设）；空 = 知识库直聊 */
    private String appId;
    /** 挂载开关（对齐 WeKnora agent_enabled 旋钮语义） */
    private Boolean appEnabled = Boolean.TRUE;
    /** 知识库范围（直聊检索范围；挂预设时不消费——D02 互斥） */
    private List<String> kbIds = new ArrayList<>();
    /** 模型（C05 五级回退最高优先源） */
    private String modelId;
    /** 直聊消费（B02 演进 2026-09-25：装配进 BaseContext.tools，与应用绑定同路径） */
    private List<String> toolIds = new ArrayList<>();
    /** 直聊消费（同上，装配 mcps） */
    private List<String> mcpIds = new ArrayList<>();
    /** 直聊消费（同上，装配 skills） */
    private List<String> skillIds = new ArrayList<>();
    /** 仅存不消费（web 搜索能力后补） */
    private Boolean webSearchEnabled = Boolean.FALSE;
    /**
     * F1 @提及技能（2026-10-07 裁决）：用户在输入框 @ 点名的技能（名称或 slug，@ 下拉数据源
     * 为 accessible 列表，二者皆是合法点名形态）。本轮对话对所选技能施加 must_use 强提示——
     * pinned 不收窄白名单、不新增能力（集外点名静默忽略并 warn）；持久化供前端重开会话回显。
     * 消费链：执行核写入 runtimeState.pinnedSkillNames → SkillsMiddleware 与 promptSkills 交集。
     */
    private List<String> skillNames = new ArrayList<>();

    private static ConversationRequestState ofPresetOnly() {
        ConversationRequestState s = new ConversationRequestState();
        s.presetOnly = true;
        return s;
    }

    /** 是否直聊（无挂载应用） */
    public boolean isDirectChat() {
        return !presetOnly && (appId == null || appId.isBlank());
    }

    /**
     * 契约校验（data.md ConversationRequestState）：去重 + 上限。
     * 超限抛业务错误，不静默截断。
     */
    public void validate() {
        if (kbIds == null) kbIds = new ArrayList<>();
        kbIds = new ArrayList<>(new LinkedHashSet<>(kbIds));
        if (kbIds.size() > KB_IDS_MAX) {
            throw BusinessException.badRequest("知识库数量超限（最多 " + KB_IDS_MAX + " 个）");
        }
        toolIds = capped(toolIds);
        mcpIds = capped(mcpIds);
        skillIds = capped(skillIds);
        if (skillNames == null) skillNames = new ArrayList<>();
        skillNames = new ArrayList<>(new LinkedHashSet<>(skillNames));
        if (skillNames.size() > SKILL_NAMES_MAX) {
            throw BusinessException.badRequest("点名技能数量超限（最多 " + SKILL_NAMES_MAX + " 个）");
        }
    }

    private static List<String> capped(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> out = new ArrayList<>(new LinkedHashSet<>(list));
        if (out.size() > LIST_MAX) {
            throw BusinessException.badRequest("能力列表超限（最多 " + LIST_MAX + " 个）");
        }
        return out;
    }
}
