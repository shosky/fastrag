package com.fastrag.module.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识图谱关系/三元组（参考 Yuxi KnowledgeGraphTriple）
 * <p>使用确定性哈希 triple_id（SHA-256 截断 32 字符）作为主键，
 * 去重键为 (kb_id, source, target, label)。</p>
 */
@Data
@TableName("kb_graph_relation")
public class KbGraphRelation {
    @TableId(type = IdType.INPUT)
    private String tripleId;

    private String kbId;

    /** 源实体名称 */
    private String source;

    /** 目标实体名称 */
    private String target;

    /** 关系类型 */
    private String label;

    /** 关系显示文本，如 "实体A -> RELATED_TO -> 实体B" */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
