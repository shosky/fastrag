package com.fastrag.module.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识图谱实体（参考 Yuxi KnowledgeGraphEntity）
 * <p>使用确定性哈希 entity_id（SHA-256 截断 32 字符）作为主键，
 * 去重键为 (kb_id, normalized_name, entity_type)。</p>
 */
@Data
@TableName("kb_graph_entity")
public class KbGraphEntity {
    @TableId(type = IdType.INPUT)
    private String entityId;

    private String kbId;

    /** 原始显示名称 */
    private String name;

    /** 标准化名称（小写 + 空白归一化），用于去重匹配 */
    private String normalizedName;

    /** 原始名称（冗余保留，同 name） */
    private String originalName;

    /** 实体类型/标签 */
    private String entityType;

    /** 描述/属性（兼容字段） */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
