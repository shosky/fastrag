package com.fastrag.module.knowledge.storage;

import com.fastrag.module.knowledge.chunking.ChunkData;
import java.util.List;

public interface StorageService {
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks);

    /**
     * 存储分片（父子分片模式）：结构感知分片的文档（有 DocNode 结构）传入子分片长度，
     * 存储前按标题层级聚合父分片（父分片落库但不向量化）。
     *
     * @param kbId            知识库 ID
     * @param fileId          文件 ID
     * @param chunks          分片列表（最终列表，含图片分片）
     * @param childChunkLength 子分片长度（父分片最大长度 = 该值 × 2，超过按子分片边界二次切分）
     */
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks, int childChunkLength);

    /**
     * 存储分片（父子分片模式，聚合参数可配置）。
     *
     * @param maxParentLength 父分片最大长度（≤0 时回退 childChunkLength × 2）
     * @param parentAggLevel  父分片聚合层级（H1/H2/H3/auto，null = auto）
     */
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks, int childChunkLength,
                     int maxParentLength, String parentAggLevel);

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
