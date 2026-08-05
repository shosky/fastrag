package com.fastrag.module.operation.service;

import com.fastrag.module.operation.model.ModelMonitorData;

/**
 * 模型监控分析服务
 */
public interface ModelMonitorService {

    /**
     * 获取模型监控总览数据
     *
     * @param timeRange 时间范围（天），如 7 / 30 / 180
     * @param keyword   模型 Code 搜索关键字（可选）
     * @param page      模型统计分页页码
     * @param pageSize  模型统计分页每页条数
     * @return 总览面板数据
     */
    ModelMonitorData getOverview(int timeRange, String keyword, int page, int pageSize);
}
