package com.fastrag.module.iam.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求DTO。
 *
 * <p>封装用户注册请求的参数，包含用户名（3-64字符）、邮箱、密码（6-64字符）和邮箱验证码。
 * 带有完整的参数校验注解。被 AuthController 的 register 接口接收。</p>
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度为3-64个字符")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度为6-64个字符")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
