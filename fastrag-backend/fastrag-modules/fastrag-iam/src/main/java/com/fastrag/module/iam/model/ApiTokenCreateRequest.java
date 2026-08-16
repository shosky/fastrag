package com.fastrag.module.iam.model;

/**
 * 创建 API Token 请求模型。
 *
 * <p>用于 {@code POST /api/api-tokens} 接口的请求体，定义创建 API Token 时所需的参数。
 * 包含字段级别的 Jakarta Validation 校验注解，确保请求参数合法性。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code name} —— Token 名称（必填），便于识别用途</li>
 *   <li>{@code permission} —— 权限范围，取值为 {@code read}（只读）或 {@code write}（读写），默认为 {@code read}</li>
 *   <li>{@code expiresIn} —— 有效期（秒），{@code null} 表示永不过期</li>
 * </ul>
 *
 * @see com.fastrag.module.iam.controller.ApiTokenController
 * @see com.fastrag.module.iam.service.impl.ApiTokenServiceImpl
 */
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
