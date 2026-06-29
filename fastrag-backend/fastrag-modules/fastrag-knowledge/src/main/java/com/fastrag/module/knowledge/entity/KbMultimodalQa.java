package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_multimodal_qa") public class KbMultimodalQa {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,title,question,answer,modalType,mediaIds,category,status,createdBy;
    @JsonIgnore private String tags;
    private LocalDateTime createdAt,updatedAt;

    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
