package com.fastrag.module.iam.entity;

/**
 * API Token 实体类，对应数据库表 {@code sys_api_token}，用于平台的程序化访问认证。
 *
 * <p>提供平台级全局 Token 机制，允许外部系统或脚本通过 Token 进行 API 调用，
 * 无需用户名密码认证。Token 创建时生成唯一值，列表查询时脱敏不返回。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code name} —— Token 名称，便于识别用途（如"数据导入脚本"）</li>
 *   <li>{@code token} —— Token 值，仅创建时返回一次，列表查询时不返回</li>
 *   <li>{@code permission} —— 权限范围，取值为 {@code read}（只读）或 {@code write}（读写）</li>
 *   <li>{@code expiresAt} —— 过期时间，{@code null} 表示永不过期</li>
 *   <li>{@code revoked} —— 是否已撤销</li>
 *   <li>{@code createdBy} —— 创建者用户 ID</li>
 * </ul>
 *
 * <p>由 {@code ApiTokenServiceImpl} 负责创建和撤销，{@code ApiTokenValidatorImpl}
 * 在 Spring Security 过滤器链中校验请求携带的 Token 是否有效且未过期。
 *
 * @see com.fastrag.module.iam.service.impl.ApiTokenServiceImpl
 * @see com.fastrag.module.iam.service.impl.ApiTokenValidatorImpl
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_api_token")
public class SysApiToken {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** Token 名称，便于识别用途 */
    private String name;

    /** Token 值（仅创建时返回，列表查询不返回） */
    private String token;

    /** 权限范围：read（只读）/ write（读写） */
    private String permission;

    /** 过期时间，null 表示永不过期 */
    private LocalDateTime expiresAt;

    /** 是否已撤销 */
    private Boolean revoked;

    /** 创建者用户 ID */
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
