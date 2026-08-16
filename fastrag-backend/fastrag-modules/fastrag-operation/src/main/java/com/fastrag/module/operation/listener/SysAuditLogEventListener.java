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
 * 系统审计日志事件监听器。
 *
 * <p>监听 Spring 应用内发布的 {@link SysAuditLogEvent} 事件，
 * 将审计信息异步写入 {@code sys_audit_log} 数据库表，实现系统操作的审计追踪。
 *
 * <p>核心设计：写入失败时仅记录警告日志，不影响主业务流程（异常被吞掉），
 * 确保审计日志的写入不会成为业务操作的瓶颈或故障点。
 *
 * <p>与其他模块的关系：其他模块（如 fastrag-knowledge、fastrag-application 等）在执行关键操作时
 * 发布 {@link SysAuditLogEvent}，由本监听器统一收集并持久化。
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
