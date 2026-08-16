package com.fastrag.module.iam.entity;

/**
 * 用户-角色关联实体类，对应数据库表 {@code sys_user_role}。
 *
 * <p>实现用户与角色的多对多关联关系，是 RBAC 权限模型的关键中间表。
 * 每条记录表示将某个角色分配给某个用户。一个用户可拥有多个角色，
 * 一个角色可分配给多个用户。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code userId} —— 用户 ID，关联 {@code sys_user} 表</li>
 *   <li>{@code roleId} —— 角色 ID，关联 {@code sys_role} 表</li>
 * </ul>
 *
 * <p>由 {@code PersonnelServiceImpl} 在分配角色时写入，在权限校验时通过此表
 * 结合 {@code SysRolePermission} 表查询用户的完整权限集合。
 *
 * @see SysUser
 * @see SysRole
 * @see SysRolePermission
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class SysUserRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String roleId;
}
