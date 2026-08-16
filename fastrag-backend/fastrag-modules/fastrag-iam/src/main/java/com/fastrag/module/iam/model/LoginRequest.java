package com.fastrag.module.iam.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户登录请求DTO。
 *
 * <p>封装用户登录请求的参数，包含用户名和密码。
 * 字段均带有@NotBlank校验，确保不为空。
 * 被 AuthController 的 login 接口接收。</p>
 */
@Data
public class LoginRequest {
    @NotBlank private String username;
    @NotBlank private String password;
}
