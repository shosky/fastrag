package com.fastrag.module.publish.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.publish.entity.*;
import com.fastrag.module.publish.service.*; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class PublishController {
    private final PublishService publishService; private final ReviewService reviewService;
    @GetMapping("/api/kb/{kbId}/versions") public ApiResponse<?> versions(@PathVariable String kbId) { return ApiResponse.success(publishService.listVersions(kbId)); }
    @GetMapping("/api/kb/{kbId}/versions/latest") public ApiResponse<?> latest(@PathVariable String kbId) { return ApiResponse.success(publishService.getLatestVersion(kbId)); }
    @GetMapping("/api/kb/{kbId}/versions/published") public ApiResponse<?> published(@PathVariable String kbId) { return ApiResponse.success(publishService.getPublishedVersion(kbId)); }
    @PostMapping("/api/kb/{kbId}/versions") public ApiResponse<?> createVersion(@PathVariable String kbId,@RequestBody KbVersion data) { return ApiResponse.success(publishService.createVersion(kbId,data)); }
    @PostMapping("/api/kb/{kbId}/versions/{vid}/transition") public ApiResponse<?> transition(@PathVariable String kbId,@PathVariable String vid,@RequestBody java.util.Map<String,String> b) { 
        String raw=b.getOrDefault("targetStatus",b.get("action"));
        // 动作名 → 状态名映射（前端传动作名，后端存状态名）
        java.util.Map<String,String> actionMap=java.util.Map.of(
            "submit","pending_review","approve","approved","publish","published","reject","rejected","draft","draft"
        );
        String status=actionMap.getOrDefault(raw,raw);
        publishService.transitionStatus(kbId,vid,status); 
        return ApiResponse.success(); 
    }
    @GetMapping("/api/reviews") public ApiResponse<?> reviews(@RequestParam(required=false) String kbId) { return ApiResponse.success(kbId!=null?reviewService.listReviews(kbId):reviewService.getPendingReviews()); }
    @GetMapping("/api/kb/{kbId}/review/{id}") public ApiResponse<?> getReview(@PathVariable String id) { return ApiResponse.success(reviewService.getReview(id)); }
    @PostMapping("/api/reviews") public ApiResponse<?> submit(@RequestBody java.util.Map<String,String> b) { return ApiResponse.success(reviewService.submitForReview(b.get("kbId"),b.get("versionId"),b.get("applicant"))); }
    @PostMapping("/api/reviews/{id}/approve") public ApiResponse<?> approve(@PathVariable String id,@RequestBody(required=false) java.util.Map<String,String> b) { reviewService.approveReview(id,b!=null?b.get("comment"):null); return ApiResponse.success(); }
    @PostMapping("/api/reviews/{id}/reject") public ApiResponse<?> reject(@PathVariable String id,@RequestBody(required=false) java.util.Map<String,String> b) { reviewService.rejectReview(id,b!=null?b.get("comment"):null); return ApiResponse.success(); }
}
