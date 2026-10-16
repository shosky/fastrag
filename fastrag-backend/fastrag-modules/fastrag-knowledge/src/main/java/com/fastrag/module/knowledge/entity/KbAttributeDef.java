package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonProperty; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.Data; import java.time.LocalDateTime; import java.util.Arrays; import java.util.List;
/** 知识库自定义属性定义（应答知识库-属性管理/添加属性）；options 仅 select 类型使用 */
@Data @TableName("kb_attribute_def") public class KbAttributeDef {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name;
    private String attrType; // text / number / date / select
    private Integer required;
    private String description;
    /** select 的候选选项，落库为 JSON 数组串 */
    private String options;
    /** 录入/展示顺序，越小越靠前 */
    private Integer sort;
    private LocalDateTime createdAt;

    /** 对外返回数组（前端 el-select 直接绑定），落库仍为 JSON 串 */
    public Object getOptions() {
        if (options == null || options.isBlank()) return null;
        try { return MAPPER.readValue(options, List.class); } catch (Exception e) { return List.of(); }
    }

    /** options 归一化：逗号串/数组/JSON 串 → JSON 数组，null 或空串存 NULL */
    @JsonProperty public void setOptions(Object options) {
        if (options == null) { this.options = null; return; }
        if (options instanceof CharSequence) {
            String s = options.toString().trim();
            if (s.isEmpty()) { this.options = null; return; }
            if (s.startsWith("[")) { this.options = s; return; }
            this.options = toJson(Arrays.stream(s.split("[,，]")).map(String::trim).filter(x -> !x.isEmpty()).toList());
            return;
        }
        this.options = toJson(options);
    }
    private static String toJson(Object v) { try { return MAPPER.writeValueAsString(v); } catch (Exception e) { return null; } }
}
