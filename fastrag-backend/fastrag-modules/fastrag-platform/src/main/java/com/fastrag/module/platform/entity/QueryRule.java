package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("query_rule") public class QueryRule {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,ruleType,pattern,action;
    private Integer enabled,priority;
    private LocalDateTime createdAt;
}
