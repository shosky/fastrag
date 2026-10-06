package com.fastrag.module.application.service.preset;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.session.SessionRequestStateStore;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.util.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话预设解析（new-conversation 深核心，contract new-conversation.DialogPresetResolver）：
 * 把〔应用预设 │ 会话请求态 │ 内置缺省〕三源解析为单一 {@link EffectiveDialogConfig}。
 *
 * <p>关键不变式（契约）：
 * <ul>
 *   <li>D02 互斥由结构保证——挂载应用分支不读取 turnState.kbIds；</li>
 *   <li>B03 逐 KB ACL 过滤覆盖两来源（请求态/应用绑定），但仅新通道——
 *       PRESET_ONLY 哨兵（旧通道）不过滤，G01 行为不变优先；</li>
 *   <li>B03 逐 KB ACL 过滤覆盖两来源（请求态/应用绑定），但仅新通道——
 *       PRESET_ONLY 哨兵（旧通道）不过滤，G01 行为不变优先；</li>
 *   <li>webSearchEnabled 不进入有效配置；tool/mcp/skill ID 于直聊装配进 Bindings
 *       （B02 演进，用户裁决 2026-09-25），挂载应用分支仍以应用绑定为唯一来源（D02）；</li>
 *   <li>PRESET_ONLY 产物与现行编排器内联配置读取逐字段等价（G01 哨兵等价性）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogPresetResolver {

    private final AppMapper appMapper;
    private final AppConfigMapper configMapper;
    private final AppBasicConfigMapper basicConfigMapper;
    private final AppGlobalPolicyMapper globalPolicyMapper;
    private final AppKbBindingMapper kbBindingMapper;
    private final AppToolBindingMapper toolBindingMapper;
    private final AppSkillBindingMapper skillBindingMapper;
    private final AppMcpBindingMapper mcpBindingMapper;
    private final AppDbBindingMapper dbBindingMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final KbAccessChecker kbAccessChecker;
    private final SessionRequestStateStore requestStateStore;

    /**
     * 唯一入口：三源 → 有效配置。
     *
     * @param conversation 目标会话（request_state 可空 = 存量会话降级）
     * @param turnState    本轮请求态；{@link ConversationRequestState#PRESET_ONLY} 供旧通道
     * @param user         当前用户（逐 KB ACL 主体）
     */
    public EffectiveDialogConfig resolve(AppConversation conversation,
                                         ConversationRequestState turnState,
                                         LoginUser user) {
        boolean presetOnly = turnState == null || turnState.isPresetOnly();
        ConversationRequestState persisted =
                requestStateStore.parse(conversation == null ? null : conversation.getRequestState());

        // 挂载判定：旧通道取会话归属应用；新通道以本轮 turnState.appId 为权威（整包覆盖语义）
        String mountedAppId;
        if (presetOnly) {
            mountedAppId = conversation == null ? null : conversation.getAppId();
        } else {
            turnState.validate();
            mountedAppId = StrUtil.isNotBlank(turnState.getAppId()) ? turnState.getAppId() : null;
        }

        if (StrUtil.isNotBlank(mountedAppId)) {
            return resolveAppPreset(conversation, mountedAppId, turnState, presetOnly, user);
        }
        return resolveDirectChat(conversation, turnState, persisted, user);
    }

    // ==================== 应用预设源（D01：与现行编排器配置读取逐字段等价） ====================

    private EffectiveDialogConfig resolveAppPreset(AppConversation conversation, String appId,
                                                   ConversationRequestState turnState, boolean presetOnly,
                                                   LoginUser user) {
        App app = appMapper.selectById(appId);
        if (app == null) throw BusinessException.notFound("应用不存在");
        if (!DataScope.visible(user, app.getOwner(), app.getOrgId(), null)) {
            throw BusinessException.forbidden("无权访问该应用");
        }

        AppConfig appConfig = configMapper.selectOne(
                new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId, appId));
        AppBasicConfig basicConfig = basicConfigMapper.selectOne(
                new LambdaQueryWrapper<AppBasicConfig>().eq(AppBasicConfig::getAppId, appId));

        // 模型五级回退的前两级：请求态模型（新通道，C05×D01 交界——请求态优先）→ app_config.model
        String modelCode = null;
        if (!presetOnly && turnState != null && StrUtil.isNotBlank(turnState.getModelId())) {
            modelCode = turnState.getModelId();
        }
        if (StrUtil.isBlank(modelCode) && appConfig != null && StrUtil.isNotBlank(appConfig.getModel())) {
            modelCode = appConfig.getModel();
        }
        // 第三级：basic.advancedOptions JSON 的 model 段
        if (StrUtil.isBlank(modelCode) && basicConfig != null && StrUtil.isNotBlank(basicConfig.getAdvancedOptions())) {
            modelCode = advancedOptionsModel(basicConfig.getAdvancedOptions());
        }

        int memoryRounds = (basicConfig != null && basicConfig.getMemoryRounds() != null)
                ? basicConfig.getMemoryRounds() : DialogDefaults.MEMORY_ROUNDS;

        // D02 互斥：kbScope 一律取应用绑定；仅新通道做 ACL 过滤（旧通道 G01 不过滤）
        List<String> kbIds = kbBindingMapper.selectList(new LambdaQueryWrapper<AppKbBinding>()
                        .eq(AppKbBinding::getAppId, appId).eq(AppKbBinding::getEnabled, 1))
                .stream().map(AppKbBinding::getKbId).toList();
        List<String> kbScope = presetOnly ? kbIds : filterAccessible(kbIds, user);

        return EffectiveDialogConfig.builder()
                .kbScope(kbScope)
                .bindings(bindings(appId))
                .modelRoute(resolveModelRoute(modelCode))
                .systemPrompt((appConfig != null && StrUtil.isNotBlank(appConfig.getPrompt()))
                        ? appConfig.getPrompt() : DialogDefaults.SYSTEM_PROMPT)
                .temperature((appConfig != null && appConfig.getTemperature() != null)
                        ? appConfig.getTemperature().doubleValue() : DialogDefaults.TEMPERATURE)
                .maxTokens((appConfig != null && appConfig.getMaxTokens() != null)
                        ? appConfig.getMaxTokens() : DialogDefaults.MAX_TOKENS)
                .memoryRounds(memoryRounds)
                .summaryThreshold(appConfig != null ? appConfig.getSummaryThreshold() : null)
                .modelRetryTimes(appConfig != null ? appConfig.getRetryTimes() : null)
                .maxSteps(appConfig != null ? appConfig.getMaxSteps() : null)
                .summaryPrompt(appConfig != null ? appConfig.getSummaryPrompt() : null)
                .safetyEnabled(isSafetyEnabled(appId))
                .greeting(basicConfig != null ? basicConfig.getGreeting() : null)
                .source(EffectiveDialogConfig.PresetSource.APP_PRESET)
                .mountedAppId(appId)
                .build();
    }

    // ==================== 知识库直聊源（REQ-C01：范围取请求态，其余内置缺省） ====================

    private EffectiveDialogConfig resolveDirectChat(AppConversation conversation,
                                                    ConversationRequestState turnState,
                                                    ConversationRequestState persisted,
                                                    LoginUser user) {
        // 本轮覆盖优先；本轮缺省（新通道理论不发生，防御）回落持久化形状
        ConversationRequestState effective =
                (turnState != null && !turnState.isPresetOnly()) ? turnState : persisted;

        List<String> kbScope = filterAccessible(
                effective == null ? List.of() : effective.getKbIds(), user);

        // B02 演进（用户裁决 2026-09-25）：直聊消费工具/MCP/技能 ID（装配进 BaseContext，与
        // 应用绑定同路径）；db 不开放直聊；webSearchEnabled 仍仅存。缺失/为空按空集处理。
        EffectiveDialogConfig.Bindings bindings = EffectiveDialogConfig.Bindings.builder()
                .tools(effective != null && effective.getToolIds() != null
                        ? effective.getToolIds() : List.of())
                .skills(effective != null && effective.getSkillIds() != null
                        ? effective.getSkillIds() : List.of())
                .mcps(effective != null && effective.getMcpIds() != null
                        ? effective.getMcpIds() : List.of())
                .databases(List.of())
                .build();

        return EffectiveDialogConfig.builder()
                .kbScope(kbScope)
                .bindings(bindings)
                .modelRoute(resolveModelRoute(effective == null ? null : effective.getModelId()))
                .systemPrompt(DialogDefaults.SYSTEM_PROMPT)
                .temperature(DialogDefaults.TEMPERATURE)
                .maxTokens(DialogDefaults.MAX_TOKENS)
                .memoryRounds(DialogDefaults.MEMORY_ROUNDS)
                .summaryThreshold(null).modelRetryTimes(null).maxSteps(null).summaryPrompt(null)
                .safetyEnabled(true)
                .greeting(null)
                .source(EffectiveDialogConfig.PresetSource.DIRECT_CHAT)
                .mountedAppId(null)
                .build();
    }

    // ==================== 私有实现 ====================

    /** 五级模型回退的后段：online 记录 → 默认网关（现行语义迁自 AppServiceImpl:450-475） */
    private EffectiveDialogConfig.ModelRoute resolveModelRoute(String modelCode) {
        String model = DialogDefaults.DEFAULT_MODEL;
        String apiUrl = null;
        String apiKey = null;
        boolean enableThinking = false;
        if (StrUtil.isNotBlank(modelCode)) {
            model = modelCode;
            ModelRecord record = modelRecordMapper.selectOne(new LambdaQueryWrapper<ModelRecord>()
                    .eq(ModelRecord::getCode, modelCode)
                    .eq(ModelRecord::getStatus, "online")
                    .last("LIMIT 1"));
            if (record != null) {
                if (StrUtil.isNotBlank(record.getApiUrl())) apiUrl = record.getApiUrl();
                if (StrUtil.isNotBlank(record.getApiKeyRef())) apiKey = record.getApiKeyRef();
                enableThinking = Boolean.TRUE.equals(record.getEnableThinking());
            } else {
                log.warn("[DialogPresetResolver] model not found or offline: code={}, will use default gateway", modelCode);
            }
        }
        return EffectiveDialogConfig.ModelRoute.builder()
                .model(model).apiUrl(apiUrl).apiKey(apiKey).enableThinking(enableThinking).build();
    }

    /** B03 逐 KB ACL 过滤（viewer 即可对话）；无权跳过 + warn，不中断（调用方继续其余 KB） */
    private List<String> filterAccessible(List<String> kbIds, LoginUser user) {
        List<String> out = new ArrayList<>();
        if (kbIds == null) return out;
        for (String kbId : kbIds) {
            if (StrUtil.isBlank(kbId)) continue;
            if (kbAccessChecker.resolveRole(kbId, user) != null) {
                out.add(kbId);
            } else {
                log.warn("[DialogPresetResolver] KB skipped for lack of access: kbId={}, user={}",
                        kbId, user.getUserId());
            }
        }
        return out;
    }

    private EffectiveDialogConfig.Bindings bindings(String appId) {
        return EffectiveDialogConfig.Bindings.builder()
                .tools(toolBindingMapper.selectList(new LambdaQueryWrapper<AppToolBinding>()
                                .eq(AppToolBinding::getAppId, appId).eq(AppToolBinding::getEnabled, 1))
                        .stream().map(AppToolBinding::getToolId).toList())
                .skills(skillBindingMapper.selectList(new LambdaQueryWrapper<AppSkillBinding>()
                                .eq(AppSkillBinding::getAppId, appId).eq(AppSkillBinding::getEnabled, 1))
                        .stream().map(AppSkillBinding::getSkillId).toList())
                .mcps(mcpBindingMapper.selectList(new LambdaQueryWrapper<AppMcpBinding>()
                                .eq(AppMcpBinding::getAppId, appId).eq(AppMcpBinding::getEnabled, 1))
                        .stream().map(AppMcpBinding::getMcpServiceId).toList())
                .databases(dbBindingMapper.selectList(new LambdaQueryWrapper<AppDbBinding>()
                                .eq(AppDbBinding::getAppId, appId).eq(AppDbBinding::getEnabled, 1))
                        .stream().map(AppDbBinding::getDbId).toList())
                .build();
    }

    /** 安全开关：未配置记录/字段空默认开启（同 AppServiceImpl:85-90 口径） */
    private boolean isSafetyEnabled(String appId) {
        AppGlobalPolicy policy = globalPolicyMapper.selectOne(
                new LambdaQueryWrapper<AppGlobalPolicy>().eq(AppGlobalPolicy::getAppId, appId).last("LIMIT 1"));
        return policy == null || policy.getSafetyEnabled() == null || policy.getSafetyEnabled() == 1;
    }

    /** advancedOptions JSON 的 model 段（现行 :440-448 同构；坏 JSON 容错为空） */
    private String advancedOptionsModel(String advancedOptions) {
        try {
            JSONObject adv = JSONUtil.parseObj(advancedOptions);
            Object model = adv.get("model");
            if (model != null && StrUtil.isNotBlank(model.toString())) return model.toString();
        } catch (Exception ignored) {
        }
        return null;
    }
}
