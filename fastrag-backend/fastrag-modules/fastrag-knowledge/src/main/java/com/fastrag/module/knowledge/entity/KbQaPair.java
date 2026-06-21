package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_qa_pair")
public class KbQaPair {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String fileId;
    private String fileName;
    private String question;
    private String answer;
    private String source; // manual / ai
    private String status; // draft / confirmed
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
