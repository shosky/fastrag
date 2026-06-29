package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_compliance_rule") public class KbComplianceRule {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,ruleName,ruleType,pattern,action,severity,createdBy;
    private Integer enabled;
    private LocalDateTime createdAt;
}
