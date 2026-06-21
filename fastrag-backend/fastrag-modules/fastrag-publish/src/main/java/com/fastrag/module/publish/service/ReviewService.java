package com.fastrag.module.publish.service;
import com.fastrag.module.publish.entity.KbReviewTask; import java.util.*;
public interface ReviewService { List<KbReviewTask> listReviews(String kbId); List<KbReviewTask> getPendingReviews(); KbReviewTask submitForReview(String kbId,String versionId,String applicant); void approveReview(String reviewId,String comment); void rejectReview(String reviewId,String comment); }
