package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fastrag.module.bpm.dto.FlowTemplateVO;
import com.fastrag.module.bpm.entity.BpmFlowTemplate;
import com.fastrag.module.bpm.mapper.BpmFlowTemplateMapper;
import com.fastrag.module.bpm.service.BpmFlowTemplateService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BpmFlowTemplateServiceImpl extends ServiceImpl<BpmFlowTemplateMapper, BpmFlowTemplate> implements BpmFlowTemplateService {
    @Override public List<FlowTemplateVO> listAll(Boolean builtinOnly, String category) {
        LambdaQueryWrapper<BpmFlowTemplate> q = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(builtinOnly)) q.eq(BpmFlowTemplate::getIsBuiltin, 1);
        if (StrUtil.isNotBlank(category)) q.eq(BpmFlowTemplate::getCategory, category);
        q.orderByDesc(BpmFlowTemplate::getIsBuiltin).orderByAsc(BpmFlowTemplate::getName);
        return baseMapper.selectList(q).stream().map(this::toVO).collect(Collectors.toList());
    }
    @Override public FlowTemplateVO get(String id) {
        BpmFlowTemplate t = baseMapper.selectById(id);
        return t == null ? null : toVO(t);
    }
    @Override public String applyTemplate(String templateId, String flowDefId) {
        // 模板应用需要重新加载节点/边并生成 draft 版本,逻辑较重,由 CanvasService 在 M2e 提供
        return templateId + " -> " + flowDefId;
    }
    private FlowTemplateVO toVO(BpmFlowTemplate t) {
        FlowTemplateVO vo = new FlowTemplateVO();
        BeanUtils.copyProperties(t, vo);
        return vo;
    }
}