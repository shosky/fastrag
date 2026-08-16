package com.fastrag.module.iam.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/**
 * 角色创建请求DTO。
 *
 * <p>封装创建角色的请求参数，包含角色名称（必填）、角色描述和权限列表。
 * 被 RoleService 的 createRole 接口使用。</p>
 */
@Data
public class RoleCreateRequest {
    @NotBlank private String name;
    private String description;
    private List<String> permissions;
}
