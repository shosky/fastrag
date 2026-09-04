package com.fastrag.module.bpm.service;
import com.fastrag.module.bpm.dto.CanvasSaveRequest;
import com.fastrag.module.bpm.dto.CanvasValidateResultVO;

import java.util.List;

public interface BpmCanvasService {
    /** 整图保存:覆盖当前 draft 版本(nodes/edges/canvas_data),仅 draft 允许保存 */
    void saveCanvas(String flowDefId, String versionId, CanvasSaveRequest req);
    /** 校验指定版本的画布结构(必含 start/end、不可达、环等) */
    CanvasValidateResultVO validate(String versionId);
    /** 基于 nodes+edges 计算结构性校验 */
    CanvasValidateResultVO validateStructure(List<?> nodes, List<?> edges);
}