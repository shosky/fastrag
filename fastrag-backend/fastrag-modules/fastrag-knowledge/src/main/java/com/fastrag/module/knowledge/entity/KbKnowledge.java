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
    // 生效截止时间：到期后由每日扫描置为 expired，与取消发布(offline)一并进入回收站集中管理
    private LocalDateTime validUntil;
    // 回收站聚合视图字段（非表字段）：来源 deleted/offline/expired 与入站时间（deletedAt 被 JsonIgnore，前端展示用此字段）
    @TableField(exist = false) private String recycleSource;
    @TableField(exist = false) private LocalDateTime recycleTime;

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
