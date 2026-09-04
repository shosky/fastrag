package com.fastrag.module.bpm.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.bpm.dto.InstanceTriggerRequest;
import com.fastrag.module.bpm.dto.TestCaseRequest;
import com.fastrag.module.bpm.entity.BpmFlowTestCase;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.mapper.BpmFlowTestCaseMapper;
import com.fastrag.module.bpm.service.BpmFlowDefService;
import com.fastrag.module.bpm.service.BpmInstanceService;
import com.fastrag.module.bpm.service.BpmTestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class BpmTestCaseServiceImpl extends ServiceImpl<BpmFlowTestCaseMapper, BpmFlowTestCase> implements BpmTestCaseService {
    private final BpmInstanceService instanceService;
    private final BpmFlowDefService flowDefService;
    private final ObjectMapper json = new ObjectMapper();

    @Override public List<BpmFlowTestCase> listByFlow(String flowDefId) {
        return baseMapper.selectList(new LambdaQueryWrapper<BpmFlowTestCase>().eq(BpmFlowTestCase::getFlowDefId, flowDefId));
    }
    @Override public BpmFlowTestCase create(String flowDefId, TestCaseRequest req, String operatorId) {
        if (StrUtil.isBlank(req.getName())) throw BpmErrorCode.NODE_CONFIG_INVALID.of("name 必填");
        BpmFlowTestCase tc = new BpmFlowTestCase();
        tc.setFlowDefId(flowDefId);
        tc.setName(req.getName());
        try { tc.setInputs(req.getInputs() == null ? "{}" : json.writeValueAsString(req.getInputs())); }
        catch (Exception e) { throw BpmErrorCode.NODE_CONFIG_INVALID.of("inputs 序列化失败"); }
        tc.setExpectedOutput(req.getExpectedOutput());
        tc.setCreatedBy(operatorId);
        baseMapper.insert(tc);
        return tc;
    }
    @Override public void delete(String id) { baseMapper.deleteById(id); }
    @Override public BpmFlowTestCase run(String id) {
        BpmFlowTestCase tc = baseMapper.selectById(id);
        if (tc == null) throw BpmErrorCode.TEST_CASE_NOT_FOUND.of(id);
        // 触发实例
        InstanceTriggerRequest ireq = new InstanceTriggerRequest();
        ireq.setFlowDefId(tc.getFlowDefId());
        ireq.setInputParams(parseJson(tc.getInputs()));
        ireq.setTriggerType("test");
        String instanceId = instanceService.trigger(ireq);
        // 取结果
        var instance = instanceService.detail(instanceId);
        String actual = instance.getOutputs() == null ? "" : instance.getOutputs().toString();
        tc.setActualOutput(actual);
        tc.setLastInstanceId(instanceId);
        tc.setLastRunAt(LocalDateTime.now());
        // 简单匹配:expected 与 actual 的字符串包含关系,或 exact equals
        boolean match = false;
        if (StrUtil.isNotBlank(tc.getExpectedOutput())) match = actual.contains(tc.getExpectedOutput());
        else match = StrUtil.isNotBlank(actual);
        tc.setMatchResult(match);
        baseMapper.updateById(tc);
        return tc;
    }
    private java.util.Map<String, Object> parseJson(String s) {
        if (StrUtil.isBlank(s)) return new java.util.HashMap<>();
        try { return json.readValue(s, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}); }
        catch (Exception e) { return new java.util.HashMap<>(); }
    }
}