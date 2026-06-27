package com.fastrag.ai.asr;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AsrResult {
    private String text;
    private List<AsrSegment> segments;

    @Data
    @Builder
    public static class AsrSegment {
        private String text;
        private Double start;
        private Double end;
    }
}
