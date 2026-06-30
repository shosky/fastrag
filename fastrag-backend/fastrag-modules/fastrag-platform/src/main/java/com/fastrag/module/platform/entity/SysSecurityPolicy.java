package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_security_policy") public class SysSecurityPolicy {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,policyType,pattern,action,description;
    private Integer priority,enabled;
    private LocalDateTime createdAt,updatedAt;
}
