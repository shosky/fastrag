package com.fastrag.module.retrieval.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_search_preference") public class KbSearchPreference {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,userId,name,searchMode;
    private Integer topK,enabled;
    private Double similarityThreshold;
    @JsonIgnore private String preferTags;
    private LocalDateTime createdAt,updatedAt;
    @JsonProperty public void setPreferTags(Object tags) { this.preferTags = tags == null ? null : tags.toString(); }
}
