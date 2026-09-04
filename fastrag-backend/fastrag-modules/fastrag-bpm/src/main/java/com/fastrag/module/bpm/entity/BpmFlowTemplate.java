package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_template") public class BpmFlowTemplate {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,category,description,canvasData,thumbnailUrl,createdBy;
    private Boolean isBuiltin;
    private LocalDateTime createdAt;
}