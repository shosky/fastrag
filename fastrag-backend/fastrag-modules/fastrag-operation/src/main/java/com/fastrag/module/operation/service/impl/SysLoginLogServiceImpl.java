package com.fastrag.module.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.operation.entity.SysLoginLog;
import com.fastrag.module.operation.mapper.SysLoginLogMapper;
import com.fastrag.module.operation.service.SysLoginLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统登录日志服务实现类。
 *
 * <p>负责用户登录日志的查询和写入操作。登录日志记录用户每次登录的时间、IP、
 * 设备信息、操作系统、浏览器和登录结果，用于安全审计和异常登录检测。
 * 支持按用户ID和登录状态过滤查询。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>addLoginLog - 写入登录日志记录，包含用户ID、用户名、IP、设备、操作系统、
 *       浏览器、登录状态和失败原因；写入失败仅记录warn日志，不阻断登录流程</li>
 *   <li>list - 按用户ID和登录状态过滤查询登录日志，支持自定义limit（默认100条），按时间倒序排列</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysLoginLogServiceImpl implements SysLoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    @Override
    public void addLoginLog(String userId, String username, String ip, String device,
                            String os, String browser, String status, String failReason) {
        try {
            SysLoginLog loginLog = new SysLoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setIp(ip);
            loginLog.setDevice(device);
            loginLog.setOs(os);
            loginLog.setBrowser(browser);
            loginLog.setStatus(status != null ? status : "success");
            loginLog.setFailReason(failReason);
            loginLog.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.warn("[LoginLogService] Failed to add login log: {}", e.getMessage());
        }
    }

    @Override
    public List<SysLoginLog> list(String userId, String status, Integer limit) {
        var w = new LambdaQueryWrapper<SysLoginLog>();
        if (userId != null) {
            w.eq(SysLoginLog::getUserId, userId);
        }
        if (status != null) {
            w.eq(SysLoginLog::getStatus, status);
        }
        w.orderByDesc(SysLoginLog::getLoginTime)
                .last(limit != null ? "LIMIT " + limit : "LIMIT 100");
        return loginLogMapper.selectList(w);
    }
}
