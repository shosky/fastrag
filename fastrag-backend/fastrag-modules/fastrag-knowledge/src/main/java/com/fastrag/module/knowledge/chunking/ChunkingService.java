package com.fastrag.module.knowledge.chunking;

/**
 * 文本分块服务接口，提供多种文档分块策略。
 *
 * <p>核心职责：
 * <ul>
 *   <li>{@link #chunk(String, String)} — 基于规则的分块：按分隔符和长度切分纯文本，
 *       支持 PDF 页感知（PAGE_BREAK 标记）和图片占位符提取</li>
 *   <li>{@link #chunkBySegments} — 基于时间戳分段分块：用于音视频 ASR 转写结果，
 *       根据时间比例分配每个 chunk 的时间范围</li>
 *   <li>{@link #structuralChunk} — 结构感知分块：基于 DocNode 结构化文档树，
 *       保留标题层级关系，区分可切分/不可切分单元，支持 overlap 尾部延续</li>
 * </ul>
 *
 * <p>所有方法均接受 strategyId 参数，通过 StrategyConfigResolver 动态获取分块参数
 * （chunkLength、overlap、delimiters 等），实现不同知识库使用不同分块策略。
 *
 * <p>实现类为 {@link ChunkingServiceImpl}。
 */
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.knowledge.parser.DocNode;
import com.fastrag.module.knowledge.parser.ParseResult;

import java.util.List;

public interface ChunkingService {
    List<ChunkData> chunk(String text, String strategyId);

    /**
     * 按显式配置规则分片（预览场景：临时策略对象构造的 config，未落库）。
     */
    List<ChunkData> chunk(String text, ParseStrategyConfig config);

    /**
     * 递归字符切分（rule_recursive 策略）：按分隔符优先级逐级降级切分。
     *
     * <p>算法要点（LangChain RecursiveCharacterTextSplitter 风格）：
     * <ol>
     *   <li>优先按第一个分隔符切分；切出的片段仍超长时，用下一个分隔符递归切分</li>
     *   <li>所有分隔符用尽仍超长时，按句子边界（。！？）兜底，最后按 chunkLength 硬切</li>
     *   <li>短片段按 chunkLength 贪心合并为 chunk，超限落盘时携带 overlap 尾部延续</li>
     * </ol>
     *
     * @param text       待切分纯文本（Markdown 全文）
     * @param strategyId 解析策略 ID（读取 chunkLength/overlap/delimiters，可为 null）
     */
    List<ChunkData> recursiveChunk(String text, String strategyId);

    /**
     * 语义切片（semantic 策略）：计算相邻句子的向量相似度，在语义突变处断开。
     *
     * <p>算法要点：
     * <ol>
     *   <li>按句子边界（。！？换行）拆句</li>
     *   <li>批量 Embedding 向量化（模型 = 策略级 embeddingModel，为空时用知识库级模型）</li>
     *   <li>相邻句子余弦相似度低于阈值（semanticThreshold/100）时视为语义断点，开启新 chunk</li>
     *   <li>chunkLength 为单块硬上限，超过时强制断句</li>
     *   <li>Embedding 不可用（无模型/调用失败）时回退规则分片，保证不中断摄入</li>
     * </ol>
     *
     * @param text            待切分纯文本
     * @param strategyId      解析策略 ID
     * @param kbEmbeddingModel 知识库级 Embedding 模型（策略级未配置时兜底，可为 null）
     */
    List<ChunkData> semanticChunk(String text, String strategyId, String kbEmbeddingModel);

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
