package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_knowledge") public class KbKnowledge {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,title,content,summary,category,source,sourceId,status,createdBy,coverImage;
    // 自定义属性值（JSON：{attrDefId: value}），定义见 kb_attribute_def
    private String attributes;
    @JsonIgnore private String tags;
    private Integer version,viewCount;
    private Double qualityScore;
    private LocalDateTime createdAt,updatedAt;
    // 回收站软删除标记：null=正常，非 null=已删除（可恢复）
    @JsonIgnore private LocalDateTime deletedAt;

    // tags 列为 MySQL JSON 类型：空串/空白归一为 null，非法 JSON 文本降级为 JSON 字符串，避免 Invalid JSON text
    @JsonProperty public void setTags(Object tags) {
        if(tags==null) { this.tags=null; return; }
        if(tags instanceof CharSequence s) {
            String v=s.toString().trim();
            if(v.isEmpty()) { this.tags=null; return; }
            try { MAPPER.readTree(v); this.tags=v; }
            catch(Exception e) { try { this.tags=MAPPER.writeValueAsString(v); } catch(Exception ex) { this.tags=v; } }
            return;
        }
        try { this.tags=MAPPER.writeValueAsString(tags); } catch(Exception e) { this.tags=tags.toString(); }
    }
}
