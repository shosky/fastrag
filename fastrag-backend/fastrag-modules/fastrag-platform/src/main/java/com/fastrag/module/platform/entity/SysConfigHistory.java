package com.fastrag.module.platform.entity;

/**
 * 系统配置变更历史实体
 * <p>
 * 对应数据库表 {@code sys_config_history}，记录系统配置的每次变更操作，包括变更前后的值、
 * 变更类型和操作人。用于配置审计和回溯追踪。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code configId} — 关联的配置项ID（{@link SysConfig#getId()}）</li>
 *   <li>{@code configKey} — 配置键（冗余存储，便于快速查询）</li>
 *   <li>{@code oldValue} — 变更前的配置值</li>
 *   <li>{@code newValue} — 变更后的配置值</li>
 *   <li>{@code changeType} — 变更类型（如 update、reset、import）</li>
 *   <li>{@code operator} — 执行变更的操作人</li>
 *   <li>{@code timestamp} — 变更时间戳</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.entity.SysConfig
 * @see com.fastrag.module.platform.mapper.SysConfigHistoryMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_config_history") public class SysConfigHistory {
    @TableId(type=IdType.AUTO) private Long id;
    private String configId,configKey,oldValue,newValue,changeType,operator;
    private LocalDateTime timestamp;
}
