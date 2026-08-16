package com.fastrag.module.iam.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码重置请求DTO。
 *
 * <p>封装密码重置请求的参数，包含注册邮箱、邮箱验证码和新密码。
 * 通过发送邮件验证码验证用户身份后重置密码。被 AuthController 的 reset-password 接口接收。</p>
 */
@Data
public class ResetPasswordRequest {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度为6-64个字符")
    private String newPassword;
}
