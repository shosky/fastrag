package com.fastrag.module.operation.model;

/**
 * 知识库分析数据模型。
 *
 * <p>用于封装知识库维度的统计分析结果，作为 {@link com.fastrag.module.operation.controller.AnalyticsController}
 * 的响应数据返回给前端。
 *
 * <p>包含三部分数据：
 * <ul>
 *     <li>metrics - 核心指标列表（总知识库数、总文档数、活跃文档数、知识引用率等）</li>
 *     <li>hotKBs - 热门知识库排行（按文档数量排序 TOP 5）</li>
 *     <li>hotDocs - 热门文档排行（按浏览量排序 TOP 5）</li>
 * </ul>
 *
 * <p>内部嵌套类：
 * <ul>
 *     <li>{@link MetricItem} - 单个指标项，包含标签、数值、展示值和趋势信息</li>
 *     <li>{@link HotKb} - 热门知识库项，包含排名、名称和文档数量</li>
 *     <li>{@link HotDoc} - 热门文档项，包含排名、名称、所属知识库和浏览量</li>
 * </ul>
 */
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
