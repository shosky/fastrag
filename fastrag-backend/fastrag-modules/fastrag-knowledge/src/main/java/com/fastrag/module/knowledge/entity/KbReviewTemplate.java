package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_review_template") public class KbReviewTemplate {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,category,description,flowConfig,createdBy;
    private Integer isBuiltin;
    private LocalDateTime createdAt;

    @JsonProperty
    public void setFlowConfig(Object flowConfig) {
        if (flowConfig == null) this.flowConfig = null;
        else if (flowConfig instanceof CharSequence) this.flowConfig = flowConfig.toString();
        else try { this.flowConfig = MAPPER.writeValueAsString(flowConfig); } catch (Exception e) { this.flowConfig = flowConfig.toString(); }
    }
}
