package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_tag_type") public class KbTagType {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,description,color,icon;
    private Integer sort,isSystem;
    private LocalDateTime createdAt,updatedAt;
}
