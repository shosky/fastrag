package com.fastrag.module.knowledge.model;
import jakarta.validation.constraints.NotBlank; import lombok.Data;
import java.time.LocalDateTime;
@Data public class QaCreateRequest {
    @NotBlank private String question; @NotBlank private String answer;
    private String source,fileId;
    // FAQ 扩展
    private String faqType;            // common / uncommon
    private String keywords;           // 多关键词，逗号分隔
    private LocalDateTime effectiveStart;
    private LocalDateTime effectiveEnd;
    private String effectiveScope;
    private Object relatedKnowledgeIds; // List<String> 或 JSON 字符串
}
