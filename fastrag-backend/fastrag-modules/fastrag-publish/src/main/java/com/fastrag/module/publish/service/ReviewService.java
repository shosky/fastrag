package com.fastrag.module.publish.service;

/**
 * 知识库版本审核服务接口。
 *
 * <p>定义知识库版本发布前的审核流程，包括审核任务的提交、审批通过和驳回。
 * 审核任务状态包括 pending（待审核）、approved（已通过）、rejected（已驳回）。</p>
 *
 * @see KbReviewTask
 */
import com.fastrag.module.publish.entity.KbReviewTask; import java.util.*;
public interface ReviewService { List<KbReviewTask> listReviews(String kbId); List<KbReviewTask> getPendingReviews(); KbReviewTask submitForReview(String kbId,String versionId,String applicant); void approveReview(String reviewId,String comment); void rejectReview(String reviewId,String comment); KbReviewTask getReview(String id); }
