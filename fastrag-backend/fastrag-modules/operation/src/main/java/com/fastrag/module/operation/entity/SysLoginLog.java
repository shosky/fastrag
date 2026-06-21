package com.fastrag.module.operation.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_login_log") public class SysLoginLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String userId,username,ip,device,os,browser,location,status,failReason;
    private LocalDateTime loginTime;
}
