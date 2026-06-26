package com.fastrag.module.knowledge.chunking;

import java.util.List;

public interface ChunkingService {
    List<ChunkData> chunk(String text, String strategyId);
}
