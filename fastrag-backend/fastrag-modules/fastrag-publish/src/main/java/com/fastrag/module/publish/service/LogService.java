package com.fastrag.module.publish.service;

import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.entity.KbUpdateLog;

import java.util.List;

public interface LogService {
    List<KbLog> listLogs(String kbId, String category);
    List<KbUpdateLog> listUpdateLogs(String kbId, String type);
    void addLog(String kbId, String category, String action, String target, String detail, String operator);
    void addUpdateLog(String kbId, String updateType, String target, String detail, String operator);
    /** 标记单条更新日志为已读 */
    void markUpdateLogRead(String id);
}
