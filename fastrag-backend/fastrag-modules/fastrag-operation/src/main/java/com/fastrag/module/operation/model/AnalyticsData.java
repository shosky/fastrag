package com.fastrag.module.operation.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AnalyticsData {
    private List<MetricItem> metrics;
    private List<HotKb> hotKBs;
    private List<HotDoc> hotDocs;

    @Data
    @Builder
    public static class MetricItem {
        private String label;
        private long value;
        private String change;
        private String trend; // up / down
    }

    @Data
    @Builder
    public static class HotKb {
        private int rank;
        private String id;
        private String name;
        private long docCount;
        private long viewCount;
    }

    @Data
    @Builder
    public static class HotDoc {
        private int rank;
        private String id;
        private String name;
        private String kbName;
        private long viewCount;
    }
}
