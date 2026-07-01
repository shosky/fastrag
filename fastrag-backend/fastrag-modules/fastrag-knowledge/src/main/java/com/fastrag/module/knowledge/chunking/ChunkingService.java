package com.fastrag.module.knowledge.chunking;

import com.fastrag.module.knowledge.parser.ParseResult;

import java.util.List;

public interface ChunkingService {
    List<ChunkData> chunk(String text, String strategyId);

    /**
     * 根据时间戳分段生成带时间信息的切片（用于音视频 ASR 结果）
     *
     * @param segments   时间戳分段列表
     * @param strategyId 解析策略 ID（用于读取 chunkLength 等参数，可为 null）
     */
    List<ChunkData> chunkBySegments(List<ParseResult.ChunkTimeSegment> segments, String strategyId);
}
