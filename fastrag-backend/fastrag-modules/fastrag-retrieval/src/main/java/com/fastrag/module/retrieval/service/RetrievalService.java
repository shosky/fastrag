package com.fastrag.module.retrieval.service;

/**
 * 知识检索核心服务接口。
 *
 * <p>定义知识检索的核心能力，支持向量检索、全文检索和混合检索三种模式。
 * 检索流程包括：查询预处理、多路召回、RRF/加权融合、重排序、MMR 多样性控制、
 * 上下文组装以及关键词 QA 匹配等完整链路。</p>
 *
 * @see RetrievalRequest
 * @see SearchResultItem
 */
import com.fastrag.module.retrieval.model.*;
import java.util.List;
import java.util.Map;
public interface RetrievalService {
    List<SearchResultItem> search(RetrievalRequest req);
    long getChunkCount(String kbId);

    /**
     * 查询增强 LLM 健康状态（检索设置页健康提示用）。
     *
     * @param kbId  知识库 ID（可空：空则只按系统级配置/默认网关解析）
     * @param model 指定模型 code（可空：空则按 KB 保存配置 > 系统默认 > 默认网关解析）
     * @return {model, gateway, reachable}
     */
    Map<String, Object> enhanceStatus(String kbId, String model);
}
