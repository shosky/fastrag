package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("wf_optimization") public class WfOptimization {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String workflowId, suggestionType, title, description;
    private Double impactScore;
    private String status;
    private String beforeMetric, afterMetric;
    private LocalDateTime createdAt;
}
