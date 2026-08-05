package com.fastrag.module.operation.service;

import com.fastrag.module.operation.entity.SysAuditLog;

import java.util.List;

public interface AuditLogService {

    List<SysAuditLog> list(String module, Integer limit);

    /**
     * 写入系统审计日志
     * <p>写入失败自动吞掉异常，不影响主业务流程。
     */
    void addLog(String userId, String username, String module, String action,
                String target, String detail, String ip, String status);
}
