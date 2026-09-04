package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.*;
import com.fastrag.module.bpm.enums.*;
import com.fastrag.module.bpm.mapper.*;
import com.fastrag.module.bpm.service.BpmFlowDefService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class BpmFlowDefServiceImpl extends ServiceImpl<BpmFlowDefMapper, BpmFlowDef> implements BpmFlowDefService {
    private final BpmFlowVersionMapper versionMapper;
    private final BpmFlowNodeMapper nodeMapper;
    private final BpmFlowEdgeMapper edgeMapper;

    @Override public PageResult<FlowDefVO> page(FlowDefPageReq req) {
        if (req.getPage() == null || req.getPage() < 1) req.setPage(1);
        if (req.getSize() == null || req.getSize() < 1 || req.getSize() > 100) req.setSize(20);
        LambdaQueryWrapper<BpmFlowDef> q = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(req.getKeyword()))
            q.and(w -> w.like(BpmFlowDef::getName, req.getKeyword()).or().like(BpmFlowDef::getDescription, req.getKeyword()));
        if (StrUtil.isNotBlank(req.getCategory())) q.eq(BpmFlowDef::getCategory, req.getCategory());
        if (StrUtil.isNotBlank(req.getVisibility())) q.eq(BpmFlowDef::getVisibility, FlowVisibility.of(req.getVisibility()).name());
        q.orderByDesc(BpmFlowDef::getUpdatedAt);
        IPage<BpmFlowDef> page = baseMapper.selectPage(new Page<>(req.getPage(), req.getSize()), q);
        List<FlowDefVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), (int) page.getCurrent(), (int) page.getSize(), records);
    }

    @Override public List<FlowDefVO> listSimple(String keyword, String visibility) {
        LambdaQueryWrapper<BpmFlowDef> q = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) q.like(BpmFlowDef::getName, keyword);
        if (StrUtil.isNotBlank(visibility)) q.eq(BpmFlowDef::getVisibility, FlowVisibility.of(visibility).name());
        q.orderByDesc(BpmFlowDef::getUpdatedAt).last("limit 100");
        return baseMapper.selectList(q).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override public FlowDefVO detail(String id) {
        BpmFlowDef def = baseMapper.selectById(id);
        if (def == null) throw BpmErrorCode.FLOW_NOT_FOUND.of(id);
        FlowDefVO vo = toVO(def);
        // 最近 5 个版本
        List<BpmFlowVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<BpmFlowVersion>().eq(BpmFlowVersion::getFlowDefId, id)
                        .orderByDesc(BpmFlowVersion::getVersionNo).last("limit 5"));
        vo.setRecentVersions(versions.stream().map(this::toVersionVO).collect(Collectors.toList()));
        // 当前版本号
        if (StrUtil.isNotBlank(def.getCurrentVersionId())) {
            BpmFlowVersion cur = versionMapper.selectById(def.getCurrentVersionId());
            if (cur != null) {
                vo.setCurrentVersionNo(cur.getVersionNo());
                vo.setCurrentVersionStatus(cur.getStatus());
            }
        }
        // 节点/边数量
        if (vo.getCurrentVersionId() != null) {
            Long nodeCount = nodeMapper.selectCount(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, vo.getCurrentVersionId()));
            Long edgeCount = edgeMapper.selectCount(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, vo.getCurrentVersionId()));
            vo.setNodeCount(nodeCount == null ? 0 : nodeCount.intValue());
            vo.setEdgeCount(edgeCount == null ? 0 : edgeCount.intValue());
        }
        return vo;
    }

    @Override @Transactional public FlowDefVO create(FlowDefRequest req, String operatorId) {
        BpmFlowDef def = new BpmFlowDef();
        BeanUtils.copyProperties(req, def, "id");
        def.setOwnerId(operatorId);
        def.setVisibility(StrUtil.isBlank(req.getVisibility()) ? FlowVisibility.private_flow.name() : FlowVisibility.of(req.getVisibility()).name());
        def.setTriggerType(StrUtil.isBlank(req.getTriggerType()) ? "manual" : req.getTriggerType());
        def.setTimeoutMs(req.getTimeoutMs() == null ? 86400000 : req.getTimeoutMs());
        def.setLogSnapshotEnabled(req.getLogSnapshotEnabled() == null ? Boolean.TRUE : req.getLogSnapshotEnabled());
        baseMapper.insert(def);
        // 同步创建 v1 draft 版本(空画布)
        BpmFlowVersion v1 = new BpmFlowVersion();
        v1.setFlowDefId(def.getId());
        v1.setVersionNo(1);
        v1.setStatus(VersionStatus.draft.name());
        v1.setCanvasData("{\"nodes\":[],\"edges\":[]}");
        v1.setNodesSnapshot("[]");
        v1.setEdgesSnapshot("[]");
        versionMapper.insert(v1);
        // 反写当前版本
        def.setCurrentVersionId(v1.getId());
        baseMapper.updateById(def);
        return detail(def.getId());
    }

    @Override public FlowDefVO update(String id, FlowDefRequest req) {
        BpmFlowDef def = baseMapper.selectById(id);
        if (def == null) throw BpmErrorCode.FLOW_NOT_FOUND.of(id);
        if (StrUtil.isNotBlank(req.getName())) def.setName(req.getName());
        if (req.getDescription() != null) def.setDescription(req.getDescription());
        if (StrUtil.isNotBlank(req.getCategory())) def.setCategory(req.getCategory());
        if (StrUtil.isNotBlank(req.getVisibility())) def.setVisibility(FlowVisibility.of(req.getVisibility()).name());
        if (req.getTimeoutMs() != null) def.setTimeoutMs(req.getTimeoutMs());
        if (StrUtil.isNotBlank(req.getTriggerType())) def.setTriggerType(req.getTriggerType());
        if (req.getLogSnapshotEnabled() != null) def.setLogSnapshotEnabled(req.getLogSnapshotEnabled());
        baseMapper.updateById(def);
        return detail(id);
    }

    @Override @Transactional public void delete(String id) {
        BpmFlowDef def = baseMapper.selectById(id);
        if (def == null) throw BpmErrorCode.FLOW_NOT_FOUND.of(id);
        // 级联:版本->节点->边(物理删除)
        List<BpmFlowVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<BpmFlowVersion>().eq(BpmFlowVersion::getFlowDefId, id));
        List<String> versionIds = versions.stream().map(BpmFlowVersion::getId).collect(Collectors.toList());
        if (!versionIds.isEmpty()) {
            edgeMapper.delete(new LambdaQueryWrapper<BpmFlowEdge>().in(BpmFlowEdge::getVersionId, versionIds));
            nodeMapper.delete(new LambdaQueryWrapper<BpmFlowNode>().in(BpmFlowNode::getVersionId, versionIds));
            versionMapper.delete(new LambdaQueryWrapper<BpmFlowVersion>().eq(BpmFlowVersion::getFlowDefId, id));
        }
        baseMapper.deleteById(id);
    }

    @Override @Transactional public FlowDefVO copy(String id, String newName, String operatorId) {
        BpmFlowDef src = baseMapper.selectById(id);
        if (src == null) throw BpmErrorCode.FLOW_NOT_FOUND.of(id);
        BpmFlowDef dst = new BpmFlowDef();
        dst.setName(StrUtil.isBlank(newName) ? (src.getName() + " - 副本") : newName);
        dst.setDescription(src.getDescription());
        dst.setCategory(src.getCategory());
        dst.setOwnerId(operatorId);
        dst.setVisibility(FlowVisibility.private_flow.name());
        dst.setTimeoutMs(src.getTimeoutMs());
        dst.setTriggerType(src.getTriggerType());
        dst.setLogSnapshotEnabled(src.getLogSnapshotEnabled());
        baseMapper.insert(dst);
        // 复制 current version(如无则用最新版本)
        String srcVerId = src.getCurrentVersionId();
        if (StrUtil.isBlank(srcVerId)) {
            BpmFlowVersion latest = versionMapper.selectOne(new LambdaQueryWrapper<BpmFlowVersion>()
                    .eq(BpmFlowVersion::getFlowDefId, id).orderByDesc(BpmFlowVersion::getVersionNo).last("limit 1"));
            srcVerId = latest == null ? null : latest.getId();
        }
        if (srcVerId != null) {
            BpmFlowVersion srcV = versionMapper.selectById(srcVerId);
            BpmFlowVersion newV = new BpmFlowVersion();
            newV.setFlowDefId(dst.getId());
            newV.setVersionNo(1);
            newV.setStatus(VersionStatus.draft.name());
            newV.setCanvasData(srcV.getCanvasData());
            newV.setNodesSnapshot(srcV.getNodesSnapshot());
            newV.setEdgesSnapshot(srcV.getEdgesSnapshot());
            newV.setRemark("复制自 " + src.getName());
            versionMapper.insert(newV);
            // 复制节点/边
            List<BpmFlowNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, srcVerId));
            if (!nodes.isEmpty()) {
                for (BpmFlowNode n : nodes) {
                    String oldKey = n.getNodeKey();
                    n.setId(null);
                    n.setVersionId(newV.getId());
                    nodeMapper.insert(n);
                    if (!oldKey.equals(n.getNodeKey())) {
                        // 若主键自动重新生成,key 仍保持原值(因为是字段而非主键);此处无需替换
                    }
                }
            }
            List<BpmFlowEdge> edges = edgeMapper.selectList(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, srcVerId));
            if (!edges.isEmpty()) {
                for (BpmFlowEdge e : edges) {
                    e.setId(null);
                    e.setVersionId(newV.getId());
                    edgeMapper.insert(e);
                }
            }
            dst.setCurrentVersionId(newV.getId());
            baseMapper.updateById(dst);
        }
        return detail(dst.getId());
    }

    private FlowDefVO toVO(BpmFlowDef def) {
        FlowDefVO vo = new FlowDefVO();
        BeanUtils.copyProperties(def, vo);
        return vo;
    }
    private FlowVersionVO toVersionVO(BpmFlowVersion v) {
        FlowVersionVO vo = new FlowVersionVO();
        BeanUtils.copyProperties(v, vo);
        return vo;
    }
}