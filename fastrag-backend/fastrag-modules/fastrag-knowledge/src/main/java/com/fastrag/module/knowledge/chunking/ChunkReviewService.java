package com.fastrag.module.knowledge.chunking;

import java.util.List;

/**
 * LLM 分片边界审查服务（structure_aware + llmReview.enabled 启用后，
 * 切片之后、入库之前的质检工序）。
 *
 * <p>LLM 仅输出合并/拆分/保留的边界调整方案，文字一个字不动；
 * 调整执行后做内容守恒校验，失败整批回退。未启用/模型缺失/调用失败/
 * 超预算的部分保持原样并标记未审查（llmReviewed=0），支持事后批量补审。</p>
 */
public interface ChunkReviewService {

    /**
     * 对 structuralChunk 产出的分片做 LLM 边界合理性审查。
     *
     * @param kbId       知识库 ID（模型回退与审查明细落库用）
     * @param fileId     文件 ID
     * @param strategyId 解析策略 ID（读取 chunk.llmReview.enabled 与 llmModel）
     * @param chunks     切片产出的分片列表
     * @return 调整后的分片列表（边界调整已应用，llmReviewed/reviewModel/reviewTime/reviewNote 已回填）
     */
    List<ChunkData> reviewAndAdjust(String kbId, String fileId, String strategyId, List<ChunkData> chunks);
}
