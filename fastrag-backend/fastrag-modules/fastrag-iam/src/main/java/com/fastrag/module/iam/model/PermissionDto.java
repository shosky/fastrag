package com.fastrag.module.iam.model;

import lombok.Data;
import java.util.List;

/**
 * 权限信息DTO。
 *
 * <p>表示系统中一个权限项的完整信息，包含权限ID、权限键、名称、类型（menu/action）、
 * 分组（menu/kb/app/workflow/review/admin）、父权限键、分类、描述、
 * 拥有此权限的角色ID列表和子权限列表。用于权限树的构建和权限管理界面展示。</p>
 */
@Data
public class PermissionDto {
    private Long id;
    private String permKey;
    private String name;
    private String type;   // menu / action
    private String group;  // menu / kb / app / workflow / review / admin
    private String parentKey;
    private String category; // menu / page_action / api
    private String description;
    private List<String> roleIds;
    private List<PermissionDto> children;
}
