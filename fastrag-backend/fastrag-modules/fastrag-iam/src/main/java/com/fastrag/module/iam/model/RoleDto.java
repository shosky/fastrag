package com.fastrag.module.iam.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色信息DTO。
 *
 * <p>表示系统中一个角色的完整信息，包含角色ID、角色键、名称、描述、
 * 是否为默认角色、是否为系统内置角色、权限列表、创建时间和更新时间。
 * 用于角色管理界面展示和角色分配。</p>
 */
@Data
public class RoleDto {
    private String id;
    private String roleKey;
    private String name;
    private String description;
    private boolean isDefault;
    private boolean isSystem;
    private List<String> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
