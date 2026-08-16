package com.fastrag.module.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.operation.entity.SysAuditLog;
import com.fastrag.module.operation.mapper.SysAuditLogMapper;
import com.fastrag.module.operation.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统审计日志服务实现类。
 *
 * <p>负责系统审计日志的查询和写入操作。审计日志记录用户在系统中执行的关键操作，
 * 用于安全审计和操作追溯。支持按模块过滤查询，默认返回最近100条记录。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>list - 按模块过滤查询审计日志，支持自定义limit，按时间倒序排列</li>
 *   <li>addLog - 写入审计日志记录，包含操作用户、模块、操作类型、目标、详情、IP、状态等信息；
 *       写入失败仅记录warn日志，不阻断业务流程</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogMapper mapper;

    @Override
    public List<SysAuditLog> list(String module, Integer limit) {
        var w = new LambdaQueryWrapper<SysAuditLog>();
        if (module != null) {
            w.eq(SysAuditLog::getModule, module);
        }
        w.orderByDesc(SysAuditLog::getTimestamp)
                .last(limit != null ? "LIMIT " + limit : "LIMIT 100");
        return mapper.selectList(w);
    }

    @Override
    public void addLog(String userId, String username, String module, String action,
                       String target, String detail, String ip, String status) {
        try {
            SysAuditLog auditLog = new SysAuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setModule(module);
            auditLog.setAction(action);
            auditLog.setTarget(target);
            auditLog.setDetail(detail);
            auditLog.setIp(ip);
            auditLog.setTimestamp(LocalDateTime.now());
            mapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("[AuditLogService] Failed to add audit log: {}", e.getMessage());
        }
    }
}
