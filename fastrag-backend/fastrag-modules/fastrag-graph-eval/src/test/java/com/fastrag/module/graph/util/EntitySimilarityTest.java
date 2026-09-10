package com.fastrag.module.graph.util;

import com.fastrag.infra.graph.EntitySimilarity;
import com.fastrag.infra.graph.EntitySimilarity.EntityVector;
import com.fastrag.infra.graph.EntitySimilarity.SimilarPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体相似度工具测试（同义实体合并候选发现）。
 * 测试放在 graph-eval 模块（其依赖 infra，可在测试中引用 infra 工具类）。
 */
class EntitySimilarityTest {

    private static float[] vec(float... v) {
        return v;
    }

    @Test
    void cosine_parallelVectors_nearOne() {
        double sim = EntitySimilarity.cosine(vec(1, 2, 3), vec(2, 4, 6));
        assertTrue(sim > 0.999, "同向向量相似度应≈1: " + sim);
    }

    @Test
    void cosine_orthogonalAndMismatch_zero() {
        assertEquals(0.0, EntitySimilarity.cosine(vec(1, 0), vec(0, 1)), 1e-9);
        assertEquals(0.0, EntitySimilarity.cosine(vec(1, 0), vec(1, 0, 0)), 1e-9, "维度不一致返回 0");
        assertEquals(0.0, EntitySimilarity.cosine(vec(0, 0), vec(1, 1)), 1e-9, "零向量返回 0");
    }

    @Test
    void topPairs_thresholdAndOrdering() {
        List<EntityVector> entities = List.of(
                new EntityVector("id-1", "卫健", vec(1, 0, 0)),
                new EntityVector("id-2", "卫健客户", vec(0.98f, 0.1f, 0)),
                new EntityVector("id-3", "光猫", vec(0, 1, 0)),
                new EntityVector("id-4", "分光器", vec(0, 0.9f, 0.1f)));

        List<SimilarPair> pairs = EntitySimilarity.topPairs(entities, 0.9, 10);

        assertEquals(2, pairs.size());
        // 按相似度降序：卫健/卫健客户 对更接近
        assertTrue(pairs.get(0).similarity() >= pairs.get(1).similarity());
        // 阈值过滤：无关对不出现
        assertTrue(pairs.stream().noneMatch(p ->
                (p.sourceName().equals("卫健") && p.targetName().equals("光猫"))));
    }

    @Test
    void topPairs_invalidVectors_skipped() {
        List<EntityVector> entities = List.of(
                new EntityVector("id-1", "a", null),
                new EntityVector("id-2", "b", new float[0]),
                new EntityVector("id-3", "c", vec(0, 0)),
                new EntityVector("id-4", "d", vec(1, 1)),
                new EntityVector("id-5", "e", vec(1, 1)));

        List<SimilarPair> pairs = EntitySimilarity.topPairs(entities, 0.5, 10);
        assertEquals(1, pairs.size());
        assertEquals("d", pairs.get(0).sourceName());
        assertEquals("e", pairs.get(0).targetName());
    }

    @Test
    void topPairs_limitApplied() {
        List<EntityVector> entities = List.of(
                new EntityVector("1", "e1", vec(1, 0)),
                new EntityVector("2", "e2", vec(1, 0)),
                new EntityVector("3", "e3", vec(1, 0)),
                new EntityVector("4", "e4", vec(1, 0)));
        assertEquals(2, EntitySimilarity.topPairs(entities, 0.9, 2).size());
    }
}
