package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_permission") public class BpmFlowPermission {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String flowDefId,subjectType,subjectId,permission,grantedBy;
    private LocalDateTime createdAt;
}