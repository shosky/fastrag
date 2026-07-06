package com.fastrag.module.graph.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_graph_relation")
public class KbGraphRelation {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String kbId;
    private String source;
    private String target;
    private String label;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
