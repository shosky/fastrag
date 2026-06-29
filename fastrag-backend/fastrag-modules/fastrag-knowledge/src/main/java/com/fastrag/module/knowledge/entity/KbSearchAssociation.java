package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_search_association") public class KbSearchAssociation {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,dimension,name,description,pattern,suggestions,conditions,createdBy;
    private Integer priority,enabled;
    private LocalDateTime createdAt,updatedAt;
}
