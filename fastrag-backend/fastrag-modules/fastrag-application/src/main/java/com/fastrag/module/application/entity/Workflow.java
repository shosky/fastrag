package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("workflow") public class Workflow {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,status,nodes,edges,createdBy;
    private LocalDateTime createdAt,updatedAt;
}
