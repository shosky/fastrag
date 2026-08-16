package com.fastrag.module.platform.entity;

/**
 * 模型训练记录实体
 * <p>
 * 对应数据库表 {@code model_training}，记录模型训练任务的执行信息，包括训练数据集、
 * 训练轮次（epochs）、训练耗时、训练指标（metrics JSON）和任务状态。
 * 用于追踪模型的训练历史和效果评估。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code modelId} — 被训练的模型ID，关联 {@link ModelRecord}</li>
 *   <li>{@code modelName} — 模型名称（冗余存储，便于查询展示）</li>
 *   <li>{@code dataset} — 训练使用的数据集标识</li>
 *   <li>{@code status} — 训练状态（如 completed、failed、running）</li>
 *   <li>{@code metrics} — 训练评估指标（JSON格式，如 loss、accuracy 等）</li>
 *   <li>{@code createdBy} — 发起训练的操作人</li>
 *   <li>{@code epochs} — 训练轮次数</li>
 *   <li>{@code duration} — 训练耗时（毫秒）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.mapper.ModelTrainingMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("model_training") public class ModelTraining {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String modelId,modelName,dataset,status,metrics,createdBy;
    private Integer epochs;
    private Long duration;
    private LocalDateTime createdAt;
}
