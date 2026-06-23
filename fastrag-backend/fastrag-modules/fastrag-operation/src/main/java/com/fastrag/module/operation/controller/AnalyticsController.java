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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final KnowledgeBaseMapper kbMapper;
    private final KbFileMapper fileMapper;

    @GetMapping("/kb")
    public ApiResponse<?> getKbAnalytics() {
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

        // 热门知识库排行（按文档数量）
        List<AnalyticsData.HotKb> hotKBs = allKBs.stream()
                .limit(10)
                .map(kb -> {
                    long docCount = fileMapper.selectCount(
                            new LambdaQueryWrapper<KbFile>()
                                    .eq(KbFile::getKbId, kb.getId())
                                    .isNull(KbFile::getDeletedAt)
                    );
                    return AnalyticsData.HotKb.builder()
                            .id(kb.getId())
                            .name(kb.getName())
                            .docCount(docCount)
                            .viewCount(0)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getDocCount(), a.getDocCount()))
                .limit(5)
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
                .last("LIMIT 5");
        List<KbFile> topFiles = fileMapper.selectList(hotDocQuery);

        List<AnalyticsData.HotDoc> hotDocs = topFiles.stream()
                .map(f -> {
                    KnowledgeBase kb = kbMapper.selectById(f.getKbId());
                    return AnalyticsData.HotDoc.builder()
                            .id(f.getId())
                            .name(f.getName())
                            .kbName(kb != null ? kb.getName() : "")
                            .viewCount(f.getViewCount() != null ? f.getViewCount() : 0)
                            .build();
                })
                .collect(Collectors.toList());

        // 设置排名
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
