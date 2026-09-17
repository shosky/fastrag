package com.fastrag.module.operation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.entity.KbStandardQuestion;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KbStandardQuestionMapper;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.operation.entity.ChatSession;
import com.fastrag.module.operation.entity.UserFeedback;
import com.fastrag.module.operation.mapper.ChatSessionMapper;
import com.fastrag.module.operation.mapper.UserFeedbackMapper;
import com.fastrag.module.operation.model.AnalyticsData;
import com.fastrag.module.retrieval.entity.KbRetrievalLog;
import com.fastrag.module.retrieval.mapper.KbRetrievalLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 运营分析（真实数据聚合）：
 * - /kb 知识资产分析 + 热门排行（period 时间窗：检索日志热度）
 * - /faq FAQ 分析（检索日志按 query 聚合 + 反馈评分）
 * - /multi-turn 多轮会话分析（chat_session + user_feedback）
 * - /intent 意图分析（标准问法按类目聚合 + 检索命中）
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final KnowledgeBaseMapper kbMapper;
    private final KbFileMapper fileMapper;
    private final KbRetrievalLogMapper retrievalLogMapper;
    private final KbQaPairMapper qaPairMapper;
    private final KbStandardQuestionMapper standardQuestionMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final UserFeedbackMapper feedbackMapper;

    private LocalDateTime windowStart(String period) {
        return switch (period == null ? "month" : period.toLowerCase()) {
            case "day", "today" -> LocalDateTime.now().minusDays(1);
            case "week" -> LocalDateTime.now().minusDays(7);
            case "quarter" -> LocalDateTime.now().minusDays(90);
            default -> LocalDateTime.now().minusDays(30);
        };
    }

    @GetMapping("/kb")
    public ApiResponse<?> getKbAnalytics(@RequestParam(required = false, defaultValue = "week") String period) {
        // 总知识库数量
        List<KnowledgeBase> allKBs = kbMapper.selectList(new LambdaQueryWrapper<>());
        long totalKBs = allKBs.size();

        // 总文档数（未删除）
        LambdaQueryWrapper<KbFile> fileQuery = new LambdaQueryWrapper<KbFile>()
                .isNull(KbFile::getDeletedAt);
        long totalFiles = fileMapper.selectCount(fileQuery);

        // 活跃文档数（已完成处理）
        LambdaQueryWrapper<KbFile> activeQuery = new LambdaQueryWrapper<KbFile>()
                .isNull(KbFile::getDeletedAt)
                .eq(KbFile::getStatus, "completed");
        long activeFiles = fileMapper.selectCount(activeQuery);

        // 知识引用率
        LambdaQueryWrapper<KbFile> chunkQuery = new LambdaQueryWrapper<KbFile>()
                .isNull(KbFile::getDeletedAt)
                .eq(KbFile::getStatus, "completed")
                .gt(KbFile::getChunkCount, 0);
        long filesWithChunks = fileMapper.selectCount(chunkQuery);
        String citationRate = activeFiles > 0
                ? String.format("%.1f%%", (double) filesWithChunks / activeFiles * 100)
                : "0%";

        List<AnalyticsData.MetricItem> metrics = List.of(
                AnalyticsData.MetricItem.builder().label("总知识库数量").value(totalKBs).change("").trend("up").build(),
                AnalyticsData.MetricItem.builder().label("总文档数").value(totalFiles).change("").trend("up").build(),
                AnalyticsData.MetricItem.builder().label("活跃文档数量").value(activeFiles).change("").trend("up").build(),
                AnalyticsData.MetricItem.builder().label("知识引用率").value(0).change(citationRate).trend("up").build()
        );

        // 时间窗内的检索热度（真实时间维度，支撑排行周期过滤）
        LocalDateTime since = windowStart(period);
        List<KbRetrievalLog> windowLogs = retrievalLogMapper.selectList(
                new LambdaQueryWrapper<KbRetrievalLog>().ge(KbRetrievalLog::getCreatedAt, since));
        Map<String, Long> searchCountByKb = windowLogs.stream()
                .filter(l -> l.getKbId() != null)
                .collect(Collectors.groupingBy(KbRetrievalLog::getKbId, Collectors.counting()));

        // 热门知识库排行（文档数 + 访问量 + 周期内检索量综合排序）
        List<AnalyticsData.HotKb> hotKBs = allKBs.stream()
                .limit(20)
                .map(kb -> {
                    long docCount = fileMapper.selectCount(
                            new LambdaQueryWrapper<KbFile>()
                                    .eq(KbFile::getKbId, kb.getId())
                                    .isNull(KbFile::getDeletedAt)
                    );
                    long viewCount = fileMapper.selectCount(
                            new LambdaQueryWrapper<KbFile>()
                                    .eq(KbFile::getKbId, kb.getId())
                                    .isNull(KbFile::getDeletedAt)
                                    .gt(KbFile::getViewCount, 0)
                    );
                    long searchCount = searchCountByKb.getOrDefault(kb.getId(), 0L);
                    return AnalyticsData.HotKb.builder()
                            .id(kb.getId())
                            .name(kb.getName())
                            .docCount(docCount)
                            .viewCount(viewCount)
                            .accessCount(docCount + viewCount + searchCount)
                            .description(kb.getDescription())
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getAccessCount(), a.getAccessCount()))
                .limit(10)
                .collect(Collectors.toList());

        // 设置排名
        for (int i = 0; i < hotKBs.size(); i++) {
            hotKBs.get(i).setRank(i + 1);
        }

        // 热门文档排行（按浏览量）
        LambdaQueryWrapper<KbFile> hotDocQuery = new LambdaQueryWrapper<KbFile>()
                .isNull(KbFile::getDeletedAt)
                .eq(KbFile::getStatus, "completed")
                .orderByDesc(KbFile::getViewCount)
                .last("LIMIT 10");
        List<KbFile> topFiles = fileMapper.selectList(hotDocQuery);

        List<AnalyticsData.HotDoc> hotDocs = topFiles.stream()
                .map(f -> {
                    KnowledgeBase kb = kbMapper.selectById(f.getKbId());
                    return AnalyticsData.HotDoc.builder()
                            .id(f.getId())
                            .name(f.getName())
                            .kbName(kb != null ? kb.getName() : "")
                            .viewCount(f.getViewCount() != null ? f.getViewCount() : 0)
                            .category(f.getCategory())
                            .build();
                })
                .collect(Collectors.toList());

        // 设置排名
        for (int i = 0; i < hotDocs.size(); i++) {
            hotDocs.get(i).setRank(i + 1);
        }

        AnalyticsData data = AnalyticsData.builder()
                .period(period)
                .metrics(metrics)
                .hotKBs(hotKBs)
                .hotDocs(hotDocs)
                .build();

        return ApiResponse.success(data);
    }

    // ===== FAQ 分析（真实聚合：检索日志按 query 统计 + 反馈评分） =====
    @GetMapping("/faq")
    public ApiResponse<?> faqAnalysis(@RequestParam(required = false, defaultValue = "month") String period) {
        LocalDateTime since = windowStart(period);
        List<KbRetrievalLog> logs = retrievalLogMapper.selectList(
                new LambdaQueryWrapper<KbRetrievalLog>().ge(KbRetrievalLog::getCreatedAt, since));

        Map<String, List<KbRetrievalLog>> byQuery = logs.stream()
                .filter(l -> l.getQuery() != null && !l.getQuery().isBlank())
                .collect(Collectors.groupingBy(KbRetrievalLog::getQuery));

        // 反馈评分按 query 关联
        Map<String, List<Integer>> scoreByQuery = feedbackMapper.selectList(
                        new LambdaQueryWrapper<UserFeedback>().ge(UserFeedback::getCreatedAt, since))
                .stream()
                .filter(f -> f.getQuery() != null && f.getScore() != null)
                .collect(Collectors.groupingBy(UserFeedback::getQuery,
                        Collectors.mapping(UserFeedback::getScore, Collectors.toList())));

        List<Map<String, Object>> rows = new ArrayList<>();
        byQuery.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(20)
                .forEach(e -> {
                    long hit = e.getValue().stream().filter(l -> Boolean.TRUE.equals(l.getHasResult())).count();
                    long miss = e.getValue().size() - hit;
                    List<Integer> scores = scoreByQuery.get(e.getKey());
                    double avg = scores == null || scores.isEmpty() ? 0.0 :
                            Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("question", e.getKey());
                    m.put("hitCount", e.getValue().size());
                    m.put("missCount", miss);
                    m.put("hitRate", e.getValue().isEmpty() ? 0.0 : Math.round((double) hit / e.getValue().size() * 100) / 100.0);
                    m.put("avgSatisfaction", avg);
                    m.put("period", period);
                    rows.add(m);
                });
        return ApiResponse.success(rows);
    }

    // ===== 多轮会话分析（真实聚合：chat_session + user_feedback） =====
    @GetMapping("/multi-turn")
    public ApiResponse<?> multiTurnAnalysis(@RequestParam(required = false, defaultValue = "month") String period) {
        LocalDateTime since = windowStart(period);
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>().ge(ChatSession::getCreatedAt, since)
                        .orderByDesc(ChatSession::getCreatedAt).last("LIMIT 50"));

        Map<String, List<UserFeedback>> fbBySession = feedbackMapper.selectList(
                        new LambdaQueryWrapper<UserFeedback>().ge(UserFeedback::getCreatedAt, since))
                .stream().filter(f -> f.getSessionId() != null)
                .collect(Collectors.groupingBy(UserFeedback::getSessionId));

        List<String> topics = standardQuestionMapper.selectList(new LambdaQueryWrapper<KbStandardQuestion>().eq(KbStandardQuestion::getEnabled, 1))
                .stream().map(KbStandardQuestion::getCategory).filter(c -> c != null && !c.isBlank()).distinct().toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChatSession s : sessions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sessionId", s.getId());
            // 主题：命中标准问法类目优先，否则取 query 前 8 字
            String topic = topics.stream().filter(t -> s.getQuery() != null && s.getQuery().contains(t)).findFirst()
                    .orElse(s.getQuery() == null ? "未知" : s.getQuery().length() > 8 ? s.getQuery().substring(0, 8) : s.getQuery());
            m.put("topic", topic);
            m.put("turnCount", 1);
            m.put("resolution", s.getAnswer() != null && !s.getAnswer().isBlank() ? "resolved" : "unresolved");
            List<UserFeedback> fbs = fbBySession.get(s.getId());
            m.put("satisfaction", fbs == null || fbs.isEmpty() ? null :
                    fbs.stream().filter(f -> f.getScore() != null).mapToInt(UserFeedback::getScore).average().orElse(0.0));
            m.put("keyIntents", topics.stream().filter(t -> s.getQuery() != null && s.getQuery().contains(t)).limit(2).toList());
            m.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
            rows.add(m);
        }
        return ApiResponse.success(rows);
    }

    // ===== 意图分析（真实聚合：标准问法按类目 + 检索命中统计） =====
    @GetMapping("/intent")
    public ApiResponse<?> intentAnalysis(@RequestParam(required = false, defaultValue = "month") String period) {
        LocalDateTime since = windowStart(period);
        List<KbStandardQuestion> stds = standardQuestionMapper.selectList(
                new LambdaQueryWrapper<KbStandardQuestion>().eq(KbStandardQuestion::getEnabled, 1));
        List<KbRetrievalLog> logs = retrievalLogMapper.selectList(
                new LambdaQueryWrapper<KbRetrievalLog>().ge(KbRetrievalLog::getCreatedAt, since));

        Map<String, List<KbStandardQuestion>> byCategory = stds.stream()
                .collect(Collectors.groupingBy(s -> s.getCategory() == null || s.getCategory().isBlank() ? "未分类" : s.getCategory()));

        // 每类意图：utteranceCount = 类内问法被查询包含的日志次数 + hitCount 累加；accuracy = 命中日志中有结果占比
        record Agg(int utterances, int hits, int withResult) { }
        Map<String, Agg> aggByIntent = new LinkedHashMap<>();
        for (var e : byCategory.entrySet()) {
            int utterances = 0, hits = 0, withResult = 0;
            for (KbStandardQuestion std : e.getValue()) {
                hits += std.getHitCount() == null ? 0 : std.getHitCount();
                for (KbRetrievalLog log : logs) {
                    if (log.getQuery() != null && std.getStandardQuestion() != null
                            && (log.getQuery().contains(std.getStandardQuestion()) || std.getStandardQuestion().contains(log.getQuery()))) {
                        utterances++;
                        if (Boolean.TRUE.equals(log.getHasResult())) withResult++;
                    }
                }
            }
            aggByIntent.put(e.getKey(), new Agg(utterances, hits, withResult));
        }
        int totalStd = stds.isEmpty() ? 1 : stds.size();
        List<Map.Entry<String, Agg>> sorted = aggByIntent.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().utterances() + b.getValue().hits(), a.getValue().utterances() + a.getValue().hits()))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            var e = sorted.get(i);
            Agg agg = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("intent", e.getKey());
            m.put("utteranceCount", agg.utterances() + agg.hits());
            m.put("accuracy", agg.utterances() == 0 ? 0.0 : Math.round((double) agg.withResult() / agg.utterances() * 100) / 100.0);
            m.put("coverage", Math.round((double) byCategory.get(e.getKey()).size() / totalStd * 100) / 100.0);
            List<String> confused = new ArrayList<>();
            for (int j = 0; j < sorted.size() && confused.size() < 2; j++) {
                if (j != i) confused.add(sorted.get(j).getKey());
            }
            m.put("topConfusedIntents", confused);
            m.put("suggestion", agg.utterances() == 0 ? "该类目暂无检索命中，建议补充标准问法或训练语料" : "建议持续补充相似问法提升命中");
            rows.add(m);
        }
        return ApiResponse.success(rows);
    }
}
