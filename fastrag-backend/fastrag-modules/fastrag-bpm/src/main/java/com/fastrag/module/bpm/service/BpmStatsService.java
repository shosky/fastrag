package com.fastrag.module.bpm.service;
import java.util.Map;

public interface BpmStatsService {
    /** 单流程监控面板:状态分布 + 平均时长 + 总量 */
    Map<String, Object> flowStats(String flowDefId);
    /** 实例事件时间线(供监控详情) */
    java.util.List<?> instanceTimeline(String instanceId);
    /** 全局统计 */
    Map<String, Object> globalStats();
}