package com.fastrag.module.platform.entity;

/**
 * 术语条目实体
 * <p>
 * 对应数据库表 {@code term_record}，存储具体的专业术语条目。每个术语条目归属于一个术语库
 * （{@link TermLibrary}），包含术语名称、别名、定义和分类信息。
 * 用于RAG系统的术语增强，确保模型对领域专业词汇的准确理解。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code libraryId} — 所属术语库ID，关联 {@link TermLibrary#getId()}</li>
 *   <li>{@code term} — 术语名称</li>
 *   <li>{@code alias} — 术语别名（同义词、缩写等）</li>
 *   <li>{@code definition} — 术语定义/解释</li>
 *   <li>{@code category} — 术语分类</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.TermService
 * @see com.fastrag.module.platform.mapper.TermRecordMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("term_record") public class TermRecord {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String libraryId,term,alias,definition,category;
    private Integer status;
    private LocalDateTime createdAt;
}
