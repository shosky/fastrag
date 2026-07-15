package com.fastrag.common.service;

import java.util.List;
import java.util.Map;

/**
 * 知识库操作服务接口。
 * 定义 tools 模块调用 knowledge 模块的契约，避免 tools 直接依赖 knowledge。
 */
public interface KbOperationService {

    /**
     * 列出用户可访问的知识库
     */
    List<Map<String, Object>> listKnowledgeBases(String userId);

    /**
     * 在知识库中检索
     * @param kbId 知识库 ID
     * @param query 查询文本
     * @param topK 返回结果数
     * @return 检索结果列表（含 content, score, source 等）
     */
    List<Map<String, Object>> queryKnowledgeBase(String kbId, String query, int topK);
}
