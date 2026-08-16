package com.fastrag.module.knowledge.entity;
/**
 * 知识库合规规则实体类，对应数据库表 kb_compliance_rule。
 *
 * <p>核心职责：
 * 定义知识库级别的合规检查规则，用于在文档入库或知识发布时执行合规校验。
 * 每条规则包含匹配模式（pattern）、触发动作（action）和严重等级（severity），
 * 可按需启用或禁用。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>ruleName — 规则名称</li>
 *   <li>ruleType — 规则类型（如关键词匹配、正则匹配等）</li>
 *   <li>pattern — 匹配模式内容</li>
 *   <li>action — 触发后的处理动作（如阻断、告警、标记等）</li>
 *   <li>severity — 严重等级</li>
 *   <li>enabled — 是否启用（0=禁用，1=启用）</li>
 *   <li>createdBy — 创建者用户 ID</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_compliance_rule") public class KbComplianceRule {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,ruleName,ruleType,pattern,action,severity,createdBy;
    private Integer enabled;
    private LocalDateTime createdAt;
}
