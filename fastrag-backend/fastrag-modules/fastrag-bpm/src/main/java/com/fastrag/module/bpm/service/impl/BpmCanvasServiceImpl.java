package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.bpm.dto.CanvasSaveRequest;
import com.fastrag.module.bpm.dto.CanvasValidateResultVO;
import com.fastrag.module.bpm.dto.EdgeRequest;
import com.fastrag.module.bpm.dto.NodeRequest;
import com.fastrag.module.bpm.entity.BpmFlowEdge;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.entity.BpmFlowVersion;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.enums.VersionStatus;
import com.fastrag.module.bpm.mapper.BpmFlowEdgeMapper;
import com.fastrag.module.bpm.mapper.BpmFlowNodeMapper;
import com.fastrag.module.bpm.mapper.BpmFlowVersionMapper;
import com.fastrag.module.bpm.service.BpmCanvasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class BpmCanvasServiceImpl implements BpmCanvasService {
    private final BpmFlowVersionMapper versionMapper;
    private final BpmFlowNodeMapper nodeMapper;
    private final BpmFlowEdgeMapper edgeMapper;

    @Override @Transactional public void saveCanvas(String flowDefId, String versionId, CanvasSaveRequest req) {
        BpmFlowVersion v = versionMapper.selectById(versionId);
        if (v == null) throw BpmErrorCode.FLOW_VERSION_NOT_FOUND.of("v" + versionId);
        if (!VersionStatus.draft.name().equals(v.getStatus()))
            throw BpmErrorCode.INSTANCE_STATE_INVALID.of("仅 draft 版本可保存画布");
        // 1) 更新 version 字段(canvasData)
        if (req.getCanvasData() != null) v.setCanvasData(req.getCanvasData());
        // 2) 全量替换 nodes/edges:先按 versionId 删除,再插入
        edgeMapper.delete(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, versionId));
        nodeMapper.delete(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, versionId));
        List<NodeRequest> nodes = Optional.ofNullable(req.getNodes()).orElse(Collections.emptyList());
        List<EdgeRequest> edges = Optional.ofNullable(req.getEdges()).orElse(Collections.emptyList());
        // 3) 插入 nodes
        for (NodeRequest n : nodes) {
            BpmFlowNode node = new BpmFlowNode();
            node.setVersionId(versionId);
            node.setNodeKey(n.getNodeKey());
            node.setNodeType(n.getNodeType());
            node.setName(n.getName());
            node.setPositionX(n.getPositionX() == null ? 0 : n.getPositionX());
            node.setPositionY(n.getPositionY() == null ? 0 : n.getPositionY());
            node.setConfig(n.getConfig());
            node.setTimeoutMs(n.getTimeoutMs() == null ? 30000 : n.getTimeoutMs());
            node.setRetryCount(n.getRetryCount() == null ? 0 : n.getRetryCount());
            node.setRetryIntervalMs(n.getRetryIntervalMs() == null ? 1000 : n.getRetryIntervalMs());
            node.setOnFailure(StrUtil.isBlank(n.getOnFailure()) ? "fail" : n.getOnFailure());
            node.setFailureBranchNodeKey(n.getFailureBranchNodeKey());
            node.setEnabled(n.getEnabled() == null ? Boolean.TRUE : n.getEnabled());
            nodeMapper.insert(node);
        }
        // 4) 插入 edges
        for (EdgeRequest e : edges) {
            BpmFlowEdge edge = new BpmFlowEdge();
            edge.setVersionId(versionId);
            edge.setSourceNodeKey(e.getSourceNodeKey());
            edge.setTargetNodeKey(e.getTargetNodeKey());
            edge.setEdgeKind(StrUtil.isBlank(e.getEdgeKind()) ? "default_edge" : e.getEdgeKind());
            edge.setConditionExpr(e.getConditionExpr());
            edge.setConditionParams(e.getConditionParams());
            edge.setLabel(e.getLabel());
            edge.setPriority(e.getPriority() == null ? 0 : e.getPriority());
            edgeMapper.insert(edge);
        }
        // 5) 同步更新 nodes/edges snapshot JSON(便于 SQL 查询;失败不影响主流程)
        v.setNodesSnapshot(toJson(nodes.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeKey", n.getNodeKey()); m.put("nodeType", n.getNodeType());
            m.put("name", n.getName()); m.put("positionX", n.getPositionX()); m.put("positionY", n.getPositionY());
            return m;
        }).collect(Collectors.toList())));
        v.setEdgesSnapshot(toJson(edges.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sourceNodeKey", e.getSourceNodeKey()); m.put("targetNodeKey", e.getTargetNodeKey());
            m.put("edgeKind", e.getEdgeKind()); m.put("label", e.getLabel());
            return m;
        }).collect(Collectors.toList())));
        versionMapper.updateById(v);
    }
    @Override public CanvasValidateResultVO validate(String versionId) {
        List<BpmFlowNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, versionId));
        List<BpmFlowEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, versionId));
        return validateStructure(nodes, edges);
    }
    @Override public CanvasValidateResultVO validateStructure(List<?> rawNodes, List<?> rawEdges) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        // 提取 nodeKey/type/source/target
        Set<String> nodeKeys = new LinkedHashSet<>();
        Map<String, String> typeOf = new HashMap<>();
        for (Object o : rawNodes) {
            String key = getField(o, "getNodeKey");
            String type = getField(o, "getNodeType");
            if (key == null) continue;
            nodeKeys.add(key);
            typeOf.put(key, type);
        }
        long startCount = typeOf.values().stream().filter(t -> "start".equals(t)).count();
        long endCount = typeOf.values().stream().filter(t -> "end".equals(t)).count();
        if (startCount == 0) errors.add("缺少开始节点(start)");
        if (startCount > 1) errors.add("开始节点只能有一个,当前 " + startCount);
        if (endCount == 0) errors.add("缺少结束节点(end)");
        // 边的源/目标存在性
        List<String[]> edgePairs = new ArrayList<>();
        for (Object o : rawEdges) {
            String src = getField(o, "getSourceNodeKey");
            String tgt = getField(o, "getTargetNodeKey");
            if (src == null || tgt == null) continue;
            edgePairs.add(new String[]{src, tgt});
            if (!nodeKeys.contains(src)) errors.add("边 source 节点不存在: " + src);
            if (!nodeKeys.contains(tgt)) errors.add("边 target 节点不存在: " + tgt);
        }
        // 不可达节点(从 start BFS)
        if (startCount == 1 && !nodeKeys.isEmpty()) {
            String startKey = typeOf.entrySet().stream().filter(e -> "start".equals(e.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
            if (startKey != null) {
                Set<String> visited = bfs(startKey, edgePairs);
                List<String> unreachable = nodeKeys.stream().filter(k -> !visited.contains(k)).collect(Collectors.toList());
                if (!unreachable.isEmpty()) warnings.add("不可达节点: " + String.join(",", unreachable));
            }
        }
        // 环检测(DFS)
        if (hasCycle(edgePairs)) errors.add("流程存在环");
        return new CanvasValidateResultVO(errors.isEmpty(), errors, warnings,
                nodeKeys.size(), edgePairs.size());
    }
    private Set<String> bfs(String start, List<String[]> edges) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start); visited.add(start);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String[] e : edges) {
                if (e[0].equals(cur) && !visited.contains(e[1])) { visited.add(e[1]); queue.add(e[1]); }
            }
        }
        return visited;
    }
    private boolean hasCycle(List<String[]> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        for (String[] e : edges) {
            adj.computeIfAbsent(e[0], k -> new ArrayList<>()).add(e[1]);
        }
        Set<String> WHITE = new HashSet<>(adj.keySet());
        Set<String> GRAY = new HashSet<>();
        Set<String> BLACK = new HashSet<>();
        for (String n : new ArrayList<>(adj.keySet())) {
            if (WHITE.contains(n) && dfsHasCycle(n, adj, WHITE, GRAY, BLACK)) return true;
        }
        return false;
    }
    private boolean dfsHasCycle(String n, Map<String, List<String>> adj, Set<String> WHITE, Set<String> GRAY, Set<String> BLACK) {
        WHITE.remove(n); GRAY.add(n);
        for (String m : adj.getOrDefault(n, Collections.emptyList())) {
            if (BLACK.contains(m)) continue;
            if (GRAY.contains(m)) return true;
            if (dfsHasCycle(m, adj, WHITE, GRAY, BLACK)) return true;
        }
        GRAY.remove(n); BLACK.add(n);
        return false;
    }
    private String getField(Object o, String method) {
        try { return (String) o.getClass().getMethod(method).invoke(o); }
        catch (Exception e) { return null; }
    }
    private String toJson(Object o) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); }
        catch (Exception e) { return "[]"; }
    }
}