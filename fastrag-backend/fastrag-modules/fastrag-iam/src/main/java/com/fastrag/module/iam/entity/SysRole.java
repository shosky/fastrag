package com.fastrag.module.iam.entity;

/**
 * 系统角色实体类，对应数据库表 {@code sys_role}。
 *
 * <p>作为 RBAC（基于角色的访问控制）模型的核心，角色是权限的集合。
 * 用户通过关联角色获得相应权限，一个用户可关联多个角色。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code roleKey} —— 角色唯一标识，如 {@code admin}、{@code user}</li>
 *   <li>{@code name} —— 角色显示名称</li>
 *   <li>{@code description} —— 角色描述</li>
 *   <li>{@code isDefault} —— 是否为默认角色（新用户注册时自动分配），1=是，0=否</li>
 *   <li>{@code isSystem} —— 是否为系统内置角色（系统角色不可删除），1=是，0=否</li>
 * </ul>
 *
 * <p>角色的权限通过 {@code SysRolePermission} 关联到 {@code SysPermission}，
 * 用户与角色的关联通过 {@code SysUserRole} 维护。
 * 由 {@code RoleServiceImpl} 负责角色的 CRUD 及默认角色设置。
 *
 * @see SysRolePermission
 * @see SysUserRole
 * @see SysPermission
 * @see com.fastrag.module.iam.service.impl.RoleServiceImpl
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String roleKey;
    private String name;
    private String description;
    private Integer isDefault;
    private Integer isSystem;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
