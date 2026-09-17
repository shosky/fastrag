package com.fastrag.module.knowledge.model;
import lombok.Data; import java.time.LocalDateTime;
import java.util.List;
@Data public class QaPairDto {
    private String id,kbId,fileId,fileName,question,answer,source,status;
    private String faqType,keywords,effectiveScope;
    private LocalDateTime effectiveStart,effectiveEnd,createdAt;
    private List<String> relatedKnowledgeIds;
    /** 当前时间是否在生效期内（无生效时间配置视为长期有效） */
    private boolean active;
}
