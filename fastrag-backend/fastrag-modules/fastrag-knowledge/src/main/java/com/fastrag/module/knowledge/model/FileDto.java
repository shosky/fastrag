package com.fastrag.module.knowledge.model;
import lombok.Data; import java.time.LocalDateTime;
@Data public class FileDto { private String id,name,category,extension,url,status,stage,parseStrategyId,parseStrategyName,folderId; private Long size; private Integer progress,duration,pages,chunkCount; private LocalDateTime deletedAt,createdAt,updatedAt; }
