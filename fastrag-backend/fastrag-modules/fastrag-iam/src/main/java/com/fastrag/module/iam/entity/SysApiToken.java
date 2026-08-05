package com.fastrag.module.iam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Token 实体，用于平台的程序化访问认证（平台级全局 Token）。
 */
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
