package com.fastrag.module.knowledge.entity;
/**
 * 知识库监听器执行日志实体类，对应数据库表 kb_listener_log。
 *
 * <p>核心职责：
 * 记录知识库监听器每次触发的执行日志，包括事件类型、消息内容、执行状态等，
 * 用于监听器的运行审计和故障排查。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 自增主键（AUTO 类型）</li>
 *   <li>listenerId — 关联的监听器 ID</li>
 *   <li>eventType — 触发的事件类型</li>
 *   <li>message — 执行消息内容</li>
 *   <li>status — 执行状态（如 success / failed）</li>
 *   <li>level — 日志级别（非数据库字段，仅用于业务逻辑传递）</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_listener_log") public class KbListenerLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String listenerId,eventType,message,status;
    @TableField(exist = false) private String level;
    private LocalDateTime createdAt;
}
