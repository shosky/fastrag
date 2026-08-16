package com.fastrag.module.publish.service;

/**
 * 知识库操作日志与更新日志服务接口。
 *
 * <p>定义知识库操作日志（{@link KbLog}）和更新日志（{@link KbUpdateLog}）的
 * 查询与写入能力。提供多组重载方法以兼容不同调用场景：</p>
 * <ul>
 *   <li>字符串参数版本：用于向后兼容旧代码</li>
 *   <li>枚举参数版本：支持使用 {@link LogCategory} 和 {@link ActionType} 枚举，并可指定 status 和 extra</li>
 *   <li>更新日志支持记录变更前后的新旧值（oldValue / newValue）</li>
 * </ul>
 *
 * @see KbLog
 * @see KbUpdateLog
 */
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.entity.KbUpdateLog;

import java.util.List;
import java.util.Map;

public interface LogService {

    // ==================== 查询接口 ====================

    /**
     * 分页查询知识库操作日志，支持按 category 过滤和 keyword 模糊搜索（对象/详情/操作人）
     */
    PageResult<KbLog> listLogs(String kbId, String category, String keyword, int page, int pageSize);

    /**
     * 统计知识库日志总数及各分类数量，返回 {@code {total, operation, retrieval, publish}}
     */
    Map<String, Long> countByCategory(String kbId);

    List<KbUpdateLog> listUpdateLogs(String kbId, String type);

    // ==================== 写入接口（字符串参数，向后兼容） ====================

    /**
     * 写入业务日志（字符串参数，向后兼容）
     * <p>status 默认写入 "success"
     */
    void addLog(String kbId, String category, String action, String target, String detail, String operator);

    /**
     * 写入更新日志（字符串参数，向后兼容）
     */
    void addUpdateLog(String kbId, String updateType, String target, String detail, String operator);

    // ==================== 写入接口（枚举参数 + status + extra） ====================

    /**
     * 写入业务日志（枚举参数，支持 status）
     */
    void addLog(String kbId, LogCategory category, ActionType action, String target, String detail, String operator, String status);

    /**
     * 写入业务日志（枚举参数，支持 status + extra）
     */
    void addLog(String kbId, LogCategory category, ActionType action, String target, String detail, String operator, String status, String extra);

    /**
     * 写入更新日志（含 oldValue / newValue）
     */
    void addUpdateLog(String kbId, String updateType, String target, String detail, String operator, String oldValue, String newValue);

    // ==================== 辅助接口 ====================

    /** 标记单条更新日志为已读（当前由前端本地处理，后端不做持久化） */
    void markUpdateLogRead(String id);
}
