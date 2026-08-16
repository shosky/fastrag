package com.fastrag.module.operation.entity;

/**
 * 系统审计日志实体类。
 *
 * <p>对应数据库表 sys_audit_log，记录系统中所有需要审计的用户操作日志，
 * 包括操作用户、操作模块、操作动作、操作目标、详细信息、IP地址和操作状态等。
 * 通过 {@link com.fastrag.module.operation.listener.SysAuditLogEventListener} 监听事件自动写入。
 *
 * <p>主要字段说明：
 * <ul>
 *     <li>id - 自增主键</li>
 *     <li>userId - 操作用户ID</li>
 *     <li>username - 操作用户名</li>
 *     <li>module - 操作模块（如 knowledge、application 等）</li>
 *     <li>action - 操作动作（如 create、update、delete 等）</li>
 *     <li>target - 操作目标（如知识库名称、文档名称等）</li>
 *     <li>detail - 操作详细信息</li>
 *     <li>ip - 操作来源IP地址</li>
 *     <li>status - 操作状态</li>
 *     <li>timestamp - 操作时间</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_audit_log") public class SysAuditLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String userId,username,module,action,target,detail,ip,status;
    private LocalDateTime timestamp;
}
