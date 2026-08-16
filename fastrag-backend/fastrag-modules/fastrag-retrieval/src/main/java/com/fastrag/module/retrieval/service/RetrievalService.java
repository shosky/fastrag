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
public interface RetrievalService { List<SearchResultItem> search(RetrievalRequest req); long getChunkCount(String kbId); }
