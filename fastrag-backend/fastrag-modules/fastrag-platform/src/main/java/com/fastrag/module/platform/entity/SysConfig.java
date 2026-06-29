package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_config") public class SysConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String configKey,configValue,configType,description,updatedBy;
    private Integer isDefault,isSystem;
    private LocalDateTime createdAt,updatedAt;
}
