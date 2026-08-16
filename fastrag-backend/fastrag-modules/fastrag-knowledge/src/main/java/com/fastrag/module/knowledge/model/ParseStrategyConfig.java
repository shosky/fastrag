package com.fastrag.module.knowledge.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析策略配置（类型化分组，替代原 advanced 自由 JSON）。
 *
 * <p>分组结构：parse（解析）/ chunk（分片）/ index（索引）。</p>
 * <p>兼容读取：旧平铺字段（chunkLength / delimiter / tableMode 等）由
 * {@code StrategyConfigResolver} 映射进新结构，新分组字段优先。</p>
 */
@Data
@NoArgsConstructor
public class ParseStrategyConfig {

    private ParseConfig parse = new ParseConfig();
    private ChunkConfig chunk = new ChunkConfig();
    private IndexConfig index = new IndexConfig();

    /** 解析配置组 */
    @Data
    @NoArgsConstructor
    public static class ParseConfig {
        /** 表格处理模式：structured / markdown / ignore */
        private String tableMode = "structured";
        /** 视频关键帧采样间隔（秒），仅 parseMethod=video/audio 生效 */
        private Integer keyframeIntervalSeconds;
        /** 关键帧 pHash 哈希阈值 */
        private Double keyframeHashThreshold;
    }

    /** 分片配置组 */
    @Data
    @NoArgsConstructor
    public static class ChunkConfig {
        /** 分片策略类型：rule_fixed / rule_recursive / structure_aware / semantic / parent_child */
        private String strategy = "rule_fixed";
        /**
         * 子分片长度（父子分片模式下为子分片目标长度；父分片按标题层级聚合，最大长度 = 该值 × 2）
         * 段落累积至接近该值再落盘，超过才按句号切分
         */
        private int chunkLength = 1000;
        /** 相邻子分片重叠字符数 */
        private int overlap = 100;
        /** content 内嵌 Markdown 标题前缀（父级标题） */
        private boolean titlePrefix = true;
        /** 生成 title / headingPath 元数据 */
        private boolean headingPath = true;
        /** 纯文本兜底路径的分隔符（无 DocNode 时按分隔符切分） */
        private List<String> delimiters = new ArrayList<>(List.of("\n\n"));
        /** 父分片最大长度（仅 parent_child 策略生效），聚合后超过按句子边界二次切分 */
        private int parentMaxChunkLength = 2000;
        /** 父分片聚合标题层级（仅 parent_child 策略生效）：H1 / H2 / H3 / auto */
        private String parentAggLevel = "auto";
        /** 语义切片阈值（0~100，对应 0.0~1.0，仅 semantic 策略生效） */
        private int semanticThreshold = 30;
        /** 语义切片 Embedding 模型 code（仅 semantic 策略生效，空 = 系统默认） */
        private String embeddingModel = "";
    }

    /** 索引配置组 */
    @Data
    @NoArgsConstructor
    public static class IndexConfig {
        /** embedding 输入拼接字段（一期固定，二期开放配置） */
        private List<String> embedFields = new ArrayList<>(List.of("content", "headingPath", "fileName"));
    }
}
