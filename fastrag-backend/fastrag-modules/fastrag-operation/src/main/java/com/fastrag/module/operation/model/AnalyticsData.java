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
        /** 格式化后的展示值，百分比指标等使用此字段替代 value */
        private String displayValue;
        /** 变化量描述，如 "+12%"，为 null 时不展示趋势 */
        private String change;
        /** 趋势方向，为 null 时不展示趋势箭头 */
        private String trend; // up / down
    }

    @Data
    @Builder
    public static class HotKb {
        private int rank;
        private String name;
        private long docCount;
    }

    @Data
    @Builder
    public static class HotDoc {
        private int rank;
        private String name;
        private String kbName;
        private long viewCount;
    }
}
