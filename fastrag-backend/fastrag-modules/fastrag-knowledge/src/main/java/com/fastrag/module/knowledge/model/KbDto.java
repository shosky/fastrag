package com.fastrag.module.knowledge.model;
import lombok.Data; import java.time.LocalDateTime; import java.util.List;
@Data public class KbDto { private String id,name,description,category,embeddingModel,creator,type; private List<String> tags; private Integer dimension; private LocalDateTime createdAt; private Long usedSize,totalSize; }
