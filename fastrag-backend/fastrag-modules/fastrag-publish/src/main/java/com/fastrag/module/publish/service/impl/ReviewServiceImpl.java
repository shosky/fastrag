package com.fastrag.module.publish.service.impl;
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
}
