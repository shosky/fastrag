package com.fastrag.module.publish.service;

import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.entity.KbUpdateLog;

import java.util.List;

public interface LogService {

    // ==================== 查询接口 ====================

    List<KbLog> listLogs(String kbId, String category);

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
