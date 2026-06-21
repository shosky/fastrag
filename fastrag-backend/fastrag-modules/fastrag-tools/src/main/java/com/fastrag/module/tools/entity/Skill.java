package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("skill") public class Skill {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,identifier,description,icon,source,category,trigger,content,codeType,code,inputs,outputs,author,version;
    private Integer enabled,recommended,usageCount;
    private LocalDateTime updatedAt;
}
