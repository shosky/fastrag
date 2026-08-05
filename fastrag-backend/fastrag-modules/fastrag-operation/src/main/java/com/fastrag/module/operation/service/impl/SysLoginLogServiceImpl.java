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
