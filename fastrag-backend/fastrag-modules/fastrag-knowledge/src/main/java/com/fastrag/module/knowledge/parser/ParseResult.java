package com.fastrag.module.knowledge.parser;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ParseResult {
    private String text;
    private int pages;
    private String metadata;
    private List<ChunkTimeSegment> segments;

    @Data
    @Builder
    public static class ChunkTimeSegment {
        private String text;
        private Double startTime;
        private Double endTime;
    }
}
