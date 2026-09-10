package com.fastrag.module.graph.service.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可视化孤立点过滤测试：零边实体不渲染，库内数据不受影响（过滤只作用于返回的 data）。
 * 用例覆盖 ID 匹配（source_id/target_id 确定性 ID）与名称回退（存量边无 source_id）。
 */
class GraphServiceImplOrphanFilterTest {

    private Map<String, Object> node(String id, String name, String type) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("name", name);
        n.put("entity_type", type);
        return n;
    }

    private Map<String, Object> edge(String sid, String source, String tid, String target) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("source_id", sid);
        e.put("source", source);
        e.put("target_id", tid);
        e.put("target", target);
        return e;
    }

    @Test
    void filter_removesNodesWithoutAnyEdge() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Object> nodes = new ArrayList<>(List.of(
                node("id-a", "全光组网", "产品"),
                node("id-b", "华为B671-S2", "设备"),
                node("id-orphan", "海康", "品牌"),      // 孤立：无任何边引用
                node("id-orphan2", "表格", "对象")       // 孤立
        ));
        List<Object> edges = new ArrayList<>(List.of(
                edge("id-a", "全光组网", "id-b", "华为B671-S2")
        ));
        data.put("nodes", nodes);
        data.put("edges", edges);

        GraphServiceImpl.filterOrphanNodes(data);

        List<?> kept = (List<?>) data.get("nodes");
        assertEquals(2, kept.size());
        assertTrue(kept.stream().allMatch(n -> {
            String id = String.valueOf(((Map<?, ?>) n).get("id"));
            return id.equals("id-a") || id.equals("id-b");
        }));
        // 边不受影响
        assertEquals(1, ((List<?>) data.get("edges")).size());
    }

    @Test
    void filter_fallsBackToNameMatchingForLegacyEdgesWithoutIds() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", new ArrayList<>(List.of(
                node("id-x", "小微ICT业务", "业务"),
                node("id-y", "客服电话", "属性")   // 孤立
        )));
        // 存量边无 source_id/target_id，只有名称
        Map<String, Object> legacyEdge = new LinkedHashMap<>();
        legacyEdge.put("source", "小微ICT业务");
        legacyEdge.put("target", "客服电话");
        data.put("edges", new ArrayList<>(List.of(legacyEdge)));

        GraphServiceImpl.filterOrphanNodes(data);

        // 端点都有边 → 都保留
        assertEquals(2, ((List<?>) data.get("nodes")).size());
    }

    @Test
    void filter_emptyEdges_clearsAllNodes() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", new ArrayList<>(List.of(node("id-a", "任意", "业务"))));
        data.put("edges", new ArrayList<>());

        GraphServiceImpl.filterOrphanNodes(data);

        assertTrue(((List<?>) data.get("nodes")).isEmpty());
    }
}
