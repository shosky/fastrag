package com.fastrag.module.iam.entity;

/**
 * 系统权限项实体类，对应数据库表 {@code sys_permission}。
 *
 * <p>定义系统中所有的可授权权限项，权限项按树形结构组织（通过 {@code parentKey} 关联）。
 * 每个权限项代表一个可被分配给角色的操作或功能入口。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code permKey} —— 权限唯一标识，如 {@code admin:user}、{@code kb:manage}</li>
 *   <li>{@code name} —— 权限显示名称</li>
 *   <li>{@code type} —— 权限类型，取值为 {@code menu}（菜单）或 {@code action}（操作）</li>
 *   <li>{@code group} —— 权限分组，取值为 menu / kb / app / workflow / review / admin</li>
 *   <li>{@code parentKey} —— 父权限的 permKey，用于构建权限树</li>
 *   <li>{@code category} —— 权限分类，取值为 menu / page_action / api</li>
 *   <li>{@code description} —— 权限描述信息</li>
 * </ul>
 *
 * <p>通过 {@code SysRolePermission} 实体将权限绑定到角色，实现 RBAC 权限模型。
 * 由 {@code PermissionServiceImpl} 负责权限的 CRUD 和树形结构查询。
 *
 * @see SysRolePermission
 * @see SysRole
 * @see com.fastrag.module.iam.service.impl.PermissionServiceImpl
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String permKey;
    private String name;
    private String type; // menu / action

    @TableField("`group`")
    private String group; // menu / kb / app / workflow / review / admin
    private String parentKey;
    private String category; // menu / page_action / api
    private String description;
}
