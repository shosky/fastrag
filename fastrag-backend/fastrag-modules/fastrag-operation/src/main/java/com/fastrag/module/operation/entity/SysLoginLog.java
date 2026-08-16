package com.fastrag.module.operation.entity;

/**
 * 系统登录日志实体类。
 *
 * <p>对应数据库表 sys_login_log，记录所有用户的登录、登出行为，
 * 包括登录时间、用户信息、设备环境（操作系统、浏览器）、IP地址、地理位置和登录结果等，
 * 用于安全审计和异常登录行为分析。
 *
 * <p>主要字段说明：
 * <ul>
 *     <li>id - 自增主键</li>
 *     <li>userId - 登录用户ID</li>
 *     <li>username - 登录用户名</li>
 *     <li>ip - 登录来源IP地址</li>
 *     <li>device - 设备信息</li>
 *     <li>os - 操作系统</li>
 *     <li>browser - 浏览器类型</li>
 *     <li>location - 地理位置</li>
 *     <li>status - 登录状态（success/failed/logout）</li>
 *     <li>failReason - 登录失败原因</li>
 *     <li>loginTime - 登录时间</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_login_log") public class SysLoginLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String userId,username,ip,device,os,browser,location,status,failReason;
    private LocalDateTime loginTime;
}
