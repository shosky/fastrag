package com.fastrag.module.platform.entity;

/**
 * 系统通知实体
 * <p>
 * 对应数据库表 {@code sys_notification}，存储系统发送给用户的通知消息。
 * 支持按目标用户（targetUser）和状态（status）过滤查询。
 * 通知类型（notifyType）和来源信息（sourceType、sourceId）用于追溯通知的产生上下文。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code title} — 通知标题</li>
 *   <li>{@code content} — 通知内容</li>
 *   <li>{@code notifyType} — 通知类型（如 system、warning、info）</li>
 *   <li>{@code sourceType} — 来源类型（如 model_test、config_change）</li>
 *   <li>{@code sourceId} — 来源关联ID</li>
 *   <li>{@code targetUser} — 目标用户ID</li>
 *   <li>{@code status} — 通知状态（unread=未读，read=已读）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.controller.NotificationController
 * @see com.fastrag.module.platform.mapper.SysNotificationMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_notification") public class SysNotification {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String title,content,notifyType,sourceType,sourceId,targetUser,status;
    private LocalDateTime createdAt;
}
