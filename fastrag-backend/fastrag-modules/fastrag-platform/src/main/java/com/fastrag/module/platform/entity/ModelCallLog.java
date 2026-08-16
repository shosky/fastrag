package com.fastrag.module.platform.entity;

/**
 * 模型调用日志实体
 * <p>
 * 对应数据库表 {@code model_call_log}，记录每次AI模型的调用详情，包括调用的模型标识、
 * 调用方、调用状态、消耗的 Token 数、响应耗时等信息，用于模型使用统计与监控。
 * </p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code modelId} — 调用的模型ID，关联 {@link ModelRecord}</li>
 *   <li>{@code caller} — 调用方标识</li>
 *   <li>{@code status} — 调用状态（如 success、failed）</li>
 *   <li>{@code orgId} — 归属组织（调用者所属组织）</li>
 *   <li>{@code duration} — 调用耗时（毫秒）</li>
 *   <li>{@code tokens} — 消耗的 Token 数量</li>
 *   <li>{@code timestamp} — 调用时间戳</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.mapper.ModelCallLogMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("model_call_log") public class ModelCallLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String modelId,caller,status;
    private String orgId; // 归属组织（调用者组织）
    private Integer duration,tokens;
    private LocalDateTime timestamp;
}
