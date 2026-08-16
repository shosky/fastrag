package com.fastrag.module.publish.entity;

/**
 * 知识库审核任务实体类。
 *
 * <p>对应数据库表 {@code kb_review_task}，记录知识库版本发布前的审核流程。
 * 审核任务由申请人（applicant）创建，审核人（reviewer）进行审批或驳回操作。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code kbId} - 关联的知识库ID</li>
 *   <li>{@code kbName} - 知识库名称</li>
 *   <li>{@code versionId} - 待审核的版本ID</li>
 *   <li>{@code applicant} - 审核申请人</li>
 *   <li>{@code reviewer} - 审核人</li>
 *   <li>{@code status} - 审核状态（pending / approved / rejected）</li>
 *   <li>{@code comment} - 审核意见</li>
 *   <li>{@code version} - 版本号</li>
 * </ul>
 *
 * @see com.fastrag.module.publish.mapper.KbReviewTaskMapper
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("kb_review_task")
public class KbReviewTask {
    @TableId(type = IdType.ASSIGN_ID) private String id;
    private String kbId, kbName, versionId, applicant, reviewer, status, comment;
    private Integer version;
    private LocalDateTime createdAt, reviewedAt;
}
