package com.fastrag.module.bpm.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fastrag.module.bpm.entity.BpmFlowInstance;
import com.fastrag.module.bpm.entity.BpmFlowInstanceEvent;
import com.fastrag.module.bpm.enums.InstanceStatus;
import com.fastrag.module.bpm.mapper.BpmFlowInstanceEventMapper;
import com.fastrag.module.bpm.mapper.BpmFlowInstanceMapper;
import com.fastrag.module.bpm.service.BpmStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service @RequiredArgsConstructor
public class BpmStatsServiceImpl implements BpmStatsService {
    private final BpmFlowInstanceMapper instanceMapper;
    private final BpmFlowInstanceEventMapper eventMapper;

    @Override public Map<String, Object> flowStats(String flowDefId) {
        List<BpmFlowInstance> all = instanceMapper.selectList(new LambdaQueryWrapper<BpmFlowInstance>().eq(BpmFlowInstance::getFlowDefId, flowDefId));
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (InstanceStatus s : InstanceStatus.values()) statusCounts.put(s.name(), 0L);
        long totalDur = 0; int durCount = 0;
        for (BpmFlowInstance ins : all) {
            statusCounts.merge(ins.getStatus(), 1L, Long::sum);
            if (ins.getDurationMs() != null) { totalDur += ins.getDurationMs(); durCount++; }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("flowDefId", flowDefId);
        r.put("total", all.size());
        r.put("statusDistribution", statusCounts);
        r.put("avgDurationMs", durCount == 0 ? 0 : totalDur / durCount);
        r.put("successRate", all.isEmpty() ? 0.0 : (statusCounts.getOrDefault("completed", 0L) * 1.0 / all.size()));
        return r;
    }
    @Override public List<?> instanceTimeline(String instanceId) {
        return eventMapper.selectList(new LambdaQueryWrapper<BpmFlowInstanceEvent>()
                .eq(BpmFlowInstanceEvent::getInstanceId, instanceId).orderByAsc(BpmFlowInstanceEvent::getCreatedAt));
    }
    @Override public Map<String, Object> globalStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalInstances", instanceMapper.selectCount(null));
        r.put("running", instanceMapper.selectCount(new LambdaQueryWrapper<BpmFlowInstance>().eq(BpmFlowInstance::getStatus, InstanceStatus.running.name())));
        r.put("completed", instanceMapper.selectCount(new LambdaQueryWrapper<BpmFlowInstance>().eq(BpmFlowInstance::getStatus, InstanceStatus.completed.name())));
        r.put("failed", instanceMapper.selectCount(new LambdaQueryWrapper<BpmFlowInstance>().eq(BpmFlowInstance::getStatus, InstanceStatus.failed.name())));
        return r;
    }
}