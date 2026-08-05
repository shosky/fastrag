package com.fastrag.module.operation.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一日志查询参数
 */
@Data
public class UnifiedLogQuery {

    /**
     * 日志分类
     * <ul>
     *   <li>audit - 系统审计日志（sys_audit_log）</li>
     *   <li>login - 登录日志（sys_login_log）</li>
     *   <li>operation - 知识库操作日志（kb_log）</li>
     *   <li>update - 知识库变更日志（kb_update_log）</li>
     * </ul>
     */
    private String category;

    /** 关键词（模糊匹配明细/目标字段） */
    private String keyword;

    /** 模块名 */
    private String module;

    /** 操作人 */
    private String operator;

    /** 关联知识库 ID */
    private String kbId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数，默认 20 */
    private Integer pageSize = 20;
}
