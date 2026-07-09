package com.fastrag.module.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 实体提及追踪表（参考 Yuxi KnowledgeGraphEntityMention）
 * <p>记录哪些 chunk 引用了哪些实体，用于孤儿检测和增量构建。</p>
 */
@Data
@TableName("kb_graph_entity_mention")
public class KbGraphEntityMention {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String entityId;
    private String kbId;
    private String fileId;
    private String chunkId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
