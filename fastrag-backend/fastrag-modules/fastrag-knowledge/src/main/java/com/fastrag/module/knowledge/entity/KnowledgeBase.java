package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb")
public class KnowledgeBase {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String description;
    private String category;
    @JsonIgnore
    private String tags; // JSON array
    private String embeddingModel;
    private Integer dimension;
    private String creator;
    private String type; // team / personal
    private String permission; // private / team / public
    private Long usedSize;
    private Long totalSize;
    private String retrievalConfig; // JSON
    private String fileTypeConfig; // JSON
    private String parseMode;
    private String splitMode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @JsonProperty
    public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
