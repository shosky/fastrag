package com.fastrag.module.iam.entity;

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
    private String roleId;
    private String status; // enabled / disabled
    private String orgId;
    private Long storageQuota;
    private Long storageUsed;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
