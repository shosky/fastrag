package com.fastrag.module.graph.entity;

/**
 * 知识图谱实体，对应数据库表 {@code kb_graph_entity}。
 *
 * <p>表示从文档中提取的知识图谱实体节点。使用基于实体名称+类型的确定性哈希值（SHA-256截断32字符）
 * 作为主键，确保同一实体在不同构建批次中的唯一性。去重键为 (kb_id, normalized_name, entity_type)，
 * 通过标准化名称实现同名实体的自动合并。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code entityId} - 实体确定性哈希ID（SHA-256截断32字符），作为主键，由 {@link com.fastrag.module.graph.util.GraphIdHashing} 计算</li>
 *   <li>{@code kbId} - 所属知识库ID</li>
 *   <li>{@code name} - 原始显示名称，即文档中出现的实体名称</li>
 *   <li>{@code normalizedName} - 标准化名称（小写+空白归一化），用于去重匹配</li>
 *   <li>{@code originalName} - 原始名称（冗余保留，同name）</li>
 *   <li>{@code entityType} - 实体类型/标签（如 Person、Organization等）</li>
 *   <li>{@code description} - 实体描述/属性信息</li>
 *   <li>{@code createdAt} - 创建时间（自动填充）</li>
 * </ul>
 *
 * <p>与{@link KbGraphRelation}通过source/target字段关联，实体作为三元组的起点或终点。
 * 与{@link KbGraphEntityMention}为一对多关系，追踪实体在哪些文档chunk中被提及。</p>
 *
 * @see KbGraphRelation
 * @see KbGraphEntityMention
 * @see com.fastrag.module.graph.util.GraphIdHashing
 */
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

    /** 实体属性 JSON 字符串（键值对数组，如 [{"text":"500元","label":"价格"}]） */
    private String attributes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
