package com.fastrag.module.operation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fastrag.module.operation.model.UnifiedLogDTO;
import com.fastrag.module.operation.model.UnifiedLogQuery;

/**
 * 统一日志查询服务
 */
public interface UnifiedLogService {

    /**
     * 分页查询统一日志
     *
     * @param query 查询参数（category 必填）
     * @return 分页结果
     */
    IPage<UnifiedLogDTO> pageQuery(UnifiedLogQuery query);
}
