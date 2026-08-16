package com.fastrag.module.operation.entity;

/**
 * 数据挖掘任务实体类。
 *
 * <p>对应数据库表 data_mining_task，记录数据挖掘任务的配置和执行状态，
 * 支持按规则类型和规则配置对知识库进行自动化数据挖掘分析。
 *
 * <p>主要字段说明：
 * <ul>
 *     <li>id - 任务唯一标识（ASSIGN_ID 雪花算法）</li>
 *     <li>name - 任务名称</li>
 *     <li>kbId - 关联的知识库ID</li>
 *     <li>ruleType - 挖掘规则类型</li>
 *     <li>ruleConfig - 挖掘规则配置（JSON格式）</li>
 *     <li>status - 任务状态</li>
 *     <li>resultSummary - 执行结果摘要</li>
 *     <li>creator - 任务创建者</li>
 *     <li>lastRunAt - 最近一次执行时间</li>
 *     <li>createdAt / updatedAt - 创建时间 / 更新时间</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("data_mining_task") public class DataMiningTask {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,kbId,ruleType,ruleConfig,status,resultSummary,creator;
    private LocalDateTime lastRunAt,createdAt,updatedAt;
}
