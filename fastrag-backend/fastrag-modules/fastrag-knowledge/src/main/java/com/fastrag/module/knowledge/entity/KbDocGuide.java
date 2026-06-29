package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_doc_guide") public class KbDocGuide {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,fileId,title,summary,outline,keyPoints,indexStatus,category,createdBy;
    @JsonIgnore private String tags;
    private Integer indexProgress;
    private LocalDateTime createdAt,updatedAt;

    @JsonProperty public void setTags(Object tags) { if(tags==null) this.tags=null; else if(tags instanceof CharSequence) this.tags=tags.toString(); else try{this.tags=MAPPER.writeValueAsString(tags);}catch(Exception e){this.tags=tags.toString();} }
}
