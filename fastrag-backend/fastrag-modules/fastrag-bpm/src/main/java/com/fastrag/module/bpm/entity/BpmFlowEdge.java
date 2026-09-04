package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_edge") public class BpmFlowEdge {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String versionId,sourceNodeKey,targetNodeKey,edgeKind;
    private String conditionExpr,conditionParams,label;
    private Integer priority;
    private LocalDateTime createdAt;
}