package com.fastrag.module.operation.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.operation.entity.UserFeedback;
import com.fastrag.module.operation.service.FeedbackService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/feedback") @RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String kbId,@RequestParam(required=false) String feedback,
                                          @RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(svc.page(kbId,feedback,status,page,pageSize));
    }
    @GetMapping("/all") public ApiResponse<?> all(@RequestParam(required=false) String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @GetMapping("/statistics") public ApiResponse<?> statistics(@RequestParam(required=false) String kbId) { return ApiResponse.success(svc.statistics(kbId)); }
    @PostMapping public ApiResponse<?> create(@RequestBody UserFeedback fb) { svc.create(fb); return ApiResponse.success(); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable Long id,@RequestBody UserFeedback fb) { return ApiResponse.success(svc.update(id,fb)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable Long id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/reply") public ApiResponse<?> reply(@PathVariable Long id,@RequestBody Map<String,String> body) {
        return ApiResponse.success(svc.reply(id,body.get("reply"),body.getOrDefault("operator","system")));
    }
}
