package com.fastrag.module.bpm.service;
import com.fastrag.module.bpm.dto.FlowExportVO;
import com.fastrag.module.bpm.dto.ImportFlowRequest;

public interface BpmImportExportService {
    /** 导出:含 def + currentVersion + nodes + edges */
    FlowExportVO export(String flowDefId, String operatorId);
    /** 导入:解析 payload → 创建新流程(含 v1 draft + nodes + edges) */
    String importFlow(ImportFlowRequest req, String operatorId);
}