package com.fastrag.module.platform.entity;

/**
 * 发布策略实体
 * <p>
 * 对应数据库表 {@code sys_publish_strategy}，定义知识库或文档的发布策略。
 * 策略包含发布类型（strategyType）和具体配置（config JSON），支持按优先级和启用状态管理。
 * 用于控制不同场景下内容的发布行为和审批流程。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code name} — 策略名称</li>
 *   <li>{@code strategyType} — 策略类型（如 manual、auto、scheduled）</li>
 *   <li>{@code config} — 策略配置（JSON格式，包含具体的发布规则参数）</li>
 *   <li>{@code description} — 策略描述</li>
 *   <li>{@code priority} — 执行优先级，数值越大优先级越高</li>
 *   <li>{@code enabled} — 启用状态（1=启用，0=禁用）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.ConfigManageService
 * @see com.fastrag.module.platform.mapper.SysPublishStrategyMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_publish_strategy") public class SysPublishStrategy {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,strategyType,config,description;
    private Integer priority,enabled;
    private LocalDateTime createdAt,updatedAt;
}
