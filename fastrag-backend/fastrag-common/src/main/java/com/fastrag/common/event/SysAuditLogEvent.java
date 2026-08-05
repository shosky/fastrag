package com.fastrag.common.event;

import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 系统审计日志事件
 * <p>由 {@code KbLogAspect} 发布，由 {@code SysAuditLogEventListener} 消费写入 {@code sys_audit_log} 表。
 * <p>事件驱动避免模块间循环依赖（publish → operation → publish）。
 */
@Getter
public class SysAuditLogEvent extends ApplicationEvent {

    /** 用户 ID */
    private final String userId;

    /** 用户名 */
    private final String username;

    /** 模块名（从 LogCategory 映射） */
    private final String module;

    /** 操作动作 */
    private final ActionType action;

    /** 操作目标 */
    private final String target;

    /** 操作详情 */
    private final String detail;

    /** 操作状态：success / failed */
    private final String status;

    /** 客户端 IP */
    private final String ip;

    public SysAuditLogEvent(Object source, String userId, String username, String module,
                            ActionType action, String target, String detail,
                            String status, String ip) {
        super(source);
        this.userId = userId;
        this.username = username;
        this.module = module;
        this.action = action;
        this.target = target;
        this.detail = detail;
        this.status = status;
        this.ip = ip;
    }
}
