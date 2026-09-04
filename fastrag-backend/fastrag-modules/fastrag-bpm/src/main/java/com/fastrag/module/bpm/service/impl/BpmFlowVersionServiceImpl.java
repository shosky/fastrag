package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.BpmFlowDef;
import com.fastrag.module.bpm.entity.BpmFlowEdge;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.entity.BpmFlowVersion;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.enums.VersionStatus;
import com.fastrag.module.bpm.mapper.BpmFlowDefMapper;
import com.fastrag.module.bpm.mapper.BpmFlowEdgeMapper;
import com.fastrag.module.bpm.mapper.BpmFlowNodeMapper;
import com.fastrag.module.bpm.mapper.BpmFlowVersionMapper;
import com.fastrag.module.bpm.service.BpmFlowVersionService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BpmFlowVersionServiceImpl extends ServiceImpl<BpmFlowVersionMapper, BpmFlowVersion> implements BpmFlowVersionService {
    private final BpmFlowNodeMapper nodeMapper;
    private final BpmFlowEdgeMapper edgeMapper;
    private final BpmFlowDefMapper defMapper;

    public BpmFlowVersionServiceImpl(BpmFlowNodeMapper nodeMapper, BpmFlowEdgeMapper edgeMapper, BpmFlowDefMapper defMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.defMapper = defMapper;
    }

    @Override public List<FlowVersionVO> listByFlow(String flowDefId) {
        return baseMapper.selectList(new LambdaQueryWrapper<BpmFlowVersion>()
                .eq(BpmFlowVersion::getFlowDefId, flowDefId).orderByDesc(BpmFlowVersion::getVersionNo))
                .stream().map(this::toVO).collect(Collectors.toList());
    }
    @Override public VersionDetailVO detail(String flowDefId, Integer versionNo) {
        BpmFlowVersion v = findByVersionNo(flowDefId, versionNo);
        VersionDetailVO vo = new VersionDetailVO();
        vo.setVersion(toVO(v));
        vo.setNodes(nodeMapper.selectList(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, v.getId())).stream().map(this::toNodeVO).collect(Collectors.toList()));
        vo.setEdges(edgeMapper.selectList(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, v.getId())).stream().map(this::toEdgeVO).collect(Collectors.toList()));
        return vo;
    }
    @Override @Transactional public Integer createDraft(String flowDefId, VersionCreateRequest req) {
        BpmFlowVersion source;
        if (Boolean.TRUE.equals(req.getFromPublished())) {
            source = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowVersion>()
                    .eq(BpmFlowVersion::getFlowDefId, flowDefId)
                    .eq(BpmFlowVersion::getStatus, VersionStatus.published.name())
                    .orderByDesc(BpmFlowVersion::getVersionNo).last("limit 1"));
        } else {
            source = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowVersion>()
                    .eq(BpmFlowVersion::getFlowDefId, flowDefId)
                    .orderByDesc(BpmFlowVersion::getVersionNo).last("limit 1"));
        }
        if (source == null) throw BpmErrorCode.FLOW_NOT_FOUND.of(flowDefId);
        Integer maxNo = baseMapper.selectList(new LambdaQueryWrapper<BpmFlowVersion>().eq(BpmFlowVersion::getFlowDefId, flowDefId))
                .stream().map(BpmFlowVersion::getVersionNo).max(Integer::compareTo).orElse(0);
        int nextNo = maxNo + 1;
        BpmFlowVersion v = new BpmFlowVersion();
        v.setFlowDefId(flowDefId);
        v.setVersionNo(nextNo);
        v.setStatus(VersionStatus.draft.name());
        v.setCanvasData(source.getCanvasData());
        v.setNodesSnapshot(source.getNodesSnapshot());
        v.setEdgesSnapshot(source.getEdgesSnapshot());
        v.setRemark(StrUtil.isBlank(req.getRemark()) ? "从 v" + source.getVersionNo() + " 复制" : req.getRemark());
        baseMapper.insert(v);
        List<BpmFlowNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, source.getId()));
        for (BpmFlowNode n : nodes) {
            n.setId(null);
            n.setVersionId(v.getId());
            nodeMapper.insert(n);
        }
        List<BpmFlowEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, source.getId()));
        for (BpmFlowEdge e : edges) {
            e.setId(null);
            e.setVersionId(v.getId());
            edgeMapper.insert(e);
        }
        BpmFlowDef def = new BpmFlowDef();
        def.setId(flowDefId);
        def.setCurrentVersionId(v.getId());
        def.setUpdatedAt(LocalDateTime.now());
        defMapper.updateById(def);
        return nextNo;
    }
    @Override @Transactional public FlowVersionVO publish(String flowDefId, Integer versionNo, String operatorId) {
        BpmFlowVersion v = findByVersionNo(flowDefId, versionNo);
        if (!VersionStatus.draft.name().equals(v.getStatus()))
            throw BpmErrorCode.INSTANCE_STATE_INVALID.of("版本状态非 draft,不能发布: v" + versionNo);
        List<BpmFlowVersion> olds = baseMapper.selectList(new LambdaQueryWrapper<BpmFlowVersion>()
                .eq(BpmFlowVersion::getFlowDefId, flowDefId).eq(BpmFlowVersion::getStatus, VersionStatus.published.name()));
        for (BpmFlowVersion old : olds) {
            if (!old.getId().equals(v.getId())) {
                old.setStatus(VersionStatus.archived.name());
                baseMapper.updateById(old);
            }
        }
        v.setStatus(VersionStatus.published.name());
        v.setPublisherId(operatorId);
        v.setPublishedAt(LocalDateTime.now());
        baseMapper.updateById(v);
        BpmFlowDef def = new BpmFlowDef();
        def.setId(flowDefId);
        def.setCurrentVersionId(v.getId());
        def.setUpdatedAt(LocalDateTime.now());
        defMapper.updateById(def);
        return toVO(v);
    }
    @Override @Transactional public FlowVersionVO rollback(String flowDefId, Integer versionNo) {
        BpmFlowVersion target = findByVersionNo(flowDefId, versionNo);
        if (!VersionStatus.published.name().equals(target.getStatus()))
            throw BpmErrorCode.INSTANCE_STATE_INVALID.of("回滚目标版本必须为 published: v" + versionNo);
        List<BpmFlowVersion> olds = baseMapper.selectList(new LambdaQueryWrapper<BpmFlowVersion>()
                .eq(BpmFlowVersion::getFlowDefId, flowDefId).eq(BpmFlowVersion::getStatus, VersionStatus.published.name()));
        for (BpmFlowVersion old : olds) {
            if (!old.getId().equals(target.getId())) {
                old.setStatus(VersionStatus.archived.name());
                baseMapper.updateById(old);
            }
        }
        target.setStatus(VersionStatus.published.name());
        baseMapper.updateById(target);
        BpmFlowDef def = new BpmFlowDef();
        def.setId(flowDefId);
        def.setCurrentVersionId(target.getId());
        def.setUpdatedAt(LocalDateTime.now());
        defMapper.updateById(def);
        return toVO(target);
    }
    @Override public void archive(String flowDefId, Integer versionNo) {
        BpmFlowVersion v = findByVersionNo(flowDefId, versionNo);
        v.setStatus(VersionStatus.archived.name());
        baseMapper.updateById(v);
    }
    private BpmFlowVersion findByVersionNo(String flowDefId, Integer versionNo) {
        BpmFlowVersion v = baseMapper.selectOne(new LambdaQueryWrapper<BpmFlowVersion>()
                .eq(BpmFlowVersion::getFlowDefId, flowDefId).eq(BpmFlowVersion::getVersionNo, versionNo));
        if (v == null) throw BpmErrorCode.FLOW_VERSION_NOT_FOUND.of("v" + versionNo);
        return v;
    }
    private FlowVersionVO toVO(BpmFlowVersion v) { FlowVersionVO vo = new FlowVersionVO(); BeanUtils.copyProperties(v, vo); return vo; }
    private NodeVO toNodeVO(BpmFlowNode n) { NodeVO vo = new NodeVO(); BeanUtils.copyProperties(n, vo); return vo; }
    private EdgeVO toEdgeVO(BpmFlowEdge e) { EdgeVO vo = new EdgeVO(); BeanUtils.copyProperties(e, vo); return vo; }
}