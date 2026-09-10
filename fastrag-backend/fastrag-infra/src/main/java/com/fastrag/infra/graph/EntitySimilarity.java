package com.fastrag.infra.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体相似度计算工具（同义实体合并候选发现）。
 *
 * <p>实体已带 embedding（名称向量，构建时生成）。本工具对实体集合做两两余弦相似度，
 * 输出超过阈值的候选对（按相似度降序），供人工确认后调用合并 API。
 * 只做"候选建议"，不做自动合并——异名同义（卫健/卫健客户）是否真同义需要人/LLM 判断。</p>
 *
 * <p>复杂度 O(n²)：单知识库实体数 ≤{@link #MAX_ENTITIES}（调用方截断）时约两百万次点积，可接受；
 * 更大规模应引入向量库近邻检索（{@code searchEntitiesByVector} 路径）。
 * 本类为无状态工具类，仅含静态方法，位于 infra 层供 Neo4jGraphStore 直接使用。</p>
 */
public final class EntitySimilarity {

    private EntitySimilarity() {}

    /** 相似候选对 */
    public record SimilarPair(String sourceEntityId, String sourceName,
                              String targetEntityId, String targetName,
                              double similarity) {}

    /** 带向量的实体（调用方从图库读取后传入） */
    public record EntityVector(String entityId, String name, float[] vector) {}

    /** 单库参与两两比对的实体数上限（防御大知识库 O(n²) 爆炸） */
    public static final int MAX_ENTITIES = 2000;

    /**
     * 两两计算余弦相似度，返回 similarity ≥ threshold 的候选对（降序，最多 limit 个）。
     * 向量为 null/长度不一致/全零的实体跳过。
     */
    public static List<SimilarPair> topPairs(List<EntityVector> entities,
                                             double threshold, int limit) {
        List<EntityVector> valid = new ArrayList<>();
        for (EntityVector e : entities) {
            if (e == null || e.vector() == null || e.vector().length == 0) continue;
            if (!hasNonZero(e.vector())) continue;
            valid.add(e);
        }
        if (valid.size() > MAX_ENTITIES) {
            valid = valid.subList(0, MAX_ENTITIES);
        }
        List<SimilarPair> pairs = new ArrayList<>();
        for (int i = 0; i < valid.size(); i++) {
            EntityVector a = valid.get(i);
            for (int j = i + 1; j < valid.size(); j++) {
                EntityVector b = valid.get(j);
                double sim = cosine(a.vector(), b.vector());
                if (sim >= threshold) {
                    pairs.add(new SimilarPair(a.entityId(), a.name(), b.entityId(), b.name(), sim));
                }
            }
        }
        pairs.sort((p, q) -> Double.compare(q.similarity(), p.similarity()));
        return pairs.size() > limit ? pairs.subList(0, limit) : pairs;
    }

    /** 余弦相似度；维度不一致或任一零向量返回 0 */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static boolean hasNonZero(float[] v) {
        for (float x : v) {
            if (x != 0f) return true;
        }
        return false;
    }
}
