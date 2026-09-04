package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_version") public class BpmFlowVersion {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String flowDefId;
    private Integer versionNo;
    private String status,canvasData,nodesSnapshot,edgesSnapshot,remark,publisherId;
    private LocalDateTime publishedAt,createdAt;
}