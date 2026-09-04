package com.fastrag.module.bpm.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.bpm.config.BpmProperties;
import com.fastrag.module.bpm.entity.BpmFlowInstance;
import com.fastrag.module.bpm.enums.InstanceStatus;
import com.fastrag.module.bpm.mapper.BpmFlowInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 超时扫描:每 30s 扫描 running 实例,超过 maxDurationHours 或 timeout_at 截止的标记为 failed */
        @Slf4j @Component @RequiredArgsConstructor
public class BpmTimeoutScanner {
    private final BpmFlowInstanceMapper instanceMapper;
    private final BpmProperties props;

    @Scheduled(cron = "${bpm.scheduler.timeout-scan-cron}")
    public void scan() {
        if (!props.getScheduler().isEnabled()) return;
        long maxHours = props.getInstance().getMaxDurationHours();
        List<BpmFlowInstance> running = instanceMapper.selectList(new LambdaQueryWrapper<BpmFlowInstance>().eq(BpmFlowInstance::getStatus, InstanceStatus.running.name()));
        LocalDateTime now = LocalDateTime.now();
        int cancelled = 0;
        for (BpmFlowInstance ins : running) {
            boolean expired = false;
            if (ins.getTimeoutAt() != null && now.isAfter(ins.getTimeoutAt())) expired = true;
            if (ins.getStartedAt() != null && Duration.between(ins.getStartedAt(), now).toHours() >= maxHours) expired = true;
            if (expired) {
                ins.setStatus(InstanceStatus.failed.name());
                ins.setFailureReason("流程超时(超过 " + maxHours + "h 或 timeout_at 截止)");
                ins.setFinishedAt(now);
                if (ins.getStartedAt() != null) ins.setDurationMs(Duration.between(ins.getStartedAt(), now).toMillis());
                instanceMapper.updateById(ins);
                cancelled++;
            }
        }
        if (cancelled > 0) log.info("BpmTimeoutScanner: 超时终止 {} 个实例", cancelled);
    }
}