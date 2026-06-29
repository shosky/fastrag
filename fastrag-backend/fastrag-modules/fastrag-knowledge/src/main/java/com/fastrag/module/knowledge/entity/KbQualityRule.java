package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_quality_rule") public class KbQualityRule {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,ruleName,metric,createdBy;
    private Integer enabled;
    private Double threshold,weight;
    private LocalDateTime createdAt;
}
