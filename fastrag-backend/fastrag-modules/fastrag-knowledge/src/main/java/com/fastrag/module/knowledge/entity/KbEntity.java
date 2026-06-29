package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_entity") public class KbEntity {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,entityType,description,valuesJson,creator;
    private LocalDateTime createdAt,updatedAt;
}
