package com.fastrag.module.knowledge.entity;
/**
 * 知识审核策略实体类，对应数据库表 kb_review_strategy。
 *
 * <p>核心职责：
 * 定义知识库级别的审核策略，指定审核策略类型和配置，关联审核模板。
 * 每个知识库可配置多个审核策略，按需启用或禁用，用于知识发布前的内容审核。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>name — 策略名称</li>
 *   <li>strategyType — 策略类型</li>
 *   <li>config — 策略配置参数，JSON 格式</li>
 *   <li>enabled — 是否启用（0=禁用，1=启用）</li>
 *   <li>createdBy — 创建者用户 ID</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_review_strategy") public class KbReviewStrategy {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,strategyType,config,createdBy;
    private Integer enabled;
    private LocalDateTime createdAt;
}
