package com.fastrag.module.knowledge.chunking;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkData {
    private String id;
    private int index;
    private String content;
    private Double startTime;
    private Double endTime;
}
