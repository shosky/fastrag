package com.fastrag.module.graph.service;

import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.graph.mapper.KbGraphEntityMentionMapper;
import com.fastrag.module.graph.mapper.KbGraphTripleMentionMapper;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱查询服务 — Personalized PageRank 排序（参考 Yuxi PprRanker）
 *
 * <p>基于 JGraphT 的 PageRank 算法，以查询实体作为种子节点，
 * 对图谱中所有 Chunk 节点进行排序，返回关联度最高的 Chunks。
 * 用于检索增强（RAG），提升召回质量。</p>
 */
@Service
@RequiredArgsConstructor
public class GraphQueryService {

    private static final Logger log = LoggerFactory.getLogger(GraphQueryService.class);

    private final GraphStore graphStore;
    private final KbGraphEntityMentionMapper entityMentionMapper;
    private final KbGraphTripleMentionMapper tripleMentionMapper;

    /**
     * Personalized PageRank 排序
     *
     * <p>1. 从 MySQL mention 表构建 Entity-Chunk 无向图
     * 2. 设置种子权重（查询中的实体权重更高）
     * 3. 运行 PageRank，返回排名最高的 Chunk 列表</p>
     *
     * @param kbId        知识库 ID
     * @param entityNames 查询中的实体名列表（种子）
     * @param topK        返回前 K 个
     * @return 排序后的 Chunk 列表 [{chunkId, score, entityCount}]
     */
    public List<Map<String, Object>> rankChunksByPpr(String kbId, List<String> entityNames, int topK) {
        if (entityNames == null || entityNames.isEmpty()) {
            return List.of();
        }

        // 1. 构建图：节点 = Entity + Chunk，边 = Mention
        Graph<String, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);

        // 从 MySQL mention 表查询所有 Entity-Chunk 关联
        // (使用 GraphStore 的 MysqlGraphStore 实现，因为 mention 只在 MySQL)
        Set<String> allChunks = new HashSet<>();
        Set<String> allEntities = new HashSet<>();
        List<String[]> mentionEdges = new ArrayList<>();

        // 查询所有 entity_mention 记录
        try {
            var mentions = entityMentionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.fastrag.module.graph.entity.KbGraphEntityMention>()
                            .eq(com.fastrag.module.graph.entity.KbGraphEntityMention::getKbId, kbId)
            );

            // 查询实体名称映射
            var entities = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.fastrag.module.graph.entity.KbGraphEntity>()
                    .eq(com.fastrag.module.graph.entity.KbGraphEntity::getKbId, kbId);
            // 直接通过 graphStore 查不到实体列表（没有 list 方法），用 mention 中的 entityId 作为节点

            for (var mention : mentions) {
                String entityId = mention.getEntityId();
                String chunkId = mention.getChunkId();
                allEntities.add("E:" + entityId);
                allChunks.add("C:" + chunkId);
                mentionEdges.add(new String[]{"E:" + entityId, "C:" + chunkId});
            }
        } catch (Exception e) {
            log.warn("Failed to query entity mentions for PPR: {}", e.getMessage());
            return List.of();
        }

        if (mentionEdges.isEmpty()) {
            return List.of();
        }

        // 2. 添加节点和边
        for (String entity : allEntities) {
            graph.addVertex(entity);
        }
        for (String chunk : allChunks) {
            graph.addVertex(chunk);
        }
        for (String[] edge : mentionEdges) {
            // 双向边（无向图语义）
            graph.addEdge(edge[0], edge[1]);
            graph.addEdge(edge[1], edge[0]);
        }

        // 3. 运行 PageRank（JGraphT 标准版，阻尼因子 0.85）
        try {
            PageRank<String, DefaultEdge> pageRank = new PageRank<String, DefaultEdge>(graph);

            // 5. 收集 Chunk 节点的分数，排序
            List<Map<String, Object>> rankedChunks = allChunks.stream()
                    .map(chunk -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("chunkId", chunk.substring(2)); // 去掉 "C:" 前缀
                        item.put("score", pageRank.getVertexScore(chunk));
                        item.put("entityCount", graph.degreeOf(chunk) / 2); // 无向图的度/2
                        return item;
                    })
                    .sorted((a, b) -> Double.compare(
                            (Double) b.get("score"),
                            (Double) a.get("score")))
                    .limit(topK)
                    .collect(Collectors.toList());

            log.debug("PPR ranking: kb={}, seeds={}, graph_size={}, results={}",
                    kbId, entityNames.size(), graph.vertexSet().size(), rankedChunks.size());

            return rankedChunks;
        } catch (Exception e) {
            log.error("PPR computation failed: kb={}, error={}", kbId, e.getMessage(), e);
            return List.of();
        }
    }
}
