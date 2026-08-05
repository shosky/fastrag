package com.fastrag.module.iam.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建 API Token 请求
 */
@Data
public class ApiTokenCreateRequest {

    @NotBlank(message = "Token 名称不能为空")
    private String name;

    @Pattern(regexp = "read|write", message = "权限范围只能是 read 或 write")
    private String permission = "read";

    /** 有效期（秒），null 表示永不过期 */
    private Long expiresIn;
}
