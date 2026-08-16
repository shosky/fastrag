package com.fastrag.module.platform.entity;

/**
 * 安全策略实体
 * <p>
 * 对应数据库表 {@code sys_security_policy}，定义平台内容安全防护策略。
 * 每条策略包含匹配模式（pattern）和执行动作（action），支持按策略类型（policyType）分类，
 * 用于管控用户输入、查询内容和模型输出中的安全风险。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code name} — 策略名称</li>
 *   <li>{@code policyType} — 策略类型（如 input_filter、output_filter、content_security）</li>
 *   <li>{@code pattern} — 匹配模式（正则表达式或关键词规则）</li>
 *   <li>{@code action} — 匹配后的执行动作（如 block、replace、log）</li>
 *   <li>{@code description} — 策略描述</li>
 *   <li>{@code priority} — 执行优先级，数值越大优先级越高</li>
 *   <li>{@code enabled} — 启用状态（1=启用，0=禁用）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.ConfigManageService
 * @see com.fastrag.module.platform.mapper.SysSecurityPolicyMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_security_policy") public class SysSecurityPolicy {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,policyType,pattern,action,description;
    private Integer priority,enabled;
    private LocalDateTime createdAt,updatedAt;
}
