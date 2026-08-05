package com.fastrag.module.operation.listener;

import com.fastrag.common.event.SysAuditLogEvent;
import com.fastrag.module.operation.entity.SysAuditLog;
import com.fastrag.module.operation.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 系统审计日志事件监听器
 * <p>监听 {@link SysAuditLogEvent}，将审计日志写入 {@code sys_audit_log} 表。
 * <p>写入失败不影响主业务流程（异常被吞掉）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysAuditLogEventListener {

    private final SysAuditLogMapper auditLogMapper;

    @EventListener
    public void handleAuditLogEvent(SysAuditLogEvent event) {
        try {
            SysAuditLog auditLog = new SysAuditLog();
            auditLog.setUserId(event.getUserId());
            auditLog.setUsername(event.getUsername());
            auditLog.setModule(event.getModule());
            auditLog.setAction(event.getAction() != null ? event.getAction().name() : null);
            auditLog.setTarget(event.getTarget());
            auditLog.setDetail(event.getDetail());
            auditLog.setIp(event.getIp());
            auditLog.setStatus(event.getStatus());
            auditLog.setTimestamp(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("[AuditListener] Failed to write sys_audit_log: {}", e.getMessage());
        }
    }
}
