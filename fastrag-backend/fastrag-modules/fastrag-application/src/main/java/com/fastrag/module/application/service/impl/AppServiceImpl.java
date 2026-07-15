package com.fastrag.module.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.executor.AgentEngine;
import com.fastrag.module.agent.executor.ModelConfig;
import com.fastrag.module.application.dto.AppSessionDTO;
import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.AppService;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
import com.fastrag.module.retrieval.service.RetrievalService;
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
    private final RetrievalService retrievalService;
    private final AgentEngine agentEngine;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    // ==================== 原有 CRUD 方法 ====================

    @Override
    public List<App> list(String kw, String tag) {
        var w = new LambdaQueryWrapper<App>();
        if (StrUtil.isNotBlank(kw)) w.like(App::getName, kw);
        if (StrUtil.isNotBlank(tag)) w.like(App::getTags, tag);
        return appMapper.selectList(w.orderByDesc(App::getCreatedAt));
    }

    @Override
    public App get(String id) {
        return appMapper.selectById(id);
    }

    @Override
    public App create(Map<String, Object> f) {
        var a = new App();
        a.setName((String) f.get("name"));
        a.setDescription((String) f.get("description"));
        a.setType((String) f.getOrDefault("type", "ChatBot"));
        a.setStatus("draft");
        appMapper.insert(a);
        return a;
    }

    @Override
    public App update(String id, Map<String, Object> f) {
        var a = appMapper.selectById(id);
        if (a != null) {
            if (f.containsKey("name")) a.setName((String) f.get("name"));
            if (f.containsKey("description")) a.setDescription((String) f.get("description"));
            appMapper.updateById(a);
        }
        return a;
    }

    @Override
    public void delete(String id) {
        appMapper.deleteById(id);
    }

    @Override
    public List<AppTemplate> getTemplates() {
        return tplMapper.selectList(null);
    }

    @Override
    public AppConfig getConfig(String id) {
        return configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId, id));
    }

    @Override
    public AppConfig saveConfig(String id, AppConfig config) {
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
        // 根据 sessionId 查找 conversation
        AppConversation conv = convMapper.selectOne(
                new LambdaQueryWrapper<AppConversation>()
                        .eq(AppConversation::getAppId, appId)
                        .eq(AppConversation::getSessionId, sessionId));
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
            m.put("createdAt", msg.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    @Override
    public void deleteSession(String appId, String sessionId) {
        AppConversation conv = convMapper.selectOne(
                new LambdaQueryWrapper<AppConversation>()
                        .eq(AppConversation::getAppId, appId)
                        .eq(AppConversation::getSessionId, sessionId));
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
        AppConversationMessage msg = convMsgMapper.selectById(messageId);
        if (msg == null) return;
        msg.setDeletedAt(java.time.LocalDateTime.now());
        convMsgMapper.updateById(msg);
    }

    @Override
    public void feedbackMessage(String appId, String messageId, String feedback) {
        AppConversationMessage msg = convMsgMapper.selectById(messageId);
        if (msg == null) return;
        msg.setFeedback(feedback);
        convMsgMapper.updateById(msg);
    }

    @Override
    public void updateMessage(String appId, String messageId, String content) {
        AppConversationMessage msg = convMsgMapper.selectById(messageId);
        if (msg == null) return;
        msg.setContent(content);
        convMsgMapper.updateById(msg);
    }

    // ==================== SSE 流式对话 ====================

    @Override
    public SseEmitter runStream(String appId, String query, String sessionId, String userId, String userName) {
        // 1. 验证应用存在
        App app = appMapper.selectById(appId);
        if (app == null) {
            throw new RuntimeException("应用不存在: " + appId);
        }

        // 2. 获取或创建会话
        AppConversation conv = null;
        if (StrUtil.isNotBlank(sessionId)) {
            conv = convMapper.selectOne(
                    new LambdaQueryWrapper<AppConversation>()
                            .eq(AppConversation::getAppId, appId)
                            .eq(AppConversation::getSessionId, sessionId));
        }
        if (conv == null) {
            // 创建新会话
            Map<String, Object> sessionInfo = createSession(appId, userId, userName);
            sessionId = (String) sessionInfo.get("sessionId");
            conv = convMapper.selectOne(
                    new LambdaQueryWrapper<AppConversation>()
                            .eq(AppConversation::getAppId, appId)
                            .eq(AppConversation::getSessionId, sessionId));
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

        // 4. 读取应用配置
        AppConfig appConfig = getConfig(appId);
        AppBasicConfig basicConfig = basicConfigMapper.selectOne(
                new LambdaQueryWrapper<AppBasicConfig>().eq(AppBasicConfig::getAppId, appId));
        int memoryRounds = (basicConfig != null && basicConfig.getMemoryRounds() != null)
                ? basicConfig.getMemoryRounds() : 5;

        // 从 app_config 读取模型 code，如果为空则从 advancedOptions 读取
        String modelCode = null;
        if (appConfig != null && StrUtil.isNotBlank(appConfig.getModel())) {
            modelCode = appConfig.getModel();
        }
        // 也尝试从 AppBasicConfig.advancedOptions JSON 中读取
        if (StrUtil.isBlank(modelCode) && basicConfig != null && StrUtil.isNotBlank(basicConfig.getAdvancedOptions())) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> adv = om.readValue(basicConfig.getAdvancedOptions(), Map.class);
                if (adv.containsKey("model") && StrUtil.isNotBlank(adv.get("model").toString())) {
                    modelCode = adv.get("model").toString();
                }
            } catch (Exception ignored) {}
        }

        // 根据 modelCode 查找 ModelRecord 获取 apiUrl 和 apiKey
        String model = "default";
        String modelApiUrl = null;
        String modelApiKey = null;
        boolean enableThinking = false;
        if (StrUtil.isNotBlank(modelCode)) {
            model = modelCode;
            ModelRecord modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, modelCode)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
            if (modelRecord != null) {
                if (StrUtil.isNotBlank(modelRecord.getApiUrl())) {
                    modelApiUrl = modelRecord.getApiUrl();
                }
                if (StrUtil.isNotBlank(modelRecord.getApiKeyRef())) {
                    modelApiKey = modelRecord.getApiKeyRef();
                }
                enableThinking = Boolean.TRUE.equals(modelRecord.getEnableThinking());
                log.info("[AppChat] Using model: code={}, name={}, apiUrl={}, thinking={}",
                        modelCode, modelRecord.getName(), modelApiUrl, enableThinking);
            } else {
                log.warn("[AppChat] Model not found or offline: code={}, will use default gateway", modelCode);
            }
        }

        String systemPrompt = (appConfig != null && StrUtil.isNotBlank(appConfig.getPrompt()))
                ? appConfig.getPrompt() : "你是一个智能助手，请根据用户的问题提供帮助。";
        double temperature = (appConfig != null && appConfig.getTemperature() != null)
                ? appConfig.getTemperature().doubleValue() : 0.7;

        // 5. 如果有问候语且是第一条消息，直接返回问候语
        String greeting = (basicConfig != null) ? basicConfig.getGreeting() : null;
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
                saveAssistantMessage(conv, greeting, 0);
                return emitter;
            } catch (Exception e) {
                throw new RuntimeException("Failed to send greeting", e);
            }
        }

        // 6. 立即创建 SSE Emitter 并返回，所有耗时操作放到异步线程
        final String effectiveSystemPromptBase = systemPrompt;
        final int finalMemoryRounds = memoryRounds;
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 分钟超时
        StringBuilder fullAnswer = new StringBuilder();
        final String finalSessionId = sessionId;
        final AppConversation finalConv = conv;
        final long startTime = System.currentTimeMillis();
        final String finalModel = model;
        final double finalTemperature = temperature;
        final String finalModelApiUrl = modelApiUrl;
        final String finalModelApiKey = modelApiKey;
        final boolean finalEnableThinking = enableThinking;
        final String finalUserId = userId;

        emitter.onCompletion(() -> log.info("[AppChat] SSE completed: sessionId={}", finalSessionId));
        emitter.onTimeout(() -> {
            log.warn("[AppChat] SSE timeout: sessionId={}", finalSessionId);
            if (fullAnswer.length() > 0) {
                saveAssistantMessage(finalConv, fullAnswer.toString(), (int)(System.currentTimeMillis() - startTime));
            }
        });
        emitter.onError(e -> log.warn("[AppChat] SSE error: sessionId={}, error={}", finalSessionId, e.getMessage()));

        // 7. 异步执行：RAG 检索 + 构建消息 + 流式对话
        // 在提交前捕获 SecurityContext，传播到异步线程
        var securityContext = SecurityContextHolder.getContext();
        streamExecutor.execute(() -> {
            try {
                // 将 SecurityContext 传播到异步线程，供下游（如 recordLog）使用
                SecurityContextHolder.setContext(securityContext);
                // RAG 知识库检索（异步执行，不阻塞 HTTP 线程）
                String ragContext = retrieveContext(appId, query);
                String effectiveSystemPrompt = effectiveSystemPromptBase;
                if (StrUtil.isNotBlank(ragContext)) {
                    effectiveSystemPrompt = effectiveSystemPromptBase + "\n\n以下是相关知识库检索到的参考资料，请参考这些资料回答用户问题：\n\n" + ragContext + "\n\n请根据以上参考资料回答用户问题。如果参考资料中没有相关信息，请如实告知用户。";
                }

                // 构建历史消息
                List<AppConversationMessage> historyMessages = convMsgMapper.selectList(
                        new LambdaQueryWrapper<AppConversationMessage>()
                                .eq(AppConversationMessage::getConversationId, finalConv.getId())
                                .orderByDesc(AppConversationMessage::getCreatedAt)
                                .last("LIMIT " + (finalMemoryRounds * 2)));
                Collections.reverse(historyMessages);

                // 构建 BaseContext
                BaseContext context = new BaseContext();
                context.setUid(finalUserId);
                context.setSystemPrompt(effectiveSystemPrompt);
                context.setModel(finalModel);

                // 加载应用绑定的工具/技能/MCP/知识库
                List<AppToolBinding> toolBindings = toolBindingMapper.selectList(
                        new LambdaQueryWrapper<AppToolBinding>()
                                .eq(AppToolBinding::getAppId, appId)
                                .eq(AppToolBinding::getEnabled, 1));
                if (!toolBindings.isEmpty()) {
                    context.setTools(toolBindings.stream()
                            .map(AppToolBinding::getToolId)
                            .filter(Objects::nonNull)
                            .toList());
                }

                List<AppSkillBinding> skillBindings = skillBindingMapper.selectList(
                        new LambdaQueryWrapper<AppSkillBinding>()
                                .eq(AppSkillBinding::getAppId, appId)
                                .eq(AppSkillBinding::getEnabled, 1));
                if (!skillBindings.isEmpty()) {
                    context.setSkills(skillBindings.stream()
                            .map(AppSkillBinding::getSkillId)
                            .filter(Objects::nonNull)
                            .toList());
                }

                List<AppMcpBinding> mcpBindings = mcpBindingMapper.selectList(
                        new LambdaQueryWrapper<AppMcpBinding>()
                                .eq(AppMcpBinding::getAppId, appId)
                                .eq(AppMcpBinding::getEnabled, 1));
                if (!mcpBindings.isEmpty()) {
                    context.setMcps(mcpBindings.stream()
                            .map(AppMcpBinding::getMcpServiceId)
                            .filter(Objects::nonNull)
                            .toList());
                }

                List<AppKbBinding> kbBindings = kbBindingMapper.selectList(
                        new LambdaQueryWrapper<AppKbBinding>()
                                .eq(AppKbBinding::getAppId, appId)
                                .eq(AppKbBinding::getEnabled, 1));
                if (!kbBindings.isEmpty()) {
                    context.setKnowledges(kbBindings.stream()
                            .map(AppKbBinding::getKbId)
                            .filter(Objects::nonNull)
                            .toList());
                }

                // 加载数据库绑定
                List<AppDbBinding> dbBindings = dbMapper.selectList(
                        new LambdaQueryWrapper<AppDbBinding>()
                                .eq(AppDbBinding::getAppId, appId)
                                .eq(AppDbBinding::getEnabled, 1));
                if (!dbBindings.isEmpty()) {
                    context.setDatabases(dbBindings.stream()
                            .map(AppDbBinding::getDbId)
                            .filter(Objects::nonNull)
                            .toList());
                }

                // 从 AppConfig 加载新配置字段（summaryThreshold/retryTimes/maxSteps/summaryPrompt）
                if (appConfig != null) {
                    context.setSummaryThreshold(appConfig.getSummaryThreshold());
                    context.setModelRetryTimes(appConfig.getRetryTimes());
                    context.setMaxSteps(appConfig.getMaxSteps());
                    if (appConfig.getSummaryPrompt() != null) {
                        context.getRuntimeState().put("summaryPrompt", appConfig.getSummaryPrompt());
                    }
                }
                log.info("[AppChat] Config: tools={}, skills={}, mcps={}, kbs={}, dbs={}, thinking={}",
                        context.getTools() != null ? context.getTools().size() : 0,
                        context.getSkills() != null ? context.getSkills().size() : 0,
                        context.getMcps() != null ? context.getMcps().size() : 0,
                        context.getKnowledges() != null ? context.getKnowledges().size() : 0,
                        context.getDatabases() != null ? context.getDatabases().size() : 0,
                        finalEnableThinking);

                // 构建 ModelConfig（maxTokens 从 appConfig 读取，默认 2048）
                int effectiveMaxTokens = (appConfig != null && appConfig.getMaxTokens() != null)
                        ? appConfig.getMaxTokens() : 2048;
                ModelConfig modelConfig = ModelConfig.builder()
                        .model(finalModel)
                        .apiUrl(finalModelApiUrl)
                        .apiKey(finalModelApiKey)
                        .enableThinking(finalEnableThinking)
                        .temperature(finalTemperature)
                        .maxTokens(effectiveMaxTokens)
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
                        finalAnswer -> {
                            // 该回调在 end 事件发送前执行
                            try {
                                long duration = System.currentTimeMillis() - startTime;
                                saveAssistantMessage(finalConv, finalAnswer, (int) duration);
                            } catch (Exception e) {
                                log.warn("[AppChat] Failed to save message after stream: {}", e.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.error("[AppChat] Execution failed: sessionId={}", finalSessionId, e);
                try {
                    Map<String, Object> errData = new LinkedHashMap<>();
                    errData.put("message", "对话执行失败: " + e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(errData));
                    emitter.complete();
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
     * RAG 知识库检索
     */
    private String retrieveContext(String appId, String query) {
        List<AppKbBinding> kbBindings = kbBindingMapper.selectList(
                new LambdaQueryWrapper<AppKbBinding>()
                        .eq(AppKbBinding::getAppId, appId)
                        .eq(AppKbBinding::getEnabled, 1));

        if (kbBindings.isEmpty()) return null;

        StringBuilder context = new StringBuilder();
        int refIndex = 1;
        for (AppKbBinding binding : kbBindings) {
            try {
                RetrievalRequest req = new RetrievalRequest();
                req.setKnowledgeId(binding.getKbId());
                req.setQuery(query);
                // 设置默认检索配置：top 5，混合模式
                RetrievalRequest.RetrievalConfig config = new RetrievalRequest.RetrievalConfig();
                config.setTopK(5);
                config.setMode("hybrid");
                config.setSimilarityThreshold(0.3);
                req.setConfig(config);

                List<SearchResultItem> results = retrievalService.search(req);
                for (SearchResultItem item : results) {
                    if (StrUtil.isNotBlank(item.getContent())) {
                        context.append("【参考资料").append(refIndex++).append("】\n")
                               .append(item.getContent()).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("[AppChat] RAG retrieval failed for kbId={}: {}", binding.getKbId(), e.getMessage());
            }
        }
        return context.length() > 0 ? context.toString() : null;
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
     * 保存助手消息到数据库
     */
    private void saveAssistantMessage(AppConversation conv, String content, int latencyMs) {
        AppConversationMessage assistantMsg = new AppConversationMessage();
        assistantMsg.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        assistantMsg.setConversationId(conv.getId());
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(content);
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
}
