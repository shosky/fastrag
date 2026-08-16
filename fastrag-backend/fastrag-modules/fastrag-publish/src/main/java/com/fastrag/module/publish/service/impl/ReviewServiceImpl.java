package com.fastrag.module.publish.service.impl;

/**
 * 知识库版本审核服务实现。
 *
 * <p>实现 {@link ReviewService} 接口，提供知识库版本发布前的审核流程管理。</p>
 *
 * <h3>核心实现逻辑：</h3>
 * <ul>
 *   <li>审核列表：可按知识库ID过滤，按创建时间倒序排列</li>
 *   <li>待审核查询：查询 status="pending" 的所有审核任务</li>
 *   <li>提交审核：创建审核任务，初始状态为 pending</li>
 *   <li>审批通过/驳回：更新审核任务状态为 approved 或 rejected，记录审核意见和审核时间</li>
 * </ul>
 *
 * @see ReviewService
 * @see KbReviewTaskMapper
 */
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.publish.entity.KbReviewTask; import com.fastrag.module.publish.mapper.KbReviewTaskMapper;
import com.fastrag.module.publish.service.ReviewService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final KbReviewTaskMapper mapper;
    @Override public List<KbReviewTask> listReviews(String kbId) { return mapper.selectList(new LambdaQueryWrapper<KbReviewTask>().eq(kbId!=null,KbReviewTask::getKbId,kbId).orderByDesc(KbReviewTask::getCreatedAt)); }
    @Override public List<KbReviewTask> getPendingReviews() { return mapper.selectList(new LambdaQueryWrapper<KbReviewTask>().eq(KbReviewTask::getStatus,"pending")); }
    @Override public KbReviewTask submitForReview(String kbId,String versionId,String applicant) { var r=new KbReviewTask(); r.setKbId(kbId); r.setVersionId(versionId); r.setApplicant(applicant); r.setStatus("pending"); r.setCreatedAt(LocalDateTime.now()); mapper.insert(r); return r; }
    @Override public void approveReview(String reviewId,String comment) { var r=mapper.selectById(reviewId); if(r!=null){r.setStatus("approved");r.setComment(comment);r.setReviewedAt(LocalDateTime.now());mapper.updateById(r);} }
    @Override public void rejectReview(String reviewId,String comment) { var r=mapper.selectById(reviewId); if(r!=null){r.setStatus("rejected");r.setComment(comment);r.setReviewedAt(LocalDateTime.now());mapper.updateById(r);} }
    @Override public KbReviewTask getReview(String id) { return mapper.selectById(id); }
}
