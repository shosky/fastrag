package com.fastrag.module.operation.service;

import com.fastrag.module.operation.entity.SysLoginLog;

import java.util.List;

/**
 * 系统登录日志服务
 */
public interface SysLoginLogService {

    /**
     * 记录登录日志
     */
    void addLoginLog(String userId, String username, String ip, String device,
                     String os, String browser, String status, String failReason);

    /**
     * 查询登录日志
     *
     * @param userId 用户 ID（可选）
     * @param status 状态：success / failed / logout（可选）
     * @param limit  返回条数上限，默认 100
     */
    List<SysLoginLog> list(String userId, String status, Integer limit);
}
