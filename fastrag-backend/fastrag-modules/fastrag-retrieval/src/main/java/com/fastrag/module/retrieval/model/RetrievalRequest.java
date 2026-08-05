package com.fastrag.module.retrieval.model;

import lombok.Data;

@Data
public class RetrievalRequest {
    private String knowledgeId;
    private String query;
    private RetrievalConfig config;

    @Data
    public static class RetrievalConfig {
        // ===== 检索模式 =====
        // 注意：以下字段均使用包装类型（可空），未传时不覆盖 KB 已保存配置 / 系统默认值
        private String mode; // fulltext / vector / hybrid
        private Integer topK;
        private Double similarityThreshold;

        // ===== 检索预处理（可选，默认开启）=====
        private Boolean enableAutoCorrection;    // 自动纠错（错别字 + 拼音）
        private Boolean enableQueryRewrite;      // 查询重写规则（术语归一）
        private Boolean enableGraphExpand;       // 图谱扩展（可空：null 不覆盖，true/false 可开可关）
        private int graphExpandDepth = 1;         // 图谱展开深度
        private int graphMaxEntities = 10;        // 图谱最大展开实体数
        private String nerModel;                  // NER 提取模型
        private Boolean enableSynonymExpansion;   // 同义词联想
        private Boolean enableKeywordMatch;       // 关键词匹配：命中问答对时优先返回

        // ===== 多路召回（可选，默认关闭）=====
        private Boolean enableMultiRetrieval;      // 是否启用多路召回
        private Integer vectorRecallCount;         // 向量召回数量
        private Integer fulltextRecallCount;       // 全文召回数量
        private Integer graphRecallCount;          // 图谱子图召回数量
        private Integer qaRecallCount;             // QA 对召回数量
        private String fusionStrategy;             // 融合策略: rrf / weighted / interleave

        // ===== BM25 参数 =====
        private Integer bm25RecallCount;           // BM25 召回数量
        private Double vectorWeight;               // 向量检索权重（混合模式）
        private Double bm25Weight;                 // BM25 权重（混合模式）
        private Double bm25SparseDropRate;         // BM25 稀疏项丢弃比例

        // ===== 重排序 =====
        private Boolean enableRerank;             // 可空：null 不覆盖，true/false 可开可关
        private String rerankModel;                // Rerank 模型选择
        private Boolean enableLLMRerank;           // LLM 重排序
        private Boolean enableMMR;                 // MMR 多样性控制
        private Double mmrLambda;                  // MMR lambda 参数 (0=多样性, 1=相关性)

        // ===== 上下文组装 =====
        private String contextAssemblyStrategy;    // concat / parent_document / window
        private Integer contextWindowSize;         // 窗口模式：前后 N 个 chunk
        private Integer maxContextTokens;          // context 最大 token 数
        private String contextOrder;               // relevance / document_order
    }
}
