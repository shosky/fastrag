package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app") public class App {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,type,icon,status,owner;
    private String orgId; // 归属组织（同组织可见，创建时写入）
    @JsonIgnore private String tags;
    private LocalDateTime createdAt,updatedAt;

    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
