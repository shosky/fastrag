package com.fastrag.module.operation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.operation.model.AnalyticsData;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final KnowledgeBaseMapper kbMapper;
    private final KbFileMapper fileMapper;
    private final KbAccessChecker accessChecker;

    /**
     * 当前用户可见的知识库范围：
     * API Token 返回 null（不过滤）；普通用户返回本组织 ∪ ACL；无任何可见库时返回不可能条件集合。
     */
    private List<String> accessibleKbIds() {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null) return List.of("__none__");
        if (user.getUserId().startsWith("api-token:")) return null;
        List<String> ids = accessChecker.getAccessibleKbIds(user.getUserId(), user.getOrgId());
        return ids.isEmpty() ? List.of("__none__") : ids;
    }

    @GetMapping("/kb")
    public ApiResponse<?> getKbAnalytics() {
        List<String> accessible = accessibleKbIds();

        // 总知识库数量（使用 selectCount）
        var kbCountW = new LambdaQueryWrapper<KnowledgeBase>();
        if (accessible != null) kbCountW.in(KnowledgeBase::getId, accessible);
        long totalKBs = kbMapper.selectCount(kbCountW);

        // 总文档数（未删除）
        var fileCountW = new LambdaQueryWrapper<KbFile>().isNull(KbFile::getDeletedAt);
        if (accessible != null) fileCountW.in(KbFile::getKbId, accessible);
        long totalFiles = fileMapper.selectCount(fileCountW);

        // 活跃文档数（已完成处理）
        long activeFiles = fileMapper.selectCount(
                new LambdaQueryWrapper<KbFile>()
                        .isNull(KbFile::getDeletedAt)
                        .eq(KbFile::getStatus, "completed")
                        .in(accessible != null, KbFile::getKbId, accessible != null ? accessible : List.of())
        );

        // 知识引用率
        long filesWithChunks = fileMapper.selectCount(
                new LambdaQueryWrapper<KbFile>()
                        .isNull(KbFile::getDeletedAt)
                        .eq(KbFile::getStatus, "completed")
                        .gt(KbFile::getChunkCount, 0)
                        .in(accessible != null, KbFile::getKbId, accessible != null ? accessible : List.of())
        );
        String citationRate = activeFiles > 0
                ? String.format("%.1f%%", (double) filesWithChunks / activeFiles * 100)
                : "0%";

        List<AnalyticsData.MetricItem> metrics = List.of(
                AnalyticsData.MetricItem.builder()
                        .label("总知识库数量").value(totalKBs)
                        .displayValue(String.valueOf(totalKBs))
                        .build(),
                AnalyticsData.MetricItem.builder()
                        .label("总文档数").value(totalFiles)
                        .displayValue(String.valueOf(totalFiles))
                        .build(),
                AnalyticsData.MetricItem.builder()
                        .label("活跃文档数量").value(activeFiles)
                        .displayValue(String.valueOf(activeFiles))
                        .build(),
                AnalyticsData.MetricItem.builder()
                        .label("知识引用率").value(0)
                        .displayValue(citationRate)
                        .build()
        );

        // 热门知识库排行（按文档数量，使用原生 selectMaps + GROUP BY + COUNT(*) 聚合）
        // 使用 last() 避免 MyBatis-Plus 在 ORDER BY 中引入非聚合列导致 only_full_group_by 错误
        List<Map<String, Object>> kbStats = fileMapper.selectMaps(
                new LambdaQueryWrapper<KbFile>()
                        .isNull(KbFile::getDeletedAt)
                        .in(accessible != null, KbFile::getKbId, accessible != null ? accessible : List.of())
                        .select(KbFile::getKbId)
                        .groupBy(KbFile::getKbId)
                        .last("ORDER BY COUNT(*) DESC LIMIT 5")
        );

        // 批量查询 KB 名称
        List<String> kbIds = kbStats.stream()
                .map(m -> String.valueOf(m.get("kb_id")))
                .collect(Collectors.toList());
        Map<String, String> kbNameMap = kbIds.isEmpty() ? Map.of() :
                kbMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBase>().in(KnowledgeBase::getId, kbIds)
                ).stream().collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));

        // 对每个 KB 查文档数（仅 5 次，可接受）
        List<AnalyticsData.HotKb> hotKBs = new java.util.ArrayList<>();
        for (int i = 0; i < kbStats.size(); i++) {
            String kbId = String.valueOf(kbStats.get(i).get("kb_id"));
            long docCount = fileMapper.selectCount(
                    new LambdaQueryWrapper<KbFile>()
                            .eq(KbFile::getKbId, kbId)
                            .isNull(KbFile::getDeletedAt)
            );            hotKBs.add(AnalyticsData.HotKb.builder()
                    .name(kbNameMap.getOrDefault(kbId, kbId))
                    .docCount(docCount)
                    .rank(i + 1)
                    .build());
        }

        // 热门文档排行（按浏览量）
        List<KbFile> topFiles = fileMapper.selectList(
                new LambdaQueryWrapper<KbFile>()
                        .isNull(KbFile::getDeletedAt)
                        .eq(KbFile::getStatus, "completed")
                        .in(accessible != null, KbFile::getKbId, accessible != null ? accessible : List.of())
                        .orderByDesc(KbFile::getViewCount)
                        .last("LIMIT 5")
        );

        // 批量查询文档所属 KB 名称
        List<String> docKbIds = topFiles.stream()
                .map(KbFile::getKbId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> docKbNameMap = docKbIds.isEmpty() ? Map.of() :
                kbMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBase>().in(KnowledgeBase::getId, docKbIds)
                ).stream().collect(Collectors.toMap(KnowledgeBase::getId, KnowledgeBase::getName));

        List<AnalyticsData.HotDoc> hotDocs = topFiles.stream()
                .map(f -> AnalyticsData.HotDoc.builder()
                        .name(f.getName())
                        .kbName(docKbNameMap.getOrDefault(f.getKbId(), ""))
                        .viewCount(f.getViewCount() != null ? f.getViewCount() : 0)
                        .build())
                .collect(Collectors.toList());

        for (int i = 0; i < hotDocs.size(); i++) {
            hotDocs.get(i).setRank(i + 1);
        }

        AnalyticsData data = AnalyticsData.builder()
                .metrics(metrics)
                .hotKBs(hotKBs)
                .hotDocs(hotDocs)
                .build();

        return ApiResponse.success(data);
    }
}
