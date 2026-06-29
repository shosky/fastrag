package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_multi_turn_qa") public class KbMultiTurnQa {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,title,description,turns,category,status,createdBy;
    @JsonIgnore private String tags;
    private LocalDateTime createdAt,updatedAt;

    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
