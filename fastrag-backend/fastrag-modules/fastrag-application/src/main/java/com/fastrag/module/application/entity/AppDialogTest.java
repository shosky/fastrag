package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_dialog_test") public class AppDialogTest {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,name,query,expectedAnswer,actualAnswer,createdBy;
    @JsonIgnore private String tags;
    private Integer matched; private Double similarity; private LocalDateTime createdAt;

    @JsonProperty public void setTags(Object tags) {
        if (tags == null) { this.tags = null; return; }
        String s = tags.toString().trim();
        // 如果已经是合法 JSON（以 [ 或 { 开头），直接使用；否则包装为 JSON 数组
        if (s.isEmpty()) { this.tags = null; }
        else if (s.startsWith("[") || s.startsWith("{")) { this.tags = s; }
        else { this.tags = "[\"" + s.replace("\"", "\\\"") + "\"]"; }
    }
}
