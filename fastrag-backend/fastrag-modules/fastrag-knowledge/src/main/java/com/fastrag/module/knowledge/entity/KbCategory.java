package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_category") public class KbCategory {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name;
    private String description;
    private String color;
    private String icon;
    private Integer sort;
    private String createdBy;
    private LocalDateTime createdAt;
}
