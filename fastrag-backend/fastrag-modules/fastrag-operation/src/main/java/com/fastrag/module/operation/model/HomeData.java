package com.fastrag.module.operation.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

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
