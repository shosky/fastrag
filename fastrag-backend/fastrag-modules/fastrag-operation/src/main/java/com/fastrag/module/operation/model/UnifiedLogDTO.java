package com.fastrag.module.operation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一日志响应 DTO
 * <p>将所有日志类型映射为统一格式，方便前端展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedLogDTO {

    /** 日志 ID */
    private String id;

    /** 日志分类：audit / login / operation / update */
    private String category;

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 模块名 */
    private String module;

    /** 操作动作 */
    private String action;

    /** 操作目标 */
    private String target;

    /** 操作详情 */
    private String detail;

    /** 操作状态：success / failed */
    private String status;

    /** 客户端 IP */
    private String ip;

    /** 操作时间 */
    private LocalDateTime timestamp;
}
