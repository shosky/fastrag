package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_reset_config") public class KbResetConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,roleKey,config;
    private Integer canReset,maxResetCount;
    private LocalDateTime createdAt;
}
