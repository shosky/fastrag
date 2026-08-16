package com.fastrag.module.graph.entity;

/**
 * 实体提及追踪实体类，对应数据库表 {@code kb_graph_entity_mention}。
 *
 * <p>记录哪些文档chunk引用了哪些实体，建立实体与文档片段之间的追溯关系。
 * 该表主要用于两个场景：孤儿检测（识别不再被任何chunk引用的孤立实体，便于清理）和
 * 增量构建（在文件更新时定位需要重新处理的实体）。每条记录表示一个实体在某chunk中被提及。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code id} - 自增主键</li>
 *   <li>{@code entityId} - 被引用的实体ID，关联 {@link KbGraphEntity#entityId}</li>
 *   <li>{@code kbId} - 所属知识库ID</li>
 *   <li>{@code fileId} - 来源文件ID，标识实体从哪个文件中提取</li>
 *   <li>{@code chunkId} - 来源chunkID，标识实体在文档中的具体片段位置</li>
 *   <li>{@code createdAt} - 创建时间（自动填充）</li>
 * </ul>
 *
 * @see KbGraphEntity
 */
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
