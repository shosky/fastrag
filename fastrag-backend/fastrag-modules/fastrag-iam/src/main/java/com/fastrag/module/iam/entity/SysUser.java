package com.fastrag.module.iam.entity;

/**
 * 系统用户实体类，对应数据库表 {@code sys_user}，是 IAM 模块的核心实体。
 *
 * <p>存储平台所有注册用户的基本信息、认证信息和组织归属。
 * 支持用户名密码登录和微信小程序登录两种认证方式。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code username} —— 登录用户名，唯一</li>
 *   <li>{@code realName} —— 用户真实姓名</li>
 *   <li>{@code phone} —— 手机号码</li>
 *   <li>{@code email} —— 邮箱地址</li>
 *   <li>{@code passwordHash} —— 密码哈希值（BCrypt 加密存储）</li>
 *   <li>{@code openid} —— 微信 openId，用于微信小程序登录</li>
 *   <li>{@code roleId} —— 主角色 ID（兼容字段，多角色通过 {@code SysUserRole} 表维护）</li>
 *   <li>{@code status} —— 账号状态，取值为 {@code enabled}（启用）或 {@code disabled}（禁用）</li>
 *   <li>{@code orgId} —— 所属组织/部门 ID，关联 {@code sys_org} 表</li>
 *   <li>{@code storageQuota} —— 存储配额上限（字节）</li>
 *   <li>{@code storageUsed} —— 已使用存储空间（字节）</li>
 * </ul>
 *
 * <p>由 {@code PersonnelServiceImpl} 负责用户的 CRUD 及角色分配，
 * 由 {@code AuthServiceImpl} 负责认证相关的读写操作。
 * 用户与角色的多对多关系通过 {@code SysUserRole} 中间表维护。
 *
 * @see SysUserRole
 * @see SysOrg
 * @see com.fastrag.module.iam.service.impl.PersonnelServiceImpl
 * @see com.fastrag.module.iam.service.impl.AuthServiceImpl
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String passwordHash;
    private String openid;
    private String roleId;
    private String status; // enabled / disabled
    private String orgId;
    private Long storageQuota;
    private Long storageUsed;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
