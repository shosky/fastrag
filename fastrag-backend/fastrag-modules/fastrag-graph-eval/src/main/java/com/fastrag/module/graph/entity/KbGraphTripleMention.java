package com.fastrag.module.graph.entity;

/**
 * 三元组提及追踪实体类，对应数据库表 {@code kb_graph_triple_mention}。
 *
 * <p>记录哪些文档chunk中提取了哪些三元组，建立关系与文档片段之间的追溯关系。
 * 该表主要用于两个场景：孤儿检测（识别不再被任何chunk引用的孤立三元组，便于清理）和
 * 增量构建（在文件更新时定位需要重新处理的三元组）。</p>
 *
 * <p>核心字段说明：</p>
 * <ul>
 *   <li>{@code id} - 自增主键</li>
 *   <li>{@code tripleId} - 被引用的三元组ID，关联 {@link KbGraphRelation#tripleId}</li>
 *   <li>{@code kbId} - 所属知识库ID</li>
 *   <li>{@code fileId} - 来源文件ID，标识三元组从哪个文件中提取</li>
 *   <li>{@code chunkId} - 来源chunkID，标识三元组在文档中的具体片段位置</li>
 *   <li>{@code text} - 关系显示文本，记录三元组的可读形式</li>
 *   <li>{@code extractorType} - 提取器类型（如"llm"），标识该三元组由哪种提取方式生成</li>
 *   <li>{@code createdAt} - 创建时间（自动填充）</li>
 * </ul>
 *
 * @see KbGraphRelation
 */
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
