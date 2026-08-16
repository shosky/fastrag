package com.fastrag.module.graph.entity;

/**
 * 知识图谱关系/三元组，对应数据库表 {@code kb_graph_relation}。
 *
 * <p>表示知识图谱中的实体间关系，即三元组（源实体 -> 关系 -> 目标实体）。
 * 使用基于源实体+目标实体+关系类型的确定性哈希值（SHA-256截断32字符）作为主键，
 * 确保同一三元组在不同构建批次中的唯一性。去重键为 (kb_id, source, target, label)。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code tripleId} - 三元组确定性哈希ID（SHA-256截断32字符），作为主键</li>
 *   <li>{@code kbId} - 所属知识库ID</li>
 *   <li>{@code source} - 源实体名称（三元组的主体）</li>
 *   <li>{@code target} - 目标实体名称（三元组的客体）</li>
 *   <li>{@code label} - 关系类型（如 RELATED_TO、PART_OF等）</li>
 *   <li>{@code content} - 关系显示文本，格式为"实体A -> 关系类型 -> 实体B"</li>
 *   <li>{@code createdAt} - 创建时间（自动填充）</li>
 * </ul>
 *
 * <p>与{@link KbGraphEntity}通过source/target字段（实体名称）关联。
 * 与{@link KbGraphTripleMention}为一对多关系，追踪三元组在哪些文档chunk中被提取。</p>
 *
 * @see KbGraphEntity
 * @see KbGraphTripleMention
 * @see com.fastrag.module.graph.util.GraphIdHashing
 */
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
