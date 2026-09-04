package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fastrag.module.bpm.dto.NodeRequest;
import com.fastrag.module.bpm.dto.NodeVO;
import com.fastrag.module.bpm.entity.BpmFlowEdge;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.mapper.BpmFlowEdgeMapper;
import com.fastrag.module.bpm.mapper.BpmFlowNodeMapper;
import com.fastrag.module.bpm.service.BpmFlowNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class BpmFlowNodeServiceImpl extends ServiceImpl<BpmFlowNodeMapper, BpmFlowNode> implements BpmFlowNodeService {
    private final BpmFlowEdgeMapper edgeMapper;

    @Override public List<NodeVO> listByVersion(String versionId) {
        return baseMapper.selectList(new LambdaQueryWrapper<BpmFlowNode>()
                .eq(BpmFlowNode::getVersionId, versionId).orderByAsc(BpmFlowNode::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }
    @Override public NodeVO getByKey(String versionId, String nodeKey) {
        BpmFlowNode n = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowNode>()
                .eq(BpmFlowNode::getVersionId, versionId).eq(BpmFlowNode::getNodeKey, nodeKey));
        return n == null ? null : toVO(n);
    }
    @Override public NodeVO create(String versionId, NodeRequest req) {
        if (StrUtil.isBlank(req.getNodeKey())) throw BpmErrorCode.NODE_CONFIG_INVALID.of("nodeKey 不能为空");
        if (StrUtil.isBlank(req.getNodeType())) throw BpmErrorCode.NODE_CONFIG_INVALID.of("nodeType 不能为空");
        BpmFlowNode n = new BpmFlowNode();
        BeanUtils.copyProperties(req, n, "id", "versionId");
        n.setVersionId(versionId);
        if (n.getPositionX() == null) n.setPositionX(0);
        if (n.getPositionY() == null) n.setPositionY(0);
        if (n.getTimeoutMs() == null) n.setTimeoutMs(30000);
        if (n.getRetryCount() == null) n.setRetryCount(0);
        if (n.getRetryIntervalMs() == null) n.setRetryIntervalMs(1000);
        if (StrUtil.isBlank(n.getOnFailure())) n.setOnFailure("fail");
        if (n.getEnabled() == null) n.setEnabled(Boolean.TRUE);
        baseMapper.insert(n);
        return toVO(n);
    }
    @Override public NodeVO update(String versionId, String nodeKey, NodeRequest req) {
        BpmFlowNode n = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowNode>()
                .eq(BpmFlowNode::getVersionId, versionId).eq(BpmFlowNode::getNodeKey, nodeKey));
        if (n == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("节点不存在: " + nodeKey);
        if (StrUtil.isNotBlank(req.getNodeType())) n.setNodeType(req.getNodeType());
        if (StrUtil.isNotBlank(req.getName())) n.setName(req.getName());
        if (req.getPositionX() != null) n.setPositionX(req.getPositionX());
        if (req.getPositionY() != null) n.setPositionY(req.getPositionY());
        if (req.getConfig() != null) n.setConfig(req.getConfig());
        if (req.getTimeoutMs() != null) n.setTimeoutMs(req.getTimeoutMs());
        if (req.getRetryCount() != null) n.setRetryCount(req.getRetryCount());
        if (req.getRetryIntervalMs() != null) n.setRetryIntervalMs(req.getRetryIntervalMs());
        if (StrUtil.isNotBlank(req.getOnFailure())) n.setOnFailure(req.getOnFailure());
        if (req.getFailureBranchNodeKey() != null) n.setFailureBranchNodeKey(req.getFailureBranchNodeKey());
        if (req.getEnabled() != null) n.setEnabled(req.getEnabled());
        baseMapper.updateById(n);
        return toVO(n);
    }
    @Override public NodeVO move(String versionId, String nodeKey, Integer x, Integer y) {
        BpmFlowNode n = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowNode>()
                .eq(BpmFlowNode::getVersionId, versionId).eq(BpmFlowNode::getNodeKey, nodeKey));
        if (n == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("节点不存在: " + nodeKey);
        if (x != null) n.setPositionX(x);
        if (y != null) n.setPositionY(y);
        baseMapper.updateById(n);
        return toVO(n);
    }
    @Override public NodeVO updateConfig(String versionId, String nodeKey, String config) {
        BpmFlowNode n = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowNode>()
                .eq(BpmFlowNode::getVersionId, versionId).eq(BpmFlowNode::getNodeKey, nodeKey));
        if (n == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("节点不存在: " + nodeKey);
        n.setConfig(config);
        baseMapper.updateById(n);
        return toVO(n);
    }
    @Override @Transactional public void delete(String versionId, String nodeKey) {
        BpmFlowNode n = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowNode>()
                .eq(BpmFlowNode::getVersionId, versionId).eq(BpmFlowNode::getNodeKey, nodeKey));
        if (n == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("节点不存在: " + nodeKey);
        baseMapper.deleteById(n.getId());
        // 级联:删除以该节点为 source/target 的边
        edgeMapper.delete(new LambdaQueryWrapper<BpmFlowEdge>()
                .eq(BpmFlowEdge::getVersionId, versionId)
                .and(w -> w.eq(BpmFlowEdge::getSourceNodeKey, nodeKey).or().eq(BpmFlowEdge::getTargetNodeKey, nodeKey)));
    }
    private NodeVO toVO(BpmFlowNode n) {
        NodeVO vo = new NodeVO();
        BeanUtils.copyProperties(n, vo);
        return vo;
    }
}