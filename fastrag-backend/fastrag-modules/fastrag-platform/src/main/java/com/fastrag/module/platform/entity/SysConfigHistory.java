package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_config_history") public class SysConfigHistory {
    @TableId(type=IdType.AUTO) private Long id;
    private String configId,configKey,oldValue,newValue,changeType,operator;
    private LocalDateTime timestamp;
}
