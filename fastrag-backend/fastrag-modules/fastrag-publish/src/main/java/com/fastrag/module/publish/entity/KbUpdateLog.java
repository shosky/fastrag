package com.fastrag.module.publish.entity;

/**
 * 知识库更新日志实体类。
 *
 * <p>对应数据库表 {@code kb_update_log}，记录知识库配置、内容等变更的更新日志，
 * 支持记录变更前后的新旧值对比信息。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code kbId} - 关联的知识库ID</li>
 *   <li>{@code updateType} - 更新类型（如策略变更、配置变更等）</li>
 *   <li>{@code target} - 更新目标对象</li>
 *   <li>{@code detail} - 更新详情描述</li>
 *   <li>{@code oldValue} - 变更前的值</li>
 *   <li>{@code newValue} - 变更后的值</li>
 *   <li>{@code operator} - 操作人</li>
 *   <li>{@code timestamp} - 更新时间</li>
 * </ul>
 *
 * @see com.fastrag.module.publish.mapper.KbUpdateLogMapper
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_update_log")
public class KbUpdateLog {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, updateType, target, detail, oldValue, newValue, operator;
    private LocalDateTime timestamp;
}
