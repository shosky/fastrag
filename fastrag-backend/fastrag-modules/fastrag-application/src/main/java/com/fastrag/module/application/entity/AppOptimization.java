package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_optimization") public class AppOptimization {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,suggestionType,title,description,status,beforeMetric,afterMetric,createdBy;
    private Double impactScore; private LocalDateTime createdAt;
}
