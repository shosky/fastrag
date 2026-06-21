package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("tool") public class Tool {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,identifier,description,type,tags,icon,inputs;
    private Integer enabled;
    private LocalDateTime createdAt;
}
