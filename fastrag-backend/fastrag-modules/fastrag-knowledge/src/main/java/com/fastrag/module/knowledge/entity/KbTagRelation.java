package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_tag_relation") public class KbTagRelation {
    @TableId(type=IdType.AUTO) private Long id;
    private String tagId,targetType,targetId;
    private LocalDateTime createdAt;
}
