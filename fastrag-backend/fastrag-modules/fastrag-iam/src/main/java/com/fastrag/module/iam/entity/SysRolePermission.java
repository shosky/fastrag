package com.fastrag.module.iam.entity;

/**
 * 角色-权限关联实体类，对应数据库表 {@code sys_role_permission}。
 *
 * <p>实现角色与权限的多对多关联关系，是 RBAC 权限模型的关键中间表。
 * 每条记录表示将某个权限项分配给某个角色。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code roleId} —— 角色 ID，关联 {@code sys_role} 表</li>
 *   <li>{@code permissionKey} —— 权限标识，关联 {@code sys_permission} 表的 {@code permKey}</li>
 * </ul>
 *
 * <p>当角色创建或更新时，通过此表维护角色拥有的权限集合。
 * 用户权限查询时，先通过 {@code SysUserRole} 获取用户的所有角色，
 * 再通过此表获取各角色关联的权限，最终合并得到用户的完整权限集。
 *
 * @see SysRole
 * @see SysPermission
 * @see SysUserRole
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_role_permission")
public class SysRolePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleId;
    private String permissionKey;
}
