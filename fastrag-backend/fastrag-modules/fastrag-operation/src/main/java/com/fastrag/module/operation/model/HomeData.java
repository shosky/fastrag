package com.fastrag.module.operation.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 运营管理首页聚合数据模型。
 *
 * <p>封装运营平台首页所需的各项数据展示内容，包括推荐知识库列表、热门文档列表、
 * 热门应用列表和最近操作活动日志。该数据由 {@code HomeController} 的接口返回，
 * 用于前端首页展示。</p>
 *
 * <p>内部类说明：</p>
 * <ul>
 *   <li>{@link KbItem} - 推荐知识库条目，包含ID、名称、描述、分类、标签和创建者</li>
 *   <li>{@link HotDoc} - 热门文档条目，包含ID、名称、所属知识库ID、浏览量、更新时间</li>
 *   <li>{@link HotApp} - 热门应用条目，包含ID、名称、类型、描述</li>
 *   <li>{@link ActivityLog} - 操作活动日志条目，包含操作者、操作类型、操作目标、详情、时间戳</li>
 * </ul>
 */
@Data
@Builder
public class HomeData {
    private List<KbItem> recommendKBs;
    private List<HotDoc> hotDocs;
    private List<HotApp> hotApps;
    private List<ActivityLog> recentActivities;

    @Data
    @Builder
    public static class KbItem {
        private String id;
        private String name;
        private String description;
        private String category;
        private List<String> tags;
        private String creator;
    }

    @Data
    @Builder
    public static class HotDoc {
        private String id;
        private String name;
        private String kbId;
        private long viewCount;
        private String updatedAt;
    }

    @Data
    @Builder
    public static class HotApp {
        private String id;
        private String name;
        private String type;
        private String description;
    }

    @Data
    @Builder
    public static class ActivityLog {
        private String id;
        private String operator;
        private String action;
        private String target;
        private String detail;
        private String timestamp;
    }
}
