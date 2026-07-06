package com.fastrag.module.iam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("email_verification")
public class EmailVerification {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String email;
    private String code;
    private String purpose; // register / reset
    private LocalDateTime expiresAt;
    private Boolean used;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
