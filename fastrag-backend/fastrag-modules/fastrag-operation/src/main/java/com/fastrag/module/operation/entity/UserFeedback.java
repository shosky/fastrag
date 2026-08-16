package com.fastrag.module.operation.entity;

/**
 * 用户反馈实体类。
 *
 * <p>对应数据库表 user_feedback，记录用户对AI问答结果的反馈评价，
 * 支持点赞/点踩评分、文字评论、分类标签和运营人员回复处理。
 * 反馈按组织（orgId）隔离，便于多组织场景下的反馈管理。
 *
 * <p>主要字段说明：
 * <ul>
 *     <li>id - 自增主键</li>
 *     <li>sessionId - 关联的聊天会话ID</li>
 *     <li>userId - 反馈提交者用户ID</li>
 *     <li>kbId - 关联的知识库ID</li>
 *     <li>appId - 关联的应用ID</li>
 *     <li>query - 原始提问内容</li>
 *     <li>answer - 原始回答内容</li>
 *     <li>feedback - 反馈类型（如 like/dislike）</li>
 *     <li>comment - 用户评论内容</li>
 *     <li>category - 反馈分类</li>
 *     <li>status - 处理状态</li>
 *     <li>reply - 运营回复内容</li>
 *     <li>processedBy - 处理人</li>
 *     <li>orgId - 归属组织ID</li>
 *     <li>score - 评分</li>
 *     <li>createdAt / processedAt - 创建时间 / 处理时间</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("user_feedback") public class UserFeedback {
    @TableId(type=IdType.AUTO) private Long id;
    private String sessionId,userId,kbId,appId,query,answer,feedback,comment,category,status,reply,processedBy;
    private String orgId; // 归属组织（提交者组织，创建时写入）
    private Integer score;
    private LocalDateTime createdAt,processedAt;
}
