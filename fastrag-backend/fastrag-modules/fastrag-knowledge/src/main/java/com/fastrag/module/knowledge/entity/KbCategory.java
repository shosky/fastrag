package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_category") public class KbCategory {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name;
    private String description;
    private String color;
    private String icon;
    private Integer sort;
    private String orgId; // 归属组织（NULL=未分配，仅管理员可见；非空=该组织私有分类）
    private String createdBy;
    private LocalDateTime createdAt;
}
