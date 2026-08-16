package com.fastrag.module.tools.entity;

/**
 * 数据库实例实体，对应数据库表 {@code db_instance}。
 *
 * <p>管理外部数据库连接信息，支持 MySQL、PostgreSQL 等多种数据库类型。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} - 主键（雪花 ID）</li>
 *   <li>{@code name} - 实例名称</li>
 *   <li>{@code dbType} - 数据库类型（mysql/postgresql 等）</li>
 *   <li>{@code host / port / dbName} - 连接地址</li>
 *   <li>{@code username / password} - 认证凭据</li>
 *   <li>{@code jdbcUrl} - JDBC 连接 URL</li>
 *   <li>{@code orgId} - 归属组织（同组织可见）</li>
 *   <li>{@code status} - 连接状态</li>
 * </ul>
 *
 * @see DbTable
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("db_instance") public class DbInstance {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String name,description,dbType,host,username,password,dbName,jdbcUrl,poolConfig,status,createdBy;
    private String orgId; // 归属组织（同组织可见，创建时写入）
    private Integer port,readOnly; private LocalDateTime createdAt,updatedAt;
}
