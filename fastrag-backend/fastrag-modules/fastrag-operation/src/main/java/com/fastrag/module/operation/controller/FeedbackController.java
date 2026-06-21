package com.fastrag.module.operation.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.operation.entity.UserFeedback;
import com.fastrag.module.operation.service.FeedbackService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/feedback") @RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @PostMapping public ApiResponse<?> create(@RequestBody UserFeedback fb) { svc.create(fb); return ApiResponse.success(); }
}
