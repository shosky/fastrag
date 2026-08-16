package com.fastrag.module.platform.entity;

/**
 * 系统配置实体
 * <p>
 * 对应数据库表 {@code sys_config}，存储平台级系统配置项。配置值以字符串形式存储（通常为JSON），
 * 支持按 configType 分类管理（如 publish、review、doc_guide 等）。
 * 配置变更时会自动在 {@link SysConfigHistory} 中记录变更历史。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code configKey} — 配置键（唯一标识，如 publish_switch、review_flow）</li>
 *   <li>{@code configValue} — 配置值（通常为JSON字符串）</li>
 *   <li>{@code configType} — 配置类型分组（如 publish、review、model）</li>
 *   <li>{@code description} — 配置描述说明</li>
 *   <li>{@code updatedBy} — 最后更新人</li>
 *   <li>{@code isDefault} — 是否为默认配置（1=是，0=否）</li>
 *   <li>{@code isSystem} — 是否为系统内置配置（1=是，不可删除；0=否）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.ConfigManageService
 * @see com.fastrag.module.platform.entity.SysConfigHistory
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_config") public class SysConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String configKey,configValue,configType,description,updatedBy;
    private Integer isDefault,isSystem;
    private LocalDateTime createdAt,updatedAt;
}
