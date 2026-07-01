package com.fastrag.module.knowledge.chunking;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChunkData {
    private String id;
    private int index;
    private String content;
    private Double startTime;
    private Double endTime;

    // PDF 页感知分块
    private Integer pageNumber;       // 所属页码 (1-based)
    private String pageRange;         // 页码范围 "3-4"
    private List<String> imageKeys;   // 关联的 MinIO 图片 key
    private String chunkType;         // "text" | "image"，默认 "text"
}
