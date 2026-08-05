package com.fastrag.module.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.module.operation.entity.SysAuditLog;
import com.fastrag.module.operation.entity.SysLoginLog;
import com.fastrag.module.operation.mapper.SysAuditLogMapper;
import com.fastrag.module.operation.mapper.SysLoginLogMapper;
import com.fastrag.module.operation.model.UnifiedLogDTO;
import com.fastrag.module.operation.model.UnifiedLogQuery;
import com.fastrag.module.operation.service.UnifiedLogService;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.mapper.KbLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一日志查询服务实现
 * <p>根据 category 路由到不同的日志表，返回统一格式的分页结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedLogServiceImpl implements UnifiedLogService {

    private final SysAuditLogMapper sysAuditLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final KbLogMapper kbLogMapper;

    @Override
    public IPage<UnifiedLogDTO> pageQuery(UnifiedLogQuery query) {
        if (query.getCategory() == null) {
            return Page.of(0, query.getPageSize());
        }
        return switch (query.getCategory()) {
            case "audit" -> queryAuditLog(query);
            case "login" -> queryLoginLog(query);
            case "operation" -> queryKbLog(query);
            default -> Page.of(0, query.getPageSize());
        };
    }

    /**
     * 查询系统审计日志（sys_audit_log）
     */
    private IPage<UnifiedLogDTO> queryAuditLog(UnifiedLogQuery query) {
        var wrapper = new LambdaQueryWrapper<SysAuditLog>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(SysAuditLog::getDetail, query.getKeyword())
                    .or().like(SysAuditLog::getTarget, query.getKeyword());
        }
        if (query.getModule() != null) {
            wrapper.eq(SysAuditLog::getModule, query.getModule());
        }
        if (query.getOperator() != null) {
            wrapper.eq(SysAuditLog::getUsername, query.getOperator());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(SysAuditLog::getTimestamp, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(SysAuditLog::getTimestamp, query.getEndTime());
        }
        wrapper.orderByDesc(SysAuditLog::getTimestamp);

        IPage<SysAuditLog> pageResult = sysAuditLogMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return pageResult.convert(this::toUnifiedDTO);
    }

    /**
     * 查询登录日志（sys_login_log）
     */
    private IPage<UnifiedLogDTO> queryLoginLog(UnifiedLogQuery query) {
        var wrapper = new LambdaQueryWrapper<SysLoginLog>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(SysLoginLog::getUsername, query.getKeyword());
        }
        if (query.getOperator() != null) {
            wrapper.eq(SysLoginLog::getUsername, query.getOperator());
        }
        wrapper.orderByDesc(SysLoginLog::getLoginTime);

        IPage<SysLoginLog> pageResult = sysLoginLogMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return pageResult.convert(this::toUnifiedDTO);
    }

    /**
     * 查询知识库操作日志（kb_log）
     */
    private IPage<UnifiedLogDTO> queryKbLog(UnifiedLogQuery query) {
        var wrapper = new LambdaQueryWrapper<KbLog>();
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            wrapper.like(KbLog::getDetail, query.getKeyword())
                    .or().like(KbLog::getTarget, query.getKeyword());
        }
        if (query.getModule() != null) {
            wrapper.eq(KbLog::getCategory, query.getModule());
        }
        if (query.getOperator() != null) {
            wrapper.eq(KbLog::getOperator, query.getOperator());
        }
        if (query.getKbId() != null) {
            wrapper.eq(KbLog::getKbId, query.getKbId());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(KbLog::getTimestamp, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(KbLog::getTimestamp, query.getEndTime());
        }
        wrapper.orderByDesc(KbLog::getTimestamp);

        IPage<KbLog> pageResult = kbLogMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return pageResult.convert(this::toUnifiedDTO);
    }

    // ==================== 转换方法 ====================

    private UnifiedLogDTO toUnifiedDTO(SysAuditLog log) {
        return UnifiedLogDTO.builder()
                .id(String.valueOf(log.getId()))
                .category("audit")
                .userId(log.getUserId())
                .username(log.getUsername())
                .module(log.getModule())
                .action(log.getAction())
                .target(log.getTarget())
                .detail(log.getDetail())
                .ip(log.getIp())
                .timestamp(log.getTimestamp())
                .build();
    }

    private UnifiedLogDTO toUnifiedDTO(SysLoginLog log) {
        return UnifiedLogDTO.builder()
                .id(String.valueOf(log.getId()))
                .category("login")
                .userId(log.getUserId())
                .username(log.getUsername())
                .module("登录认证")
                .action(log.getStatus())
                .detail(log.getFailReason())
                .ip(log.getIp())
                .timestamp(log.getLoginTime())
                .build();
    }

    private UnifiedLogDTO toUnifiedDTO(KbLog log) {
        return UnifiedLogDTO.builder()
                .id(log.getId())
                .category("operation")
                .username(log.getOperator())
                .module(log.getCategory())
                .action(log.getAction())
                .target(log.getTarget())
                .detail(log.getDetail())
                .status(log.getStatus())
                .timestamp(log.getTimestamp())
                .build();
    }
}
