package com.fastrag.module.operation.model;

import com.fastrag.common.response.PageResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模型监控分析 — 总览面板响应数据
 */
@Data
@Builder
public class ModelMonitorData {

    /** 4 个指标卡片 */
    private List<MetricItem> metrics;

    /** 模型使用分布 */
    private List<ModelDistItem> distribution;

    /** 高消耗应用排行 */
    private List<TopAppItem> topApps;

    /** 模型调用统计（分页） */
    private PageResult<ModelStatsItem> stats;

    @Data
    @Builder
    public static class MetricItem {
        private String label;
        private String value;     // "1,234,567"
        private String change;    // "+12.3%"
        private String trend;     // "up" / "down"
    }

    @Data
    @Builder
    public static class ModelDistItem {
        private String name;       // 模型 Code
        private double percentage; // 占比（如 45.0）
        private String token;      // "556,780"
    }

    @Data
    @Builder
    public static class TopAppItem {
        private int rank;
        private String name;       // 应用名称（caller）
        private String token;      // "456,780"
        private String cost;       // "￥2,283.90" 或 "—"
    }

    @Data
    @Builder
    public static class ModelStatsItem {
        private String code;       // 模型 Code
        private long calls;        // 调用总量
        private long fails;        // 失败量
        private String token;      // Token 消耗
        private String cost;       // 消耗金额
    }
}
