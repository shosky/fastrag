package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("app_template") public class AppTemplate {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name;
    @TableField(updateStrategy = FieldStrategy.IGNORED) private String content;
    private String description;
    private String type;
    @TableField(updateStrategy = FieldStrategy.IGNORED) private String category;
    @TableField(updateStrategy = FieldStrategy.IGNORED) private String creator;
    private Integer usageCount;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
    @TableField(updateStrategy = FieldStrategy.IGNORED) private String configSnapshot;
}
