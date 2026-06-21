package com.fastrag.module.knowledge.model;
import lombok.Data; import java.time.LocalDateTime;
@Data public class QaPairDto { private String id,kbId,fileId,fileName,question,answer,source,status; private LocalDateTime createdAt; }
