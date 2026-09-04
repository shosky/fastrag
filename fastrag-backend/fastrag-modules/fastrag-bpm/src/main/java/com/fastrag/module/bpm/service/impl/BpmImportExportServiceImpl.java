package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.*;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.enums.VersionStatus;
import com.fastrag.module.bpm.mapper.*;
import com.fastrag.module.bpm.service.BpmImportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 导入导出服务:JSON 文档版本 1 */
@Service @RequiredArgsConstructor
public class BpmImportExportServiceImpl implements BpmImportExportService {
    private final BpmFlowDefMapper defMapper;
    private final BpmFlowVersionMapper versionMapper;
    private final BpmFlowNodeMapper nodeMapper;
    private final BpmFlowEdgeMapper edgeMapper;
    private final BpmFlowDefServiceImpl flowDefService; // for create()
    private final ObjectMapper json = new ObjectMapper();

    @Override public FlowExportVO export(String flowDefId, String operatorId) {
        BpmFlowDef def = defMapper.selectById(flowDefId);
        if (def == null) throw BpmErrorCode.FLOW_NOT_FOUND.of(flowDefId);
        FlowExportVO vo = new FlowExportVO();
        vo.setExportedAt(LocalDateTime.now().toString());
        vo.setExportedBy(operatorId);
        FlowDefVO defVo = flowDefService.detail(flowDefId);
        vo.setDef(defVo);
        if (StrUtil.isNotBlank(def.getCurrentVersionId())) {
            BpmFlowVersion v = versionMapper.selectById(def.getCurrentVersionId());
            if (v != null) {
                FlowVersionVO vVo = new FlowVersionVO();
                BeanUtils.copyProperties(v, vVo);
                vo.setCurrentVersion(vVo);
                vo.setNodes(nodeMapper.selectList(new LambdaQueryWrapper<BpmFlowNode>().eq(BpmFlowNode::getVersionId, v.getId())).stream().map(n -> {
                    NodeVO x = new NodeVO(); BeanUtils.copyProperties(n, x); return x;
                }).collect(Collectors.toList()));
                vo.setEdges(edgeMapper.selectList(new LambdaQueryWrapper<BpmFlowEdge>().eq(BpmFlowEdge::getVersionId, v.getId())).stream().map(e -> {
                    EdgeVO x = new EdgeVO(); BeanUtils.copyProperties(e, x); return x;
                }).collect(Collectors.toList()));
            }
        }
        return vo;
    }
    @Override @Transactional public String importFlow(ImportFlowRequest req, String operatorId) {
        if (StrUtil.isBlank(req.getPayload())) throw BpmErrorCode.IMPORT_FORMAT_INVALID.of("payload 必填");
        FlowExportVO doc;
        try { doc = json.readValue(req.getPayload(), FlowExportVO.class); }
        catch (Exception e) { throw BpmErrorCode.IMPORT_FORMAT_INVALID.of("JSON 解析失败: " + e.getMessage()); }
        if (doc == null || doc.getDef() == null) throw BpmErrorCode.IMPORT_FORMAT_INVALID.of("缺少 def");
        if (doc.getExportVersion() == null || doc.getExportVersion() > 1)
            throw BpmErrorCode.IMPORT_VERSION_NOT_SUPPORTED.of("v" + doc.getExportVersion());
        // 创建流程
        FlowDefRequest creq = new FlowDefRequest();
        creq.setName(StrUtil.isBlank(req.getNewName()) ? doc.getDef().getName() + " 导入" : req.getNewName());
        creq.setDescription(doc.getDef().getDescription());
        creq.setCategory(doc.getDef().getCategory());
        creq.setTimeoutMs(doc.getDef().getTimeoutMs());
        creq.setTriggerType(doc.getDef().getTriggerType());
        FlowDefVO created = flowDefService.create(creq, operatorId);
        String newFlowId = created.getId();
        // 用导入的 nodes/edges 覆盖 v1 draft
        if (doc.getNodes() != null || doc.getEdges() != null) {
            String versionId = created.getCurrentVersionId();
            CanvasSaveRequest save = new CanvasSaveRequest();
            if (doc.getNodes() != null) {
                save.setNodes(doc.getNodes().stream().map(n -> {
                    NodeRequest r = new NodeRequest();
                    BeanUtils.copyProperties(n, r);
                    return r;
                }).collect(Collectors.toList()));
            }
            if (doc.getEdges() != null) {
                save.setEdges(doc.getEdges().stream().map(e -> {
                    EdgeRequest r = new EdgeRequest();
                    BeanUtils.copyProperties(e, r);
                    return r;
                }).collect(Collectors.toList()));
            }
            canvasService().saveCanvas(newFlowId, versionId, save);
        }
        return newFlowId;
    }
    /** 通过 Spring 容器获取 BpmCanvasService(避免循环依赖导入) */
    private com.fastrag.module.bpm.service.BpmCanvasService canvasService() {
        return SpringContextHolder.getBean(com.fastrag.module.bpm.service.BpmCanvasService.class);
    }
    /** 极简 Spring 上下文工具(只支持 getBean) */
    static final class SpringContextHolder implements org.springframework.context.ApplicationContextAware {
        private static org.springframework.context.ApplicationContext ctx;
        @Override public void setApplicationContext(org.springframework.context.ApplicationContext applicationContext) {
            ctx = applicationContext;
        }
        static <T> T getBean(Class<T> cls) { return ctx.getBean(cls); }
    }
    @org.springframework.stereotype.Component static class SpringContextHolderInit implements org.springframework.context.ApplicationContextAware {
        @Override public void setApplicationContext(org.springframework.context.ApplicationContext applicationContext) {
            SpringContextHolder.ctx = applicationContext;
        }
    }
}