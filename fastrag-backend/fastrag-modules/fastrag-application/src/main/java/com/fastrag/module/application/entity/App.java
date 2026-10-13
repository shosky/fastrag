package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app") public class App {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,type,icon,status,owner;
    @JsonIgnore private String tags;
    private LocalDateTime createdAt,updatedAt;

    /** 入参兼容数组/字符串：统一落库为 JSON 字符串 */
    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
    /** 出参解析为数组（与 KbDto 行为一致）；直接输出 @JsonIgnore 字段会把原始 JSON 字符串发给前端 */
    @JsonProperty("tags") public Object getTagsOut() {
        if (tags == null || tags.isBlank()) return null;
        try { return cn.hutool.json.JSONUtil.toList(tags, String.class); } catch (Exception e) { return tags; }
    }
}
