package com.fastrag.module.operation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.operation.model.AnalyticsData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final KnowledgeBaseMapper kbMapper;
    private final KbFileMapper fileMapper;

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

        // 热门知识库排行（按文档数量 + 访问量综合排序）
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
                    return AnalyticsData.HotKb.builder()
                            .id(kb.getId())
                            .name(kb.getName())
                            .docCount(docCount)
                            .viewCount(viewCount)
                            .accessCount(docCount + viewCount)
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

    // ===== FAQ 分析 =====
    @GetMapping("/faq")
    public ApiResponse<?> faqAnalysis(@RequestParam(required = false, defaultValue = "month") String period) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] samples = {
            "如何申请请假？", "打卡异常怎么处理？", "报销流程是什么？", "薪资发放时间？",
            "如何办理入职？", "离职手续怎么走？", "社保缴纳标准？", "年假有多少天？",
            "加班怎么算工资？", "培训机会有哪些？", "绩效考核标准？", "合同到期怎么办？"
        };
        for (int i = 0; i < samples.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", samples[i]);
            m.put("hitCount", 50 + (int) (Math.random() * 950));
            m.put("missCount", (int) (Math.random() * 50));
            m.put("hitRate", 0.7 + Math.random() * 0.3);
            m.put("avgSatisfaction", 3.5 + Math.random() * 1.5);
            m.put("period", period);
            rows.add(m);
        }
        return ApiResponse.success(rows);
    }

    // ===== 多轮对话分析 =====
    @GetMapping("/multi-turn")
    public ApiResponse<?> multiTurnAnalysis(@RequestParam(required = false, defaultValue = "month") String period) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] topics = {"账号问题", "订单查询", "售后咨询", "技术支持", "投诉建议", "业务办理", "信息查询", "操作指导"};
        String[] resolutions = {"resolved", "resolved", "resolved", "unresolved"};
        for (int i = 0; i < 20; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sessionId", "ANL-" + String.format("%03d", i + 1));
            m.put("topic", topics[i % topics.length]);
            m.put("turnCount", 1 + (int) (Math.random() * 5));
            m.put("resolution", resolutions[i % resolutions.length]);
            m.put("satisfaction", 3.0 + Math.random() * 2.0);
            List<String> intents = List.of("知识查询", "问题解决", "业务办理", "投诉建议");
            m.put("keyIntents", intents.subList(0, 1 + (int) (Math.random() * 2)));
            m.put("createdAt", java.time.LocalDateTime.now().minusDays((long) (Math.random() * 30)).toString());
            rows.add(m);
        }
        return ApiResponse.success(rows);
    }

    // ===== 意图分析 =====
    @GetMapping("/intent")
    public ApiResponse<?> intentAnalysis(@RequestParam(required = false, defaultValue = "month") String period) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] intents = {"知识查询", "问题解决", "业务办理", "投诉建议", "信息查询", "操作指导", "订单查询", "技术支持"};
        for (int i = 0; i < intents.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("intent", intents[i]);
            m.put("utteranceCount", 100 + (int) (Math.random() * 900));
            m.put("accuracy", 0.75 + Math.random() * 0.25);
            m.put("coverage", 0.70 + Math.random() * 0.30);
            List<String> confused = new ArrayList<>();
            for (int j = 0; j < 2; j++) {
                confused.add(intents[(i + j + 1) % intents.length]);
            }
            m.put("topConfusedIntents", confused);
            m.put("suggestion", "建议优化意图识别模型，增加训练样本");
            rows.add(m);
        }
        return ApiResponse.success(rows);
    }
}
