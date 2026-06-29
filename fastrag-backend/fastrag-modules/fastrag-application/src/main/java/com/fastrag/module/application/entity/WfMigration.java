package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("wf_migration") public class WfMigration {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String sourceWorkflowId,targetWorkflowId,targetEnv,strategy,status,validateResult,operator;
    private Integer progress; private LocalDateTime createdAt;
}
