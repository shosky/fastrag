package com.fastrag.module.operation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.application.entity.App;
import com.fastrag.module.application.mapper.AppMapper;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.operation.model.HomeData;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.mapper.KbLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final KnowledgeBaseMapper kbMapper;
    private final KbFileMapper fileMapper;
    private final AppMapper appMapper;
    private final KbLogMapper logMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping
    public ApiResponse<?> getHomeData() {
        // 推荐知识库：最新 5 个
        List<HomeData.KbItem> kbs = kbMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .orderByDesc(KnowledgeBase::getCreatedAt)
                        .last("LIMIT 5")
        ).stream().map(kb -> HomeData.KbItem.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .category(kb.getCategory())
                .tags(parseTags(kb.getTags()))
                .creator(kb.getCreator())
                .build()
        ).collect(Collectors.toList());

        // 热门文档：浏览量最高 10 个
        List<HomeData.HotDoc> docs = fileMapper.selectList(
                new LambdaQueryWrapper<KbFile>()
                        .isNull(KbFile::getDeletedAt)
                        .eq(KbFile::getStatus, "completed")
                        .orderByDesc(KbFile::getViewCount)
                        .last("LIMIT 10")
        ).stream().map(f -> HomeData.HotDoc.builder()
                .id(f.getId())
                .name(f.getName())
                .kbId(f.getKbId())
                .viewCount(f.getViewCount() != null ? f.getViewCount() : 0)
                .updatedAt(f.getUpdatedAt() != null ? f.getUpdatedAt().format(FMT) : "")
                .build()
        ).collect(Collectors.toList());

        // 热门应用：最新 5 个
        List<HomeData.HotApp> apps = appMapper.selectList(
                new LambdaQueryWrapper<App>()
                        .orderByDesc(App::getCreatedAt)
                        .last("LIMIT 5")
        ).stream().map(a -> HomeData.HotApp.builder()
                .id(a.getId())
                .name(a.getName())
                .type(a.getType())
                .description(a.getDescription())
                .build()
        ).collect(Collectors.toList());

        // 最近动态：最新 10 条日志
        List<HomeData.ActivityLog> logs = logMapper.selectList(
                new LambdaQueryWrapper<KbLog>()
                        .orderByDesc(KbLog::getTimestamp)
                        .last("LIMIT 10")
        ).stream().map(l -> HomeData.ActivityLog.builder()
                .id(l.getId())
                .operator(l.getOperator())
                .action(l.getAction())
                .target(l.getTarget())
                .detail(l.getDetail())
                .timestamp(l.getTimestamp() != null ? l.getTimestamp().format(FMT) : "")
                .build()
        ).collect(Collectors.toList());

        HomeData data = HomeData.builder()
                .recommendKBs(kbs)
                .hotDocs(docs)
                .hotApps(apps)
                .recentActivities(logs)
                .build();

        return ApiResponse.success(data);
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return Collections.emptyList();
        try {
            // 简单解析 JSON 数组字符串 ["tag1","tag2"]
            String cleaned = tagsJson.trim();
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
                if (cleaned.isBlank()) return Collections.emptyList();
                return Arrays.stream(cleaned.split(","))
                        .map(s -> s.trim().replace("\"", "").replace("'", ""))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
