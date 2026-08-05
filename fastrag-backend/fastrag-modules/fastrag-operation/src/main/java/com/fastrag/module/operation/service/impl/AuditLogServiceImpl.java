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
