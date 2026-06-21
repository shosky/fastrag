package com.fastrag.module.operation.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_audit_log") public class SysAuditLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String userId,username,module,action,target,detail,ip;
    private LocalDateTime timestamp;
}
