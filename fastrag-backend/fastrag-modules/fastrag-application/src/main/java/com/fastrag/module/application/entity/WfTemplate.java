package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("wf_template") public class WfTemplate {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String name,category,description,canvasData,createdBy;
    private Integer isBuiltin; private LocalDateTime createdAt;
}
