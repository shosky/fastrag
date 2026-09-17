package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.Data; import java.time.LocalDateTime; import java.util.Arrays;
@Data @TableName("kb_doc_guide") public class KbDocGuide {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,fileId,title,summary,outline,keyPoints,indexStatus,category,createdBy;
    @JsonIgnore private String tags;
    private Integer indexProgress;
    private LocalDateTime createdAt,updatedAt;

    @JsonProperty public void setTags(Object tags) { if(tags==null) this.tags=null; else if(tags instanceof CharSequence) this.tags=tags.toString(); else try{this.tags=MAPPER.writeValueAsString(tags);}catch(Exception e){this.tags=tags.toString();} }

    /** outline/key_points 是 JSON 列：空值必须存 NULL（空串会报 "The document is empty."）。
     *  这里只保留 String setter（Lombok 检测到同名方法不再生成），避免与 Object setter 冲突导致空串旁路。
     *  支持：空→NULL、已是 JSON 数组→原样、换行/逗号分隔文本→JSON 数组。 */
    public void setOutline(String outline) { this.outline = toJsonArray(outline); }
    public void setKeyPoints(String keyPoints) { this.keyPoints = toJsonArray(keyPoints); }

    private static String toJsonArray(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("[")) return t;
        return write(Arrays.stream(t.split("[\\r\\n,，;；]")).map(String::trim).filter(x -> !x.isEmpty()).toList());
    }
    private static String write(Object v) { try { return MAPPER.writeValueAsString(v); } catch (Exception e) { return null; } }
}
