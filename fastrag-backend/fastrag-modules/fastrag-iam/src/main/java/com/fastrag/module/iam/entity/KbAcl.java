package com.fastrag.module.iam.entity;

/**
 * 知识库访问控制列表（ACL）实体类，对应数据库表 {@code kb_acl}。
 *
 * <p>用于管理知识库级别的用户访问权限，实现知识库与用户之间的多对多授权关系。
 * 每条记录表示将某个用户以特定角色授权给某个知识库。
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code kbId} —— 知识库 ID，关联知识库模块</li>
 *   <li>{@code userId} —— 被授权的用户 ID</li>
 *   <li>{@code kbRole} —— 知识库角色，取值为 {@code owner}（所有者）、{@code editor}（编辑者）、{@code viewer}（查看者）</li>
 *   <li>{@code grantedBy} —— 授权操作人用户 ID</li>
 *   <li>{@code grantedAt} —— 授权时间</li>
 * </ul>
 *
 * <p>由 {@code KbAclServiceImpl} 进行 CRUD 操作，{@code KbAclController} 提供外部接口。
 * 在查询知识库权限时，通过此实体判断用户对知识库的访问级别。
 *
 * @see com.fastrag.module.iam.service.impl.KbAclServiceImpl
 */
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_acl")
public class KbAcl {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String kbId;
    private String userId;
    private String kbRole; // owner / editor / viewer
    private String grantedBy;
    private LocalDateTime grantedAt;
}
