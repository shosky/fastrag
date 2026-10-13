package com.fastrag.module.bpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 业务流优化建议（wf_optimization）。前端「名称」即 title。 */
@Data
@TableName("wf_optimization")
public class WfOptimization {
    @TableId
    private String id;
    private String workflowId;
    private String suggestionType;
    private String title;
    private String description;
    private BigDecimal impactScore;
    private String status;
    private String beforeMetric;
    private String afterMetric;
    private LocalDateTime createdAt;
}
