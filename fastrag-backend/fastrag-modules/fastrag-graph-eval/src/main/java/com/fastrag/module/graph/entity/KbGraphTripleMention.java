package com.fastrag.module.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 三元组提及追踪表（参考 Yuxi KnowledgeGraphTripleMention）
 * <p>记录哪些 chunk 引用了哪些三元组，用于孤儿检测和增量构建。</p>
 */
@Data
@TableName("kb_graph_triple_mention")
public class KbGraphTripleMention {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String tripleId;
    private String kbId;
    private String fileId;
    private String chunkId;

    /** 关系显示文本 */
    private String text;

    /** 提取器类型，如 "llm" */
    private String extractorType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
