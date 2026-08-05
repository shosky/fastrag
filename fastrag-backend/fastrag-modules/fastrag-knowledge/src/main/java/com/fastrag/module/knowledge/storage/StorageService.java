package com.fastrag.module.knowledge.storage;

import com.fastrag.module.knowledge.chunking.ChunkData;
import java.util.List;

public interface StorageService {
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks);

    /**
     * 分批存储 chunk（用于流水线并行）
     *
     * @param kbId       知识库 ID
     * @param fileId     文件 ID
     * @param chunks     一批 chunk
     * @param batchStart 该批在全局中的起始索引
     */
    void storeChunkBatch(String kbId, String fileId, List<ChunkData> chunks, int batchStart);
}
