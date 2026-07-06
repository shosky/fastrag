package com.fastrag.module.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_graph_entity")
public class KbGraphEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String kbId;
    private String name;
    private String entityType;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
