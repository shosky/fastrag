package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.Data; import java.time.LocalDateTime; import java.util.Arrays;
@Data @TableName("kb_note") public class KbNote {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,title,content,targetType,targetId,createdBy;
    @JsonIgnore private String tags;
    private LocalDateTime createdAt,updatedAt;

    /**
     * tags 落库为 JSON 数组：逗号串/数组/JSON 串均归一化，null 或空串存 NULL。
     * 与 KbKnowledge/KbMediaStorage 同款处理：空串直接写 MySQL JSON 列会报 3140（Invalid JSON text），
     * 此前笔记漏了这层归一化，导致新建笔记（前端默认 tags="" ）必然 500。
     */
    @JsonProperty public void setTags(Object tags) {
        if (tags == null) { this.tags = null; return; }
        if (tags instanceof CharSequence) {
            String s = tags.toString().trim();
            if (s.isEmpty()) { this.tags = null; return; }
            if (s.startsWith("[")) { this.tags = s; return; }
            this.tags = toJson(Arrays.stream(s.split("[,，]")).map(String::trim).filter(x -> !x.isEmpty()).toList());
            return;
        }
        this.tags = toJson(tags);
    }
    private static String toJson(Object v) { try { return MAPPER.writeValueAsString(v); } catch (Exception e) { return null; } }
}
