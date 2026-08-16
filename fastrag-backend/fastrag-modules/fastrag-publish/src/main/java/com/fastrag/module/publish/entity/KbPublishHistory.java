package com.fastrag.module.publish.entity;

/**
 * 知识库发布历史实体类。
 *
 * <p>对应数据库表 {@code kb_publish_history}，记录知识库每次发布的详细历史信息，
 * 用于追踪知识库的上线、下线等发布操作记录。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code kbId} - 关联的知识库ID</li>
 *   <li>{@code knowledgeId} - 关联的知识版本ID</li>
 *   <li>{@code publishType} - 发布类型（如 publish）</li>
 *   <li>{@code onlineVersion} - 上线版本号</li>
 *   <li>{@code offlineVersion} - 下线版本号</li>
 *   <li>{@code status} - 发布状态（如 published）</li>
 *   <li>{@code version} - 版本号</li>
 *   <li>{@code scheduledAt} - 计划发布时间</li>
 *   <li>{@code publishedAt} - 实际发布时间</li>
 * </ul>
 *
 * @see com.fastrag.module.publish.mapper.KbPublishHistoryMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_publish_history") public class KbPublishHistory {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,knowledgeId,publishType,onlineVersion,offlineVersion,status,operator;
    private Integer version;
    private LocalDateTime scheduledAt,publishedAt,createdAt;
}
