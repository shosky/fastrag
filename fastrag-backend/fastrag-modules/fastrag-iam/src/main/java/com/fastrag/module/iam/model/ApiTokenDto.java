package com.fastrag.module.iam.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Token 响应 DTO
 */
@Data
public class ApiTokenDto {

    private String id;
    private String name;
    private String token;        // 仅创建时返回
    private String permission;
    private LocalDateTime expiresAt;
    private boolean expired;
    private String createdBy;
    private LocalDateTime createdAt;
}
