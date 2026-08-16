package com.fastrag.module.graph.service;

/**
 * 图谱查询服务 — Personalized PageRank（PPR）排序。
 *
 * <p>基于 Entity-Chunk 二分提及图（mention 表），以查询实体作为种子节点，
 * 运行个性化 PageRank（KG-04 修复：原实现为标准 PageRank 且种子未生效，MySQL 模式下空转）：
 * </p>
 * <ol>
 *   <li>从 MySQL mention 表（kb_graph_entity_mention）构建 Entity-Chunk 无向图</li>
 *   <li>将查询实体名映射为实体 ID（kb_graph_entity.normalized_name 匹配），作为 PPR 种子</li>
 *   <li>运行个性化 PageRank（阻尼因子 0.85，种子节点均匀分配个性化向量）</li>
 *   <li>按分数降序返回 Top-K 个 Chunk 列表，每个结果包含 chunkId、score、entityCount</li>
 * </ol>
 *
 * <p>说明：该实现依赖 MySQL mention 表（MySQL 存储模式）；Neo4j 存储模式下 mention 数据
 * 不在 MySQL 中，将返回空列表（与存储模式相匹配，不再标注 deprecated）。</p>
 *
 * @see com.fastrag.infra.graph.GraphStore
 */
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.graph.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
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
 * <p>基于 JGraphT 图结构 + 手动迭代实现个性化 PageRank：以查询实体为种子，
 * 对图谱中所有 Chunk 节点进行相关性排序，用于检索增强（RAG）场景。
 * 存储无关：mention 数据与实体 ID 均通过 {@link GraphStore} 读取
 * （MySQL 模式读 mention 表，Neo4j 模式读 MENTIONS 边）。</p>
 */
@Service
@RequiredArgsConstructor
public class GraphQueryService {

    private static final Logger log = LoggerFactory.getLogger(GraphQueryService.class);

    /** PPR 阻尼因子 */
    private static final double DAMPING = 0.85;
    /** 最大迭代次数 */
    private static final int MAX_ITERATIONS = 100;
    /** 收敛阈值 */
    private static final double TOLERANCE = 1e-8;

    private final GraphStore graphStore;

    /**
     * Personalized PageRank 排序：以查询实体为种子，返回排名最高的 Chunk 列表。
     *
     * <p>实现步骤：</p>
     * <ol>
     *   <li>通过 {@link GraphStore#listEntityMentions} 读取该知识库全部 Entity-Chunk 提及，构建无向二分图（双向边）</li>
     *   <li>将查询实体名（normalized_name 匹配，{@link GraphStore#findEntityId}）映射为图中实体节点，作为种子集</li>
     *   <li>迭代计算个性化 PageRank：s' = (1-d)·p + d·Σ(u→v) s(u)/outdeg(u)，p 为种子均匀向量</li>
     *   <li>按 Chunk 节点分数降序返回 Top-K</li>
     * </ol>
     *
     * @param kbId        知识库 ID
     * @param entityNames 查询中的实体名列表（种子）
     * @param topK        返回前 K 个
     * @return 排序后的 Chunk 列表 [{chunkId, score, entityCount}]；种子未命中图谱时返回空列表
     */
    public List<Map<String, Object>> rankChunksByPpr(String kbId, List<String> entityNames, int topK) {
        if (entityNames == null || entityNames.isEmpty()) {
            return List.of();
        }

        // 1. 构建图：节点 = Entity + Chunk，边 = Mention（双向边表达无向语义）
        Graph<String, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
        Set<String> allChunks = new HashSet<>();
        Set<String> allEntities = new HashSet<>();
        List<String[]> mentionEdges = new ArrayList<>();

        try {
            for (Map<String, String> mention : graphStore.listEntityMentions(kbId)) {
                String entityId = mention.get("entityId");
                String chunkId = mention.get("chunkId");
                if (entityId == null || chunkId == null) continue;
                allEntities.add("E:" + entityId);
                allChunks.add("C:" + chunkId);
                mentionEdges.add(new String[]{"E:" + entityId, "C:" + chunkId});
            }
        } catch (Exception e) {
            log.warn("Failed to load entity mentions for PPR: {}", e.getMessage());
            return List.of();
        }

        if (mentionEdges.isEmpty()) {
            log.debug("PPR: no entity mentions for kb={}, return empty", kbId);
            return List.of();
        }

        for (String entity : allEntities) graph.addVertex(entity);
        for (String chunk : allChunks) graph.addVertex(chunk);
        for (String[] edge : mentionEdges) {
            graph.addEdge(edge[0], edge[1]);
            graph.addEdge(edge[1], edge[0]);
        }

        // 2. 查询实体名 → 实体 ID，确定种子节点（仅保留存在于图中的种子）
        Set<String> seeds = new HashSet<>();
        for (String name : entityNames) {
            String normalized = NameNormalizer.normalize(name);
            if (normalized.isEmpty()) continue;
            String id = graphStore.findEntityId(kbId, normalized);
            if (id != null) {
                String node = "E:" + id;
                if (graph.containsVertex(node)) {
                    seeds.add(node);
                }
            }
        }

        if (seeds.isEmpty()) {
            log.debug("PPR: none of the seed entities found in graph, kb={}, seeds={}", kbId, entityNames);
            return List.of();
        }

        // 3. 个性化 PageRank 迭代
        Map<String, Double> score = personalizedPageRank(graph, seeds);

        // 4. 收集 Chunk 节点分数并排序
        List<Map<String, Object>> rankedChunks = allChunks.stream()
                .map(chunk -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("chunkId", chunk.substring(2)); // 去掉 "C:" 前缀
                    item.put("score", score.getOrDefault(chunk, 0.0));
                    item.put("entityCount", graph.degreeOf(chunk) / 2); // 双向边，度/2 = mention 数
                    return item;
                })
                .sorted((a, b) -> Double.compare(
                        (Double) b.get("score"),
                        (Double) a.get("score")))
                .limit(topK)
                .collect(Collectors.toList());

        log.info("PPR ranking: kb={}, seeds={}, graph_size={}, results={}",
                kbId, seeds.size(), graph.vertexSet().size(), rankedChunks.size());

        return rankedChunks;
    }

    /**
     * 迭代计算个性化 PageRank：
     * <pre>s'(v) = (1-d)·p(v) + d · Σ_{u→v} s(u)/outdeg(u)</pre>
     * <p>p 为个性化向量（种子节点均匀分配 1/|seeds|，其余为 0）；初始分数 s = p；
     * 迭代至收敛（L1 范数变化 &lt; 阈值）或达到最大迭代次数。</p>
     */
    private Map<String, Double> personalizedPageRank(Graph<String, DefaultEdge> graph, Set<String> seeds) {
        int n = graph.vertexSet().size();
        if (n == 0) return Collections.emptyMap();

        double seedWeight = 1.0 / seeds.size();
        Map<String, Double> personalization = new HashMap<>();
        for (String v : graph.vertexSet()) {
            personalization.put(v, seeds.contains(v) ? seedWeight : 0.0);
        }

        Map<String, Double> score = new HashMap<>(personalization);
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            Map<String, Double> next = new HashMap<>(n);
            double delta = 0.0;
            for (String v : graph.vertexSet()) {
                double sum = 0.0;
                for (DefaultEdge in : graph.incomingEdgesOf(v)) {
                    String u = graph.getEdgeSource(in);
                    int outdeg = graph.outDegreeOf(u);
                    if (outdeg > 0) {
                        sum += score.getOrDefault(u, 0.0) / outdeg;
                    }
                }
                double newScore = (1 - DAMPING) * personalization.get(v) + DAMPING * sum;
                next.put(v, newScore);
                delta += Math.abs(newScore - score.getOrDefault(v, 0.0));
            }
            score = next;
            if (delta < TOLERANCE) {
                log.debug("PPR converged after {} iterations, delta={}", iter + 1, delta);
                break;
            }
        }
        return score;
    }
}
