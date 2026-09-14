package com.fastrag.module.retrieval.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
// query_rule 只读行实体（表由 platform 模块管理，此处仅供查询增强引擎读取）
@Data @TableName("query_rule") public class QueryRuleRow {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,ruleType,pattern,action;
    private Integer enabled,priority;
    private LocalDateTime createdAt;
}
