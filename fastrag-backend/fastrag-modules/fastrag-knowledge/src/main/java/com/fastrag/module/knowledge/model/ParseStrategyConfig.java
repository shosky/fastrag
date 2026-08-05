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
        /** PPT 整页解析（每页作为完整单元） */
        private Boolean enablePptWholePage = true;
        /** 视频关键帧采样间隔（秒），仅 parseMethod=video/audio 生效 */
        private Integer keyframeIntervalSeconds;
        /** 关键帧 pHash 哈希阈值 */
        private Double keyframeHashThreshold;
        /** AI 文档摘要（二期） */
        private Boolean enableDocSummary = false;
    }

    /** 分片配置组 */
    @Data
    @NoArgsConstructor
    public static class ChunkConfig {
        /** 目标长度：段落累积至接近该值再落盘，超过才按句号切分 */
        private int chunkLength = 2000;
        /** 相邻 chunk 重叠字符数 */
        private int overlap = 100;
        /** content 内嵌 Markdown 标题前缀（父级标题） */
        private boolean titlePrefix = true;
        /** 生成 title / headingPath 元数据 */
        private boolean headingPath = true;
        /** 纯文本兜底路径的分隔符（无 DocNode 时按分隔符切分） */
        private List<String> delimiters = new ArrayList<>(List.of("\n\n"));
    }

    /** 索引配置组 */
    @Data
    @NoArgsConstructor
    public static class IndexConfig {
        /** embedding 输入拼接字段（一期固定，二期开放配置） */
        private List<String> embedFields = new ArrayList<>(List.of("content", "headingPath", "fileName"));
    }
}
