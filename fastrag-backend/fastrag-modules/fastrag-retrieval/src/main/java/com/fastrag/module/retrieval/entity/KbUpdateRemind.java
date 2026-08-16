package com.fastrag.module.retrieval.entity;

/**
 * 知识库更新提醒配置实体类。
 *
 * <p>对应数据库表 {@code kb_update_remind}，管理知识库的定时更新提醒配置，
 * 支持按 cron 表达式定时发送更新通知。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code kbId} - 关联的知识库ID</li>
 *   <li>{@code enabled} - 是否启用提醒（1=启用，0=禁用）</li>
 *   <li>{@code cronExpr} - 定时提醒的 cron 表达式，默认 "0 9 * * *"（每天9点）</li>
 *   <li>{@code channels} - 提醒渠道配置</li>
 *   <li>{@code lastRemindAt} - 上次提醒时间</li>
 * </ul>
 *
 * @see com.fastrag.module.retrieval.mapper.KbUpdateRemindMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_update_remind") public class KbUpdateRemind {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId;
    private Integer enabled;
    private String cronExpr;
    private String channels;
    private LocalDateTime lastRemindAt,createdAt,updatedAt;
}
