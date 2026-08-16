package com.fastrag.module.knowledge.entity;
/**
 * 知识发布计划实体类，对应数据库表 kb_publish_plan。
 *
 * <p>核心职责：
 * 表示知识库的批量发布计划，支持按策略一次性发布多个知识条目。
 * 记录计划的成功和失败数量、执行状态与详细结果，支持定时执行。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识</li>
 *   <li>kbId — 所属知识库 ID</li>
 *   <li>name — 计划名称</li>
 *   <li>knowledgeIds — 待发布的知识条目 ID 列表</li>
 *   <li>strategy — 发布策略配置</li>
 *   <li>executionStatus — 执行状态（如 pending / running / completed / failed）</li>
 *   <li>executionDetail — 执行结果详情</li>
 *   <li>successCount / failCount — 成功和失败的数量</li>
 *   <li>scheduledTime — 计划执行时间</li>
 *   <li>executedTime — 实际执行时间</li>
 *   <li>createdBy — 创建者用户 ID</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_publish_plan") public class KbPublishPlan {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,knowledgeIds,strategy,executionStatus,executionDetail,createdBy;
    private Integer successCount,failCount;
    private LocalDateTime scheduledTime,executedTime,createdAt;
}
