package com.fastrag.module.retrieval.entity;

/**
 * 知识库检索日志实体类。
 *
 * <p>对应数据库表 {@code kb_retrieval_log}，记录每次知识检索的请求信息和结果统计，
 * 用于检索效果分析和运营监控。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code kbId} - 关联的知识库ID</li>
 *   <li>{@code query} - 用户检索查询文本</li>
 *   <li>{@code userId} - 执行检索的用户ID</li>
 *   <li>{@code hitCount} - 命中结果数量</li>
 *   <li>{@code latencyMs} - 检索耗时（毫秒）</li>
 *   <li>{@code topScore} - 最高相似度分数</li>
 *   <li>{@code graphEntityCount} - 图谱实体命中数</li>
 *   <li>{@code hasResult} - 是否有检索结果</li>
 *   <li>{@code createdAt} - 检索时间</li>
 * </ul>
 *
 * @see com.fastrag.module.retrieval.mapper.KbRetrievalLogMapper
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_retrieval_log") public class KbRetrievalLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String kbId,query,userId;
    private Integer hitCount,latencyMs;
    private Double topScore;
    private Integer graphEntityCount;
    private Boolean hasResult;
    private LocalDateTime createdAt;
}
