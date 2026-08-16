package com.fastrag.module.knowledge.entity;
/**
 * 知识库质量规则实体类，对应数据库表 kb_quality_rule。
 *
 * <p>核心职责：
 * 定义知识库级别的质量评估规则，用于对知识内容进行质量度量。
 * 每条规则指定评估指标（metric）、阈值（threshold）和权重（weight），
 * 可按需启用或禁用，支持多个规则组合进行综合质量评分。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>ruleName — 规则名称</li>
 *   <li>metric — 评估指标名称</li>
 *   <li>threshold — 质量阈值</li>
 *   <li>weight — 规则在综合评分中的权重</li>
 *   <li>enabled — 是否启用（0=禁用，1=启用）</li>
 *   <li>createdBy — 创建者用户 ID</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_quality_rule") public class KbQualityRule {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,ruleName,metric,createdBy;
    private Integer enabled;
    private Double threshold,weight;
    private LocalDateTime createdAt;
}
