package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_tag") public class KbTag {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,tagTypeId,name,color,description,createdBy;
    private Integer usageCount;
    private LocalDateTime createdAt;
}
