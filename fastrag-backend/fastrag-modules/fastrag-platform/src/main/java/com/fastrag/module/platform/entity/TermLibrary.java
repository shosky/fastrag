package com.fastrag.module.platform.entity;

/**
 * 术语库实体
 * <p>
 * 对应数据库表 {@code term_library}，存储术语库的基本信息。术语库用于组织和管理特定领域
 * 的专业词汇集合，每个术语库下可包含多个术语条目（{@link TermRecord}）。
 * termCount 字段记录当前库中的术语数量。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code name} — 术语库名称</li>
 *   <li>{@code description} — 术语库描述</li>
 *   <li>{@code owner} — 术语库负责人/所有者</li>
 *   <li>{@code termCount} — 库中术语条目数量</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.TermService
 * @see com.fastrag.module.platform.entity.TermRecord
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("term_library") public class TermLibrary {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,owner;
    private Integer termCount;
    private LocalDateTime createdAt,updatedAt;
}
