package com.fastrag.module.tools.entity;

/**
 * 数据库表实体，对应数据库表 {@code db_table}。
 *
 * <p>记录通过 {@link DbInstance} 同步获取的数据库表结构信息，
 * 支持按表启用/禁用供 Agent 工具调用时使用。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} - 主键（雪花 ID）</li>
 *   <li>{@code dbId} - 所属数据库实例 ID</li>
 *   <li>{@code tableName} - 表名</li>
 *   <li>{@code columns} - 列结构信息（JSON）</li>
 *   <li>{@code rowCount} - 表行数</li>
 *   <li>{@code syncedAt} - 最近同步时间</li>
 * </ul>
 *
 * @see DbInstance
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("db_table") public class DbTable {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String dbId,tableName,tableComment,columns;
    private Long rowCount; private Integer enabled; private LocalDateTime syncedAt;
}
