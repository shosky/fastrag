package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_media_storage") public class KbMediaStorage {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,mediaType,name,originalName,extension,objectKey,resolution,thumbnailKey,ocrText;
    private Long size;
    private Integer duration,width,height;
    private String transcript,description,status,createdBy;
    @JsonIgnore private String tags;
    private LocalDateTime createdAt,updatedAt;

    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
