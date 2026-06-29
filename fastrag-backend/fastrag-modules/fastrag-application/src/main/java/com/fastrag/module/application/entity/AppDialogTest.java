package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_dialog_test") public class AppDialogTest {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,name,query,expectedAnswer,actualAnswer,createdBy;
    @JsonIgnore private String tags;
    private Integer matched; private Double similarity; private LocalDateTime createdAt;

    @JsonProperty public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
