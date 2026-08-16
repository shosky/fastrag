package com.fastrag.module.iam.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 权限创建请求DTO。
 *
 * <p>封装创建权限的请求参数，包含权限键、名称、类型（menu/action）、
 * 分组、父权限键、分类和描述。被 PermissionService 的 createPermission 接口使用。</p>
 */
@Data
public class PermissionCreateRequest {
    @NotBlank private String permKey;
    @NotBlank private String name;
    @NotBlank private String type;   // menu / action
    private String group;
    private String parentKey;
    private String category; // menu / page_action / api
    private String description;
}
