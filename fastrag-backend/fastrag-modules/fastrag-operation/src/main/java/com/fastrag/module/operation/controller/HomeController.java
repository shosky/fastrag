package com.fastrag.module.operation.controller;

/**
 * 首页数据聚合控制器。
 *
 * <p>提供系统首页所需的全局数据聚合接口，包括推荐知识库、热门文档、热门应用和最近动态。
 * 所有数据均基于当前登录用户的权限进行过滤：API Token用户不过滤，普通用户按组织+ACL过滤。
 *
 * <p>核心逻辑：
 * <ul>
 *     <li>推荐知识库：按创建时间倒序取最新5个有权限的知识库</li>
 *     <li>热门文档：按浏览量倒序取TOP 10已完成处理的文档</li>
 *     <li>热门应用：按创建时间倒序取最新5个应用（需具备 app:use 权限）</li>
 *     <li>最近动态：按时间倒序取最新10条操作日志</li>
 * </ul>
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/home - 获取首页聚合数据（推荐知识库、热门文档、热门应用、最近动态）</li>
 * </ul>
 *
 * <p>依赖模块：fastrag-knowledge（KnowledgeBaseMapper、KbFileMapper）、fastrag-application（AppMapper）、
 * fastrag-publish（KbLogMapper）、fastrag-security（KbAccessChecker、SecurityUtil）
 */
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
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.service.KbAccessChecker;
import com.fastrag.security.util.SecurityUtil;
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
    private final KbAccessChecker accessChecker;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping
    public ApiResponse<?> getHomeData() {
        // 可访问的知识库列表：超管 / API Token 返回 null（不过滤），普通用户按 ACL
        List<String> accessible = accessibleKbIds();
        if (accessible != null && accessible.isEmpty()) {
            return ApiResponse.success(HomeData.builder()
                    .recommendKBs(Collections.emptyList())
                    .hotDocs(Collections.emptyList())
                    .hotApps(Collections.emptyList())
                    .recentActivities(Collections.emptyList())
                    .build());
        }

        // 推荐知识库：最新 5 个（仅限有访问权限的库）
        var kbW = new LambdaQueryWrapper<KnowledgeBase>();
        if (accessible != null) kbW.in(KnowledgeBase::getId, accessible);
        List<HomeData.KbItem> kbs = kbMapper.selectList(
                kbW.orderByDesc(KnowledgeBase::getCreatedAt).last("LIMIT 5")
        ).stream().map(kb -> HomeData.KbItem.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .category(kb.getCategory())
                .tags(parseTags(kb.getTags()))
                .creator(kb.getCreator())
                .build()
        ).collect(Collectors.toList());

        // 热门文档：浏览量最高 10 个（仅限有访问权限的库）
        var fW = new LambdaQueryWrapper<KbFile>()
                .isNull(KbFile::getDeletedAt)
                .eq(KbFile::getStatus, "completed");
        if (accessible != null) fW.in(KbFile::getKbId, accessible);
        List<HomeData.HotDoc> docs = fileMapper.selectList(
                fW.orderByDesc(KbFile::getViewCount).last("LIMIT 10")
        ).stream().map(f -> HomeData.HotDoc.builder()
                .id(f.getId())
                .name(f.getName())
                .kbId(f.getKbId())
                .viewCount(f.getViewCount() != null ? f.getViewCount() : 0)
                .updatedAt(f.getUpdatedAt() != null ? f.getUpdatedAt().format(FMT) : "")
                .build()
        ).collect(Collectors.toList());

        // 热门应用：最新 5 个（无应用使用权限的用户不返回任何应用）
        List<HomeData.HotApp> apps = canUseApp() ? appMapper.selectList(
                new LambdaQueryWrapper<App>()
                        .orderByDesc(App::getCreatedAt)
                        .last("LIMIT 5")
        ).stream().map(a -> HomeData.HotApp.builder()
                .id(a.getId())
                .name(a.getName())
                .type(a.getType())
                .description(a.getDescription())
                .build()
        ).collect(Collectors.toList()) : Collections.emptyList();

        // 最近动态：最新 10 条日志（仅限有访问权限的库）
        var lW = new LambdaQueryWrapper<KbLog>();
        if (accessible != null) lW.in(KbLog::getKbId, accessible);
        List<HomeData.ActivityLog> logs = logMapper.selectList(
                lW.orderByDesc(KbLog::getTimestamp).last("LIMIT 10")
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

    /**
     * 当前用户可访问的知识库 ID。
     * 仅平台级 API Token（程序化访问）返回 null（不过滤）；所有登录用户（含超管/kb_admin）按本组织 ∪ ACL 过滤。
     */
    private List<String> accessibleKbIds() {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null) return Collections.emptyList();
        if (user.getUserId().startsWith("api-token:")) return null;
        return accessChecker.getAccessibleKbIds(user.getUserId(), user.getOrgId());
    }

    /** 当前用户是否可使用应用（超管 / API Token 放行） */
    private boolean canUseApp() {
        LoginUser user = SecurityUtil.getCurrentUser();
        if (user == null) return false;
        return user.hasPermission("*") || user.getUserId().startsWith("api-token:") || user.hasPermission("app:use");
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
