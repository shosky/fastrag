package com.fastrag.module.knowledge.chunking;

import com.fastrag.module.knowledge.parser.ParseResult;

import java.util.List;

public interface ChunkingService {
    List<ChunkData> chunk(String text, String strategyId);

    /**
     * 根据时间戳分段生成带时间信息的切片（用于音视频 ASR 结果）
     */
    List<ChunkData> chunkBySegments(List<ParseResult.ChunkTimeSegment> segments);
}
