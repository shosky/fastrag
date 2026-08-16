package com.fastrag.module.operation.mapper;

/**
 * 系统审计日志 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，提供对 {@link com.fastrag.module.operation.entity.SysAuditLog}
 * 实体（sys_audit_log 表）的基础 CRUD 操作。由 {@link com.fastrag.module.operation.listener.SysAuditLogEventListener}
 * 监听事件后调用 insert 写入审计日志，以及 {@link com.fastrag.module.operation.service.AuditLogService} 查询使用。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fastrag.module.operation.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {}
