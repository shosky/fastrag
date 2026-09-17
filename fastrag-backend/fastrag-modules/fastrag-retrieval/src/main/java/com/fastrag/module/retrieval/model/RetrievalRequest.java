package com.fastrag.module.retrieval.model;

import lombok.Data;
import java.util.List;

@Data
public class RetrievalRequest {
    private String knowledgeId;
    private String query;
    private RetrievalConfig config;
    /** 检索偏好标签：命中的结果加权 */
    private List<String> preferTags;

    @Data
    public static class RetrievalConfig {
        private String mode = "hybrid"; // fulltext / vector / hybrid
        private int topK = 10;
        private double similarityThreshold = 0.0;
        private boolean enableRerank = false;
        private String rerankModel;
    }
}
