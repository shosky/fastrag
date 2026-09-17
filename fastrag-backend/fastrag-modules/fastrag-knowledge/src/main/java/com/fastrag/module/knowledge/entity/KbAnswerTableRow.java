package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.Data;
import java.util.LinkedHashMap; import java.util.Map;
/** 表格知识的行内容（表格内容，JSON 列键值） */
@Data @TableName("kb_answer_table_row") public class KbAnswerTableRow {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String tableId;
    @JsonIgnore private String content;
    private Integer sortOrder;
    @JsonProperty public Map<String,Object> getContentMap() { try { return MAPPER.readValue(content==null?"{}":content, Map.class); } catch (Exception e) { return new LinkedHashMap<>(); } }
    @JsonProperty public void setContentMap(Object v) { try { this.content = v==null?null:MAPPER.writeValueAsString(v); } catch (Exception e) { this.content = v==null?null:v.toString(); } }
}
