package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fastrag.module.bpm.dto.EdgeRequest;
import com.fastrag.module.bpm.dto.EdgeVO;
import com.fastrag.module.bpm.entity.BpmFlowEdge;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.enums.EdgeKind;
import com.fastrag.module.bpm.mapper.BpmFlowEdgeMapper;
import com.fastrag.module.bpm.service.BpmFlowEdgeService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BpmFlowEdgeServiceImpl extends ServiceImpl<BpmFlowEdgeMapper, BpmFlowEdge> implements BpmFlowEdgeService {
    @Override public List<EdgeVO> listByVersion(String versionId) {
        return baseMapper.selectList(new LambdaQueryWrapper<BpmFlowEdge>()
                .eq(BpmFlowEdge::getVersionId, versionId).orderByAsc(BpmFlowEdge::getPriority).orderByAsc(BpmFlowEdge::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }
    @Override public EdgeVO get(String edgeId) {
        BpmFlowEdge e = baseMapper.selectById(edgeId);
        return e == null ? null : toVO(e);
    }
    @Override public EdgeVO create(String versionId, EdgeRequest req) {
        if (StrUtil.isBlank(req.getSourceNodeKey()) || StrUtil.isBlank(req.getTargetNodeKey()))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("source/target nodeKey 不能为空");
        BpmFlowEdge e = new BpmFlowEdge();
        BeanUtils.copyProperties(req, e, "id", "versionId");
        e.setVersionId(versionId);
        if (StrUtil.isBlank(e.getEdgeKind())) e.setEdgeKind(EdgeKind.default_edge.name());
        else e.setEdgeKind(EdgeKind.of(e.getEdgeKind()).name());
        if (e.getPriority() == null) e.setPriority(0);
        baseMapper.insert(e);
        return toVO(e);
    }
    @Override public EdgeVO update(String edgeId, EdgeRequest req) {
        BpmFlowEdge e = baseMapper.selectById(edgeId);
        if (e == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("边不存在: " + edgeId);
        if (StrUtil.isNotBlank(req.getSourceNodeKey())) e.setSourceNodeKey(req.getSourceNodeKey());
        if (StrUtil.isNotBlank(req.getTargetNodeKey())) e.setTargetNodeKey(req.getTargetNodeKey());
        if (StrUtil.isNotBlank(req.getEdgeKind())) e.setEdgeKind(EdgeKind.of(req.getEdgeKind()).name());
        if (req.getConditionExpr() != null) e.setConditionExpr(req.getConditionExpr());
        if (req.getConditionParams() != null) e.setConditionParams(req.getConditionParams());
        if (req.getLabel() != null) e.setLabel(req.getLabel());
        if (req.getPriority() != null) e.setPriority(req.getPriority());
        baseMapper.updateById(e);
        return toVO(e);
    }
    @Override public void delete(String edgeId) { baseMapper.deleteById(edgeId); }
    @Override public void deleteByNodeKey(String versionId, String nodeKey) {
        baseMapper.delete(new LambdaQueryWrapper<BpmFlowEdge>()
                .eq(BpmFlowEdge::getVersionId, versionId)
                .and(w -> w.eq(BpmFlowEdge::getSourceNodeKey, nodeKey).or().eq(BpmFlowEdge::getTargetNodeKey, nodeKey)));
    }
    private EdgeVO toVO(BpmFlowEdge e) {
        EdgeVO vo = new EdgeVO();
        BeanUtils.copyProperties(e, vo);
        return vo;
    }
}