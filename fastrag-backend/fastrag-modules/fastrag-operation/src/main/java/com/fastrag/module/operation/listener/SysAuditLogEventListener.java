package com.fastrag.module.operation.listener;

import com.fastrag.common.event.SysAuditLogEvent;
import com.fastrag.module.operation.entity.SysAuditLog;
import com.fastrag.module.operation.mapper.SysAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 系统审计日志事件监听器。
 *
 * <p>监听 Spring 应用内发布的 {@link SysAuditLogEvent} 事件，
 * 将审计信息写入 {@code sys_audit_log} 数据库表，实现系统操作的审计追踪。
 *
 * <p>核心设计：
 * <ul>
 *   <li><b>异步化（backlog S5 / gap-review P1-11）</b>：{@code @EventListener + @Async}
 *       组合（{@code @EnableAsync} 已在启动类开启），审计写入在独立线程池执行，
 *       彻底离开发布方关键路径；</li>
 *   <li><b>吞错降级</b>：写入失败不外抛、不影响主业务流程；失败计数累计（{@link AtomicLong}），
 *       每分钟至多汇总一条 warn，避免下游故障时日志风暴；</li>
 *   <li><b>outcome 语义</b>：status 列承载 {@link com.fastrag.module.operation.model.AuditOutcome}
 *       四值（success/failed/rejected/filtered）；事件原值透传，拦截/拒绝类事件由发布方
 *       携带 rejected/filtered（敏感词 checkInput 埋点接线登记后续批次）。</li>
 * </ul>
 *
 * <p>与其他模块的关系：其他模块（如 fastrag-knowledge、fastrag-application 等）在执行关键操作时
 * 发布 {@link SysAuditLogEvent}，由本监听器统一收集并持久化。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysAuditLogEventListener {

    /** 失败汇总 warn 的最小间隔（S5：每分钟至多一条，防日志风暴） */
    static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final SysAuditLogMapper auditLogMapper;

    /** 写失败累计总数（进程生命周期内） */
    private final AtomicLong failureTotal = new AtomicLong();
    /** 距上次汇总 warn 的新增失败数 */
    private final AtomicLong failuresSinceLastWarn = new AtomicLong();
    /** 上次汇总 warn 时间戳（毫秒） */
    private volatile long lastWarnAtMillis = 0L;

    @Async
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
            // S5：status 列语义扩展为 outcome，事件原值透传（发布方携带 rejected/filtered 时原样入库）
            auditLog.setStatus(event.getStatus());
            auditLog.setTimestamp(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            long total = failureTotal.incrementAndGet();
            long sinceLast = failuresSinceLastWarn.incrementAndGet();
            long now = System.currentTimeMillis();
            if (now - lastWarnAtMillis >= WARN_INTERVAL_MILLIS) {
                lastWarnAtMillis = now;
                long reported = failuresSinceLastWarn.getAndSet(0);
                log.warn("[AuditListener] sys_audit_log write failures: {} since last report, {} total, latest: {}",
                        reported, total, e.getMessage());
            }
        }
    }

    /** 写失败累计总数（package 可见，测试观察用）。 */
    long failureCount() {
        return failureTotal.get();
    }
}
