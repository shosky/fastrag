package com.fastrag.module.platform.entity;

/**
 * 模型测试报告实体
 * <p>
 * 对应数据库表 {@code model_test_report}，记录模型测试的执行结果，包括测试数据集、
 * 测试评分、详细指标（metrics JSON）、测试样本数量和整体状态。
 * 用于追踪模型质量评估历史。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code modelId} — 被测试的模型ID，关联 {@link ModelRecord}</li>
 *   <li>{@code modelName} — 模型名称（冗余存储，便于查询展示）</li>
 *   <li>{@code testDataset} — 测试使用的数据集标识</li>
 *   <li>{@code score} — 测试综合评分</li>
 *   <li>{@code metrics} — 详细测试指标（JSON格式）</li>
 *   <li>{@code testCount} — 测试样本数量</li>
 *   <li>{@code status} — 测试状态（如 completed、failed、running）</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.mapper.ModelTestReportMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("model_test_report") public class ModelTestReport {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String modelId,modelName,testDataset,score,metrics,status;
    private Integer testCount;
    private LocalDateTime createdAt;
}
