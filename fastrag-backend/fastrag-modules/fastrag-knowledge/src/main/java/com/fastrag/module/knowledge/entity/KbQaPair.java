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
    // FAQ 扩展：常见/非常见问题、多关键词、生效时间与生效功能、关联知识
    private String faqType; // common / uncommon
    private String keywords; // 多关键词，逗号分隔
    private LocalDateTime effectiveStart;
    private LocalDateTime effectiveEnd;
    private String effectiveScope; // 生效功能范围，如 online_service / robot / all
    private String relatedKnowledgeIds; // JSON 数组：关联知识条目 id
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
