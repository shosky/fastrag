package com.fastrag.module.platform.entity;

/**
 * 查询规则实体
 * <p>
 * 对应数据库表 {@code query_rule}，定义系统查询行为的规则配置。
 * 每条规则包含匹配模式（pattern）和对应的执行动作（action），支持按类型（ruleType）分类，
 * 并通过 priority 和 enabled 字段控制规则的执行优先级和启用状态。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code name} — 规则名称</li>
 *   <li>{@code description} — 规则描述</li>
 *   <li>{@code ruleType} — 规则类型（如 rewrite、filter、expand 等）</li>
 *   <li>{@code pattern} — 匹配模式（正则表达式或关键词）</li>
 *   <li>{@code action} — 匹配后执行的动作（JSON配置）</li>
 *   <li>{@code enabled} — 启用状态（1=启用，0=禁用）</li>
 *   <li>{@code priority} — 执行优先级，数值越大优先级越高</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.QueryRuleService
 * @see com.fastrag.module.platform.mapper.QueryRuleMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("query_rule") public class QueryRule {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,ruleType,pattern,action;
    private Integer enabled,priority;
    private LocalDateTime createdAt;
}
