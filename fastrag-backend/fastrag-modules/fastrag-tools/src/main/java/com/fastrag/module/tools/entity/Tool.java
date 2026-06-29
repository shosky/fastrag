package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("tool") public class Tool {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,identifier,description,type,icon,inputs;
    @JsonIgnore private String tags;
    private Integer enabled;
    private LocalDateTime createdAt;

    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
