package com.fastrag.module.knowledge.chunking;

import com.fastrag.module.knowledge.parser.DocNode;
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

    /**
     * 结构感知分片：基于 DocNode 列表，保留标题层级关系。
     *
     * 算法要点：
     * 1. 不可切分单元（Atomic Unit：表格、代码块、公式、图片）整体保留
     * 2. 可切分单元（段落）按 chunkLength 切分
     * 3. headingPath 栈算法维护当前标题上下文
     * 4. 每个 chunk 附带 title + headingPath
     *
     * @param nodes      结构化文档节点列表
     * @param strategyId 解析策略 ID
     * @return 携带层级信息的 chunk 列表
     */
    List<ChunkData> structuralChunk(List<DocNode> nodes, String strategyId);
}
