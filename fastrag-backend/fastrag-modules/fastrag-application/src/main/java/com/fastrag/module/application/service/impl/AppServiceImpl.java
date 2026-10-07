package com.fastrag.module.application.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.executor.AgentEngine;
import com.fastrag.module.agent.executor.ModelConfig;
import com.fastrag.module.agent.mapper.SandboxConfigMapper;
import com.fastrag.module.agent.middleware.HistoryMiddleware;
import com.fastrag.module.application.dto.AppSessionDTO;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.AppService;
import com.fastrag.module.application.service.preset.DialogDefaults;
import com.fastrag.module.application.service.preset.DialogPresetResolver;
import com.fastrag.module.application.service.preset.EffectiveDialogConfig;
import com.fastrag.module.application.service.session.SessionRequestStateStore;
import com.fastrag.module.application.service.session.StopRegistry;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.SensitiveWordService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.DataScope;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    /** 沙箱能力提示：应用绑定沙箱配置时追加进 system prompt（工具恒注册但模型无从得知沙箱可用，见 2026-09-30 排查）。
     * 示例路径必须在 /workspace 下——工作区守卫拒收外部路径，用 /tmp 作示例曾把模型带偏 */
    private static final String SANDBOX_CAPABILITY_HINT =
            "\n\n【沙箱已启用】本应用接入了代码执行沙箱：需要运行代码、执行命令或处理文件时，请主动使用沙箱工具——"
                    + "先用 write_file 把代码写入沙箱（路径必须在 /workspace 下，如 /workspace/main.py），"
                    + "再用 execute 执行（例如 `python3 /workspace/main.py`），并基于命令输出来回答用户。";

    /** R1（P0-6）：会话历史投影字符预算，超限裁最旧 */
    private static final int HISTORY_BUDGET_CHARS = 6000;

    /** R2（P0-5）：请求级 KB 句柄注册表键（cN → {kbId,fileId,chunkIndex}）。
     * AgentEngine 将 runtimeState 浅拷贝进 ToolContext.extensions——嵌套 Map 是共享引用，
     * KnowledgeToolExecutor 就地写入即请求级可见（结构化通道另保留真实 id）。 */
    static final String KB_HANDLE_MAP_KEY = "kbHandleMap";

    /** R2：句柄编号游标（AtomicInteger，跨同一请求内多次 query_kb 连续编号，避免 cN 冲突覆盖） */
    static final String KB_HANDLE_SEQ_KEY = "kbHandleSeq";

    private final AppMapper appMapper;
    private final AppConfigMapper configMapper;
    private final AppTemplateMapper tplMapper;
    private final AppConversationMapper convMapper;
    private final AppConversationMessageMapper convMsgMapper;
    private final AppKbBindingMapper kbBindingMapper;
    private final AppBasicConfigMapper basicConfigMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final AppToolBindingMapper toolBindingMapper;
    private final AppSkillBindingMapper skillBindingMapper;
    private final AppMcpBindingMapper mcpBindingMapper;
    private final AppDbBindingMapper dbMapper;
    private final LlmService llmService;
    private final AgentEngine agentEngine;
    private final SensitiveWordService sensitiveWordService;
    private final AppGlobalPolicyMapper globalPolicyMapper;
    private final DialogPresetResolver presetResolver;
    private final StopRegistry stopRegistry;
    private final SessionRequestStateStore requestStateStore;
    /** 沙箱配置存在性校验与对话链注入（REQ-H03 分支②，2026-09-28 沙箱实装） */
    private final SandboxConfigMapper sandboxConfigMapper;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    // ==================== 原有 CRUD 方法 ====================

    /** 应用可见性：属主 / 同组织 / 系统级(owner=system) / API Token（与知识库组织模型一致） */
    private boolean visible(App app, LoginUser user) {
        return DataScope.visible(user, app.getOwner(), app.getOrgId(), null);
    }

    private void requireVisible(App app) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (app == null) throw BusinessException.notFound("应用不存在");
        if (!visible(app, user)) throw BusinessException.forbidden("无权访问该应用");
    }

    private void requireManage(App app) {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (app == null) throw BusinessException.notFound("应用不存在");
        if (!DataScope.manageable(user, app.getOwner())) throw BusinessException.forbidden("无权管理该应用");
    }

    /** 应用「安全策略启用」开关：未配置或字段为空时默认开启（安全默认） */
    private boolean isSafetyEnabled(String appId) {
        AppGlobalPolicy policy = globalPolicyMapper.selectOne(
                new LambdaQueryWrapper<AppGlobalPolicy>().eq(AppGlobalPolicy::getAppId, appId).last("LIMIT 1"));
        return policy == null || policy.getSafetyEnabled() == null || policy.getSafetyEnabled() == 1;
    }

    /** 会话归属校验：仅会话属主可访问（API Token 除外） */
    private AppConversation requireOwnConversation(String appId, String sessionId) {
        AppConversation conv = convMapper.selectOne(
                new LambdaQueryWrapper<AppConversation>()
                        .eq(AppConversation::getAppId, appId)
                        .eq(AppConversation::getSessionId, sessionId));
        if (conv == null) throw BusinessException.notFound("会话不存在");
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user) && !user.getUserId().equals(conv.getUserId())) {
            throw BusinessException.forbidden("无权访问该会话");
        }
        return conv;
    }

    /**
     * 会话 ID 主键定位 + 归属校验（new-conversation：app_id 可空后的定位键迁移不变量）。
     * 新旧通道共用；直聊会话（app_id=NULL）仅可经此定位。
     */
    private AppConversation requireOwnConversationById(String conversationId, LoginUser user) {
        AppConversation conv = convMapper.selectById(conversationId);
        if (conv == null) throw BusinessException.notFound("会话不存在");
        if (!DataScope.isApiToken(user) && !user.getUserId().equals(conv.getUserId())) {
            throw BusinessException.forbidden("无权访问该会话");
        }
        return conv;
    }

    /** 消息归属校验：消息所属会话必须属于当前用户 */
    private void requireOwnMessage(String messageId) {
        AppConversationMessage msg = convMsgMapper.selectById(messageId);
        if (msg == null) throw BusinessException.notFound("消息不存在");
        AppConversation conv = convMapper.selectById(msg.getConversationId());
        if (conv == null) throw BusinessException.notFound("会话不存在");
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user) && !user.getUserId().equals(conv.getUserId())) {
            throw BusinessException.forbidden("无权访问该消息");
        }
    }

    @Override
    public List<App> list(String kw, String tag) {
        var w = new LambdaQueryWrapper<App>();
        if (StrUtil.isNotBlank(kw)) w.like(App::getName, kw);
        if (StrUtil.isNotBlank(tag)) w.like(App::getTags, tag);
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user)) {
            // 属主 / 同组织 / 系统级（owner=system）
            w.and(q -> q.eq(App::getOwner, user.getUserId())
                    .or(o -> o.eq(App::getOrgId, user.getOrgId()).or().eq(App::getOwner, "system")));
        }
        return appMapper.selectList(w.orderByDesc(App::getCreatedAt));
    }

    @Override
    public App get(String id) {
        App app = appMapper.selectById(id);
        requireVisible(app);
        return app;
    }

    @Override
    public App create(Map<String, Object> f) {
        LoginUser user = SecurityUtil.getCurrentUser();
        var a = new App();
        a.setName((String) f.get("name"));
        a.setDescription((String) f.get("description"));
        a.setType((String) f.getOrDefault("type", "ChatBot"));
        a.setStatus("draft");
        a.setOwner(user.getUserId());
        a.setOrgId(user.getOrgId());
        a.setSandboxConfigId(requireSandboxConfigExists((String) f.get("sandboxConfigId")));
        appMapper.insert(a);
        return a;
    }

    @Override
    public App update(String id, Map<String, Object> f) {
        var a = appMapper.selectById(id);
        requireManage(a);
        if (a != null) {
            if (f.containsKey("name")) a.setName((String) f.get("name"));
            if (f.containsKey("description")) a.setDescription((String) f.get("description"));
            if (f.containsKey("sandboxConfigId")) {
                // 传空串/null 表示解除引用（回到分支②降级）；非空须存在
                a.setSandboxConfigId(requireSandboxConfigExists((String) f.get("sandboxConfigId")));
            }
            appMapper.updateById(a);
        }
        return a;
    }

    /** 沙箱引用存在性校验：空放行（=未配置，对话走降级分支②）；非空须命中 sandbox_config 表。 */
    private String requireSandboxConfigExists(String sandboxConfigId) {
        if (StrUtil.isBlank(sandboxConfigId)) {
            return null;
        }
        if (sandboxConfigMapper.selectById(sandboxConfigId.trim()) == null) {
            throw BusinessException.badRequest("沙箱配置不存在: " + sandboxConfigId);
        }
        return sandboxConfigId.trim();
    }

    @Override
    public void delete(String id) {
        var a = appMapper.selectById(id);
        requireManage(a);
        appMapper.deleteById(id);
    }

    @Override
    public List<AppTemplate> getTemplates() {
        return tplMapper.selectList(null);
    }

    @Override
    public AppConfig getConfig(String id) {
        App app = appMapper.selectById(id);
        requireVisible(app);
        return configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId, id));
    }

    @Override
    public AppConfig saveConfig(String id, AppConfig config) {
        App app = appMapper.selectById(id);
        requireManage(app);
        config.setAppId(id);
        var existing = configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId, id));
        if (existing != null) {
            config.setId(existing.getId());
            configMapper.updateById(config);
        } else {
            configMapper.insert(config);
        }
        return config;
    }

    // ==================== 旧版关键词匹配（兼容保留） ====================

    @Override
    @Deprecated
    public Map<String, Object> run(String id, String query) {
        App app = appMapper.selectById(id);
        requireVisible(app);
        var r = new HashMap<String, Object>();
        r.put("sessionId", UUID.randomUUID().toString());
        r.put("answer", matchAnswer(query));
        return r;
    }

    private String matchAnswer(String query) {
        if (StrUtil.isBlank(query)) return "请输入您的问题，我会尽力为您解答。";
        String lower = query.toLowerCase().trim();
        String bestAnswer = null;
        int bestScore = 0;
        for (String[] rule : QA_RULES) {
            String[] keywords = rule[0].split(",");
            int score = 0;
            for (String kw : keywords) {
                if (lower.contains(kw.trim().toLowerCase())) score += kw.length();
            }
            if (score > bestScore) {
                bestScore = score;
                bestAnswer = rule[1];
            }
        }
        if (bestAnswer != null) return bestAnswer;
        if (lower.contains("?") || lower.contains("？") || lower.contains("吗") || lower.contains("怎么") || lower.contains("如何") || lower.contains("什么") || lower.contains("哪里") || lower.contains("能否")) {
            return "关于您提到的「" + query + "」，目前我的知识库中暂未收录相关内容。";
        }
        return "感谢您的提问。关于「" + query + "」，我目前暂时无法给出准确的回答。";
    }

    private static final List<String[]> QA_RULES = List.of(
        new String[]{"你好,您好,hi,hello", "您好！我是智能问答助手，请问有什么可以帮到您的？"},
        new String[]{"谢谢,感谢", "不客气！如果您还有其他问题，随时可以继续提问。"},
        new String[]{"你是谁", "我是企业智能问答助手，可以帮您查询公司各类规章制度和常见问题。"}
    );

    // ==================== 会话管理 ====================

    @Override
    public Map<String, Object> createSession(String appId, String userId, String userName) {
        App app = appMapper.selectById(appId);
        requireVisible(app);
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        AppConversation conv = new AppConversation();
        conv.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        conv.setAppId(appId);
        conv.setSessionId(sessionId);
        conv.setUserId(userId);
        conv.setUserName(userName);
        conv.setTitle("新对话");
        conv.setMessageCount(0);
        conv.setTokenCount(0);
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        convMapper.insert(conv);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("conversationId", conv.getId());
        result.put("title", conv.getTitle());
        result.put("createdAt", conv.getCreatedAt());
        return result;
    }

    @Override
    public List<AppSessionDTO> listSessions(String appId, String userId) {
        App app = appMapper.selectById(appId);
        requireVisible(app);
        LambdaQueryWrapper<AppConversation> w = new LambdaQueryWrapper<AppConversation>()
                .eq(AppConversation::getAppId, appId);
        // 如果指定了 userId，则只查看自己的会话
        if (StrUtil.isNotBlank(userId)) {
            w.eq(AppConversation::getUserId, userId);
        }
        w.orderByDesc(AppConversation::getUpdatedAt);
        List<AppConversation> conversations = convMapper.selectList(w);

        List<AppSessionDTO> result = new ArrayList<>();
        for (AppConversation conv : conversations) {
            AppSessionDTO dto = new AppSessionDTO();
            dto.setConversationId(conv.getId());
            dto.setSessionId(conv.getSessionId());
            dto.setTitle(conv.getTitle());
            dto.setFirstQuestion(conv.getFirstQuestion());
            dto.setAnswerSummary(conv.getAnswerSummary());
            dto.setMessageCount(conv.getMessageCount());
            dto.setTokenCount(conv.getTokenCount());
            dto.setCreatedAt(conv.getCreatedAt());
            dto.setUpdatedAt(conv.getUpdatedAt());
            result.add(dto);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getSessionMessages(String appId, String sessionId) {
        // 根据 sessionId 查找 conversation（含归属校验）
        AppConversation conv = requireOwnConversation(appId, sessionId);
        if (conv == null) return Collections.emptyList();

        List<AppConversationMessage> messages = convMsgMapper.selectList(
                new LambdaQueryWrapper<AppConversationMessage>()
                        .eq(AppConversationMessage::getConversationId, conv.getId())
                        .isNull(AppConversationMessage::getDeletedAt)
                        .orderByAsc(AppConversationMessage::getCreatedAt));

        List<Map<String, Object>> result = new ArrayList<>();
        for (AppConversationMessage msg : messages) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            m.put("tokens", msg.getTokens());
            m.put("latencyMs", msg.getLatencyMs());
            m.put("feedback", msg.getFeedback());
            m.put("thinkingContent", msg.getThinkingContent());
            m.put("toolCalls", msg.getToolCalls());
            m.put("iterations", msg.getIterations());
            m.put("references", msg.getReferences());
            m.put("createdAt", msg.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    @Override
    public void deleteSession(String appId, String sessionId) {
        AppConversation conv = requireOwnConversation(appId, sessionId);
        if (conv == null) return;
        // 删除消息
        convMsgMapper.delete(
                new LambdaQueryWrapper<AppConversationMessage>()
                        .eq(AppConversationMessage::getConversationId, conv.getId()));
        // 删除会话
        convMapper.deleteById(conv.getId());
    }

    @Override
    public void deleteMessage(String appId, String messageId) {
        requireOwnMessage(messageId);
        AppConversationMessage msg = convMsgMapper.selectById(messageId);
        if (msg == null) return;
        msg.setDeletedAt(java.time.LocalDateTime.now());
        convMsgMapper.updateById(msg);
    }

    @Override
    public void feedbackMessage(String appId, String messageId, String feedback) {
        requireOwnMessage(messageId);
        AppConversationMessage msg = convMsgMapper.selectById(messageId);
        if (msg == null) return;
        msg.setFeedback(feedback);
        convMsgMapper.updateById(msg);
    }

    @Override
    public void updateMessage(String appId, String messageId, String content) {
        requireOwnMessage(messageId);
        AppConversationMessage msg = convMsgMapper.selectById(messageId);
        if (msg == null) return;
        msg.setContent(content);
        convMsgMapper.updateById(msg);
    }

    // ==================== SSE 流式对话（双入口、单执行核） ====================

    /**
     * 旧通道对话（G01 行为不变）：壳 = 应用可见性 + 会话定位 + PRESET_ONLY 解析 + 委托共享执行核。
     * 禁止在本方法体内做任何配置读取——配置一律经 DialogPresetResolver（design §2 编排器扩展）。
     */
    @Override
    public SseEmitter runStream(String appId, String query, String sessionId, String userId, String userName) {
        App app = appMapper.selectById(appId);
        requireVisible(app);
        AppConversation conv = locateSessionForApp(appId, sessionId, userId, userName);
        EffectiveDialogConfig cfg = presetResolver.resolve(
                conv, ConversationRequestState.PRESET_ONLY, SecurityUtil.getCurrentUser());
        return executeDialog(conv, cfg, query, userId, null);
    }

    /** 新通道对话（new-conversation）：会话一等公民入口；turnState 为本轮请求态覆盖（空时回落持久化形状） */
    @Override
    public SseEmitter runStreamByConversation(String conversationId, String query,
                                              ConversationRequestState turnState, LoginUser user) {
        AppConversation conv = requireOwnConversationById(conversationId, user);
        if (turnState == null) turnState = requestStateStore.load(conversationId);
        EffectiveDialogConfig cfg = presetResolver.resolve(conv, turnState, user);
        return executeDialog(conv, cfg, query, user.getUserId(), turnState);
    }

    /** 旧通道会话定位/创建（原 runStream 步骤 2 原样提取，行为不变） */
    private AppConversation locateSessionForApp(String appId, String sessionId, String userId, String userName) {
        AppConversation conv = null;
        if (StrUtil.isNotBlank(sessionId)) {
            conv = requireOwnConversation(appId, sessionId);
        }
        if (conv == null) {
            Map<String, Object> sessionInfo = createSession(appId, userId, userName);
            String sid = (String) sessionInfo.get("sessionId");
            conv = convMapper.selectOne(
                    new LambdaQueryWrapper<AppConversation>()
                            .eq(AppConversation::getAppId, appId)
                            .eq(AppConversation::getSessionId, sid));
        }
        return conv;
    }

    /**
     * 共享执行核（新旧通道唯一对话执行路径）：只消费 EffectiveDialogConfig，不感知配置来源。
     *
     * @param persistState 新通道=本轮请求态（三落库点固化，REQ-B01）；旧通道=null（不固化）
     */
    private SseEmitter executeDialog(AppConversation conv, EffectiveDialogConfig cfg, String query,
                                     String userId, ConversationRequestState persistState) {
        final String sessionId = StrUtil.isNotBlank(conv.getSessionId()) ? conv.getSessionId() : conv.getId();

        // 敏感词输入拦截（直聊恒开＝安全缺省；挂应用＝应用开关，现行口径）：
        // 命中 blockInput 词条时直接拒绝，不保存用户消息、不产生模型调用
        final boolean finalSafetyFilterOn = cfg.isSafetyEnabled();
        Optional<String> blocked = finalSafetyFilterOn ? sensitiveWordService.checkInput(query) : Optional.empty();
        if (blocked.isPresent()) {
            try {
                SseEmitter emitter = new SseEmitter(60 * 1000L);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("content", blocked.get());
                data.put("blocked", true);
                data.put("sessionId", sessionId);
                emitter.send(SseEmitter.event().name("message").data(data));
                emitter.send(SseEmitter.event().name("end").data(data));
                emitter.complete();
                return emitter;
            } catch (Exception e) {
                throw new RuntimeException("Failed to send blocked-input reply", e);
            }
        }

        // 3. 保存用户消息
        AppConversationMessage userMsg = new AppConversationMessage();
        userMsg.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        userMsg.setConversationId(conv.getId());
        userMsg.setRole("user");
        userMsg.setContent(query);
        userMsg.setTokens(0);
        userMsg.setCreatedAt(LocalDateTime.now());
        convMsgMapper.insert(userMsg);

        // 更新会话的 firstQuestion 和 title（如果是第一条消息）
        if (conv.getMessageCount() == null || conv.getMessageCount() == 0) {
            conv.setFirstQuestion(query.length() > 200 ? query.substring(0, 200) : query);
            conv.setTitle(query.length() > 30 ? query.substring(0, 30) + "..." : query);
        }

        // 有效配置已由 DialogPresetResolver 解析（prompt/temperature/maxTokens/memoryRounds/model 路由
        // 全部来自 cfg；旧通道=应用预设源，直聊=内置缺省——此处零配置读取）

        // 5. 如果有问候语且是第一条消息，直接返回问候语
        String greeting = cfg.getGreeting();
        if (greeting != null && !greeting.isEmpty()
                && (conv.getMessageCount() == null || conv.getMessageCount() == 0)
                && isGreetingQuery(query)) {
            try {
                SseEmitter emitter = new SseEmitter(60 * 1000L);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("content", greeting);
                data.put("isGreeting", true);
                emitter.send(SseEmitter.event().name("message").data(data));
                emitter.send(SseEmitter.event().name("end").data(data));
                emitter.complete();

                // 保存问候语到消息记录
                saveAssistantMessage(conv, greeting, null, null, 0, java.util.List.of(), null);
                return emitter;
            } catch (Exception e) {
                throw new RuntimeException("Failed to send greeting", e);
            }
        }

        // 6. 立即创建 SSE Emitter 并返回，所有耗时操作放到异步线程
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 分钟超时
        StringBuilder fullAnswer = new StringBuilder();
        final String finalSessionId = sessionId;
        final AppConversation finalConv = conv;
        final long startTime = System.currentTimeMillis();
        final String finalUserId = userId;
        final java.util.concurrent.atomic.AtomicBoolean answerSaved =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<BaseContext> contextRef =
                new java.util.concurrent.atomic.AtomicReference<>();

        // 部分落库（超时/停止共用）：恰一次幂等 + 请求态固化（REQ-B01 三落库点之二）；迭代轮数尽力读取
        Runnable partialSave = () -> {
            // 部分保存时工具调用尚未定形（toolCallsJson 为 null），知识来源无从提取，落空列表
            persistAnswer(finalConv, cfg, fullAnswer.toString(), null, null,
                    (int) (System.currentTimeMillis() - startTime),
                    Collections.emptyList(),
                    readIterations(contextRef.get()), persistState, answerSaved);
        };

        // 停止注册表（new-conversation）：partialSave + 关流；取消后引擎 error 事件被抑制（NFR-03）
        StopRegistry.CancelHandle handle = new StopRegistry.CancelHandle(() -> {
            partialSave.run();
            emitter.complete();
        });
        stopRegistry.register(conv.getId(), handle);

        emitter.onCompletion(() -> {
            log.info("[AppChat] SSE completed: sessionId={}", finalSessionId);
            stopRegistry.unregister(conv.getId());
        });
        emitter.onTimeout(() -> {
            log.warn("[AppChat] SSE timeout: sessionId={}", finalSessionId);
            partialSave.run();
        });
        emitter.onError(e -> log.warn("[AppChat] SSE error: sessionId={}, error={}", finalSessionId, e.getMessage()));

        // 7. 异步执行：RAG 检索 + 构建消息 + 流式对话
        // 在提交前捕获 SecurityContext，传播到异步线程
        var securityContext = SecurityContextHolder.getContext();
        streamExecutor.execute(() -> {
            try {
                // 将 SecurityContext 传播到异步线程，供下游（如 recordLog）使用
                SecurityContextHolder.setContext(securityContext);
                // 全 agentic 化（2026-09-30）：移除无条件预检索与 RAG 提示块、sources 预取事件——
                // 知识资料改由模型按需调用 query_kb 获取（绑定 KB 时 KnowledgeBaseMiddleware 注入能力提示），
                // 检索来源经工具调用通道落库（extractKbSources）。代价是 KB 相关问题的首答延迟增加一轮工具往返，
                // 换来无关问题（如纯代码执行）不再产生检索延迟与噪音上下文。
                String effectiveSystemPrompt = cfg.getSystemPrompt();

                // 构建历史消息（R1/P0-6 复活：原为死代码，现投影进 runtimeState 供 HistoryMiddleware 注入）
                List<AppConversationMessage> historyMessages = convMsgMapper.selectList(
                        new LambdaQueryWrapper<AppConversationMessage>()
                                .eq(AppConversationMessage::getConversationId, finalConv.getId())
                                .orderByDesc(AppConversationMessage::getCreatedAt)
                                .last("LIMIT " + (cfg.getMemoryRounds() * 2)));
                Collections.reverse(historyMessages);

                // 构建 BaseContext（能力装配全部来自有效配置；直聊四空集）
                BaseContext context = new BaseContext();
                context.setUid(finalUserId);
                context.setSystemPrompt(effectiveSystemPrompt);
                context.setModel(cfg.getModelRoute().getModel());
                contextRef.set(context);

                // R1（P0-6）会话历史最小恢复版：最近 N 轮投影 [{role,content}] 存 runtimeState（字符预算内，超限裁最旧），
                // 由 HistoryMiddleware（order=12）作为 system prompt 附加块注入。
                // Deviation：真多轮 messages 注入待引擎侧立项；请求级句柄注册表（R2）同处预填。
                context.getRuntimeState().put(HistoryMiddleware.HISTORY_KEY,
                        projectHistory(historyMessages, HISTORY_BUDGET_CHARS));
                context.getRuntimeState().put(KB_HANDLE_MAP_KEY, new LinkedHashMap<String, Object>());
                context.getRuntimeState().put(KB_HANDLE_SEQ_KEY, new java.util.concurrent.atomic.AtomicInteger(0));

                if (!cfg.getBindings().getTools().isEmpty()) {
                    context.setTools(cfg.getBindings().getTools());
                }
                if (!cfg.getBindings().getSkills().isEmpty()) {
                    context.setSkills(cfg.getBindings().getSkills());
                }
                if (!cfg.getBindings().getMcps().isEmpty()) {
                    context.setMcps(cfg.getBindings().getMcps());
                }
                if (!cfg.getKbScope().isEmpty()) {
                    context.setKnowledges(cfg.getKbScope());
                }
                if (!cfg.getBindings().getDatabases().isEmpty()) {
                    context.setDatabases(cfg.getBindings().getDatabases());
                }

                // 沙箱配置注入（REQ-H03 分支②，2026-09-28）：应用引用 sandbox_config_id 时透传执行链；
                // 未引用保持 runtimeState 无该键——FilesystemMiddleware/SandboxExecutionService 走「not configured」降级
                if (StrUtil.isNotBlank(conv.getAppId())) {
                    App dialogApp = appMapper.selectById(conv.getAppId());
                    if (dialogApp != null && StrUtil.isNotBlank(dialogApp.getSandboxConfigId())) {
                        context.getRuntimeState().put("sandboxConfigId", dialogApp.getSandboxConfigId());
                        // 能力注入（2026-09-30 修复）：绑定沙箱的应用显式告知模型沙箱可用，
                        // 否则模型只看到「执行 Shell 命令」的工具描述，用户要求跑代码时不会主动联想到 execute。
                        // 全局开关关闭等异常场景仍由执行链三分支降级文本兜底，此处只表达应用侧意图。
                        context.setSystemPrompt(StrUtil.nullToEmpty(context.getSystemPrompt()) + SANDBOX_CAPABILITY_HINT);
                    }
                }

                // 执行预算（直聊为 null——引擎侧既有 null 容忍）
                context.setSummaryThreshold(cfg.getSummaryThreshold());
                context.setModelRetryTimes(cfg.getModelRetryTimes());
                context.setMaxSteps(cfg.getMaxSteps());
                if (cfg.getSummaryPrompt() != null) {
                    context.getRuntimeState().put("summaryPrompt", cfg.getSummaryPrompt());
                }
                log.info("[AppChat] Config: tools={}, skills={}, mcps={}, kbs={}, dbs={}, thinking={}",
                        cfg.getBindings().getTools().size(),
                        cfg.getBindings().getSkills().size(),
                        cfg.getBindings().getMcps().size(),
                        cfg.getKbScope().size(),
                        cfg.getBindings().getDatabases().size(),
                        cfg.getModelRoute().isEnableThinking());

                // 构建 ModelConfig（全部来自有效配置，缺省 2048 已在解析器兜底）
                ModelConfig modelConfig = ModelConfig.builder()
                        .model(cfg.getModelRoute().getModel())
                        .apiUrl(cfg.getModelRoute().getApiUrl())
                        .apiKey(cfg.getModelRoute().getApiKey())
                        .enableThinking(cfg.getModelRoute().isEnableThinking())
                        .temperature(cfg.getTemperature())
                        .maxTokens(cfg.getMaxTokens())
                        .build();
                context.setModelConfig(modelConfig);

                // 构建 fake AgentRun（无需中间件，仅用于提取查询）
                AgentRun fakeRun = new AgentRun();
                fakeRun.setId(finalSessionId);
                fakeRun.setUid(finalUserId);
                Map<String, Object> input = new HashMap<>();
                input.put("query", query);
                fakeRun.setInputPayload(input);

                // 使用 AgentEngine 执行（处理所有 SSE 事件）
                agentEngine.executeStream(context, emitter, modelConfig, fakeRun,
                        (finalAnswer, finalThinking, finalToolCalls) -> {
                            // 该回调在 end 事件发送前执行
                            try {
                                // 敏感词输出过滤（受安全开关控制）：命中 replaceAnswer 词条时替换文本，
                                // 并补发 replace 事件让前端把气泡整段替换为过滤后全文
                                Optional<String> filtered = finalSafetyFilterOn
                                        ? sensitiveWordService.filterOutput(finalAnswer)
                                        : Optional.empty();
                                if (filtered.isPresent()) {
                                    try {
                                        Map<String, Object> fixData = new LinkedHashMap<>();
                                        fixData.put("content", filtered.get());
                                        fixData.put("replace", true);
                                        emitter.send(SseEmitter.event().name("message").data(fixData));
                                    } catch (Exception e) {
                                        log.warn("[AppChat] Failed to send filtered answer: {}", e.getMessage());
                                    }
                                }
                                long duration = System.currentTimeMillis() - startTime;
                                // 全 agentic 化：知识来源改自工具调用通道提取（query_kb 的结构化结果投影）；
                                // R2：引用投影按请求级句柄注册表（cN→真实 id）解析，未识别句柄 fail-closed 丢弃
                                List<Map<String, Object>> sources = extractKbSources(finalToolCalls,
                                        handleMapOf(contextRef.get()));
                                persistAnswer(finalConv, cfg, filtered.orElse(finalAnswer), finalThinking,
                                        finalToolCalls, (int) duration, sources,
                                        readIterations(contextRef.get()), persistState, answerSaved);
                            } catch (Exception e) {
                                log.warn("[AppChat] Failed to save message after stream: {}", e.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.error("[AppChat] Execution failed: sessionId={}", finalSessionId, e);
                try {
                    if (handle.getCancelled().get()) {
                        // 用户已停止：不补发 error 事件（NFR-03 无孤儿流）
                        emitter.complete();
                    } else {
                        Map<String, Object> errData = new LinkedHashMap<>();
                        errData.put("message", "对话执行失败: " + e.getMessage());
                        emitter.send(SseEmitter.event().name("error").data(errData));
                        emitter.complete();
                    }
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                // 清理异步线程的 SecurityContext，避免线程池污染
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * R1（P0-6）会话历史投影：转为 [{role,content}] 列表（旧→新），供 HistoryMiddleware 渲染 system 附加块。
     * <ul>
     *   <li>剔除列表末尾的当前轮 user 消息——streamChat 先落库当前问题再查历史，
     *       该消息会由引擎作为本轮 user 消息构建，历史块不得重复</li>
     *   <li>空内容条目跳过</li>
     *   <li>字符预算按渲染长度（"role: content\n"）累计，从最新往最旧收，超限裁最旧（保持最近连续段）</li>
     * </ul>
     */
    private List<Map<String, Object>> projectHistory(List<AppConversationMessage> historyMessages, int budgetChars) {
        List<Map<String, Object>> projected = new ArrayList<>();
        if (historyMessages == null || historyMessages.isEmpty()) {
            return projected;
        }
        List<AppConversationMessage> msgs = new ArrayList<>(historyMessages);
        if (!msgs.isEmpty() && "user".equals(msgs.get(msgs.size() - 1).getRole())) {
            msgs.remove(msgs.size() - 1);
        }
        Deque<Map<String, Object>> kept = new ArrayDeque<>();
        int used = 0;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            AppConversationMessage m = msgs.get(i);
            String content = m.getContent();
            if (content == null || content.isBlank()) continue;
            String role = m.getRole() == null || m.getRole().isBlank() ? "user" : m.getRole();
            int cost = role.length() + 2 + content.length() + 1;
            if (used + cost > budgetChars) break;
            used += cost;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", role);
            entry.put("content", content);
            kept.addFirst(entry);
        }
        projected.addAll(kept);
        return projected;
    }

    /**
     * 全 agentic 化（2026-09-30）：知识来源自工具调用通道提取——扫描 toolCallsJson 中
     * query_kb 调用的结构化结果（result.data.display_type=knowledge_chunks.chunks），
     * 投影为与原预检索 sources 同构的条目（fileName/fileId/score/kbId/chunkIndex/content），供 references 落库。
     */
    private List<Map<String, Object>> extractKbSources(String toolCallsJson) {
        return extractKbSources(toolCallsJson, null);
    }

    /**
     * R2（P0-5）句柄协议投影：带请求级句柄注册表（cN → {kbId,fileId,chunkIndex}）的提取。
     * <ul>
     *   <li>chunk 带 handle：从注册表解析真实 id（fileId/kbId/chunkIndex）；
     *       句柄未注册 → 该条丢弃（fail-closed，模型幻觉句柄不产生假引用）</li>
     *   <li>chunk 无 handle（旧格式/其他调用方）：沿用旧投影（真实 id 直接取 chunk 字段）</li>
     *   <li>fileName/score/content 属展示信息，仍取自 chunk 本体</li>
     * </ul>
     */
    private List<Map<String, Object>> extractKbSources(String toolCallsJson, Map<String, Object> handleMap) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (StrUtil.isBlank(toolCallsJson)) {
            return sources;
        }
        try {
            cn.hutool.json.JSONArray calls = JSONUtil.parseArray(toolCallsJson);
            for (Object o : calls) {
                cn.hutool.json.JSONObject call = (cn.hutool.json.JSONObject) o;
                if (!"query_kb".equals(call.getStr("name"))) {
                    continue;
                }
                cn.hutool.json.JSONObject result = call.getJSONObject("result");
                cn.hutool.json.JSONObject data = result == null ? null : result.getJSONObject("data");
                cn.hutool.json.JSONArray chunks = data == null ? null : data.getJSONArray("chunks");
                if (chunks == null) {
                    continue;
                }
                for (Object c : chunks) {
                    cn.hutool.json.JSONObject chunk = (cn.hutool.json.JSONObject) c;
                    String handle = chunk.getStr("handle");
                    String fileId;
                    String kbId;
                    Object chunkIndex;
                    if (handle != null && !handle.isBlank()) {
                        // R2：句柄 → 真实 id（请求级注册表）；未识别句柄丢弃（fail-closed）
                        Object refObj = handleMap == null ? null : handleMap.get(handle);
                        if (!(refObj instanceof Map)) {
                            log.debug("[AppChat] Unresolved KB handle '{}' dropped from references (fail-closed)", handle);
                            continue;
                        }
                        Map<?, ?> ref = (Map<?, ?>) refObj;
                        fileId = ref.get("fileId") == null ? null : String.valueOf(ref.get("fileId"));
                        kbId = ref.get("kbId") == null ? data.getStr("kb_id") : String.valueOf(ref.get("kbId"));
                        chunkIndex = ref.get("chunkIndex") instanceof Number ? ref.get("chunkIndex") : 0;
                    } else {
                        fileId = chunk.getStr("file_id"); // QF-1：上游结构化通道已含 file_id，投影不再丢弃
                        kbId = data.getStr("kb_id");
                        chunkIndex = chunk.get("chunk_index");
                    }
                    Map<String, Object> src = new LinkedHashMap<>();
                    src.put("fileName", chunk.getStr("source"));
                    src.put("fileId", fileId);
                    src.put("score", chunk.get("score"));
                    src.put("kbId", kbId);
                    src.put("chunkIndex", chunkIndex);
                    src.put("content", chunk.getStr("content"));
                    sources.add(src);
                }
            }
        } catch (Exception e) {
            log.warn("[AppChat] Failed to extract kb sources from toolCalls: {}", e.getMessage());
        }
        return sources;
    }

    /**
     * 判断用户消息是否为打招呼
     */
    private boolean isGreetingQuery(String query) {
        if (StrUtil.isBlank(query)) return false;
        String lower = query.toLowerCase().trim();
        return lower.contains("你好") || lower.contains("您好") || lower.contains("hi")
                || lower.contains("hello") || lower.contains("hey") || lower.equals("嗨");
    }

    /**
     * 保存助手消息到数据库（references = 检索来源投影，两通道统一，REQ-F01）
     */
    private void saveAssistantMessage(AppConversation conv, String content, String thinkingContent, String toolCalls,
                                      int latencyMs, List<Map<String, Object>> sources, Integer iterations) {
        AppConversationMessage assistantMsg = new AppConversationMessage();
        assistantMsg.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        assistantMsg.setConversationId(conv.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(content);
        assistantMsg.setThinkingContent(thinkingContent);
        assistantMsg.setToolCalls(toolCalls);
        assistantMsg.setReferences(projectReferences(sources));
        assistantMsg.setIterations(iterations);
        assistantMsg.setLatencyMs(latencyMs);
        assistantMsg.setTokens(0);
        assistantMsg.setCreatedAt(LocalDateTime.now());
        convMsgMapper.insert(assistantMsg);

        // 更新会话统计
        int msgCount = (conv.getMessageCount() != null ? conv.getMessageCount() : 0) + 2; // user + assistant
        conv.setMessageCount(msgCount);
        if (conv.getMessageCount() == 2) {
            // 第一轮对话，设置 answerSummary
            conv.setAnswerSummary(content.length() > 200 ? content.substring(0, 200) + "..." : content);
        }
        conv.setUpdatedAt(LocalDateTime.now());
        convMapper.updateById(conv);
    }

    /**
     * 恰一次落库（new-conversation）：引擎回调 / 超时 / 停止三条路径共用，
     * AtomicBoolean 保证助手消息与请求态固化各至多一次（contract StopRegistry 幂等语义的执行侧）。
     * 空答案（停止早于任何输出）标记已处理但不落空行。
     */
    private void persistAnswer(AppConversation conv, EffectiveDialogConfig cfg, String answer,
                               String thinkingContent, String toolCalls, int latencyMs,
                               List<Map<String, Object>> sources, Integer iterations,
                               ConversationRequestState persistState,
                               java.util.concurrent.atomic.AtomicBoolean saved) {
        if (!saved.compareAndSet(false, true)) return;
        if (answer == null || answer.isEmpty()) return;
        saveAssistantMessage(conv, answer, thinkingContent, toolCalls, latencyMs, sources, iterations);
        if (persistState != null) {
            requestStateStore.persistOnSuccess(conv.getId(), persistState);
        }
    }

    /** 从执行上下文读引擎迭代轮数（runtimeState 侧通道，引擎在 onComplete 前写入）；不可得返回 null */
    private Integer readIterations(BaseContext context) {
        if (context == null || context.getRuntimeState() == null) return null;
        Object v = context.getRuntimeState().get("iterations");
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    /** R2：从执行上下文取请求级句柄注册表（runtimeState 与 ToolContext.extensions 共享同一 Map 引用） */
    private Map<String, Object> handleMapOf(BaseContext context) {
        if (context == null) return null;
        Object v = context.getRuntimeState().get(KB_HANDLE_MAP_KEY);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    /**
     * 消息引用投影（contract data.md MessageReference）：sources 的子集，
     * 剔除 content（分片全文不随消息持久化，防膨胀）；与流内 sources 事件同源。
     */
    private String projectReferences(List<Map<String, Object>> sources) {
        List<Map<String, Object>> projection = new ArrayList<>();
        if (sources != null) {
            for (Map<String, Object> src : sources) {
                Map<String, Object> p = new LinkedHashMap<>(src);
                p.remove("content");
                projection.add(p);
            }
        }
        return JSONUtil.toJsonStr(projection);
    }
}
