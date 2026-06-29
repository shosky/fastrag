package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_listener") public class KbListener {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,listenType,target,config,status,createdBy;
    private LocalDateTime lastRunAt,createdAt;
}
