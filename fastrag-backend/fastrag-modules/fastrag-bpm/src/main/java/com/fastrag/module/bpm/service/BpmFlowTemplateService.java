package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.FlowTemplateVO;
import com.fastrag.module.bpm.entity.BpmFlowTemplate;

import java.util.List;

public interface BpmFlowTemplateService extends IService<BpmFlowTemplate> {
    List<FlowTemplateVO> listAll(Boolean builtinOnly, String category);
    FlowTemplateVO get(String id);
    /** 应用模板到指定流程(创建新草稿版本,并复制 nodes/edges) */
    String applyTemplate(String templateId, String flowDefId);
}