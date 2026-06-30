package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_publish_strategy") public class SysPublishStrategy {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,strategyType,config,description;
    private Integer priority,enabled;
    private LocalDateTime createdAt,updatedAt;
}
