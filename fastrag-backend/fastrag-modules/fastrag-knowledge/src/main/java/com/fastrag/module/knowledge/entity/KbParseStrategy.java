package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_parse_strategy")
public class KbParseStrategy {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String name;
    private String description;
    private String extensions; // JSON array
    private String parseMethod; // default / pptx / pdf / video / audio
    private Integer isDefault;
    private String advanced; // JSON
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
