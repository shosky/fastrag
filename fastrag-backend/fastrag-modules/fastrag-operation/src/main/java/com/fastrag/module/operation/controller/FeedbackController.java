package com.fastrag.module.operation.controller;

/**
 * 用户反馈管理控制器。
 *
 * <p>提供用户反馈的完整生命周期管理接口，包括反馈的创建、查询、更新、删除和回复功能，
 * 以及反馈统计和概览数据的获取。
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/feedback - 分页查询用户反馈列表，支持 kbId、feedback、status、page、pageSize 参数</li>
 *     <li>GET /api/feedback/all - 查询所有反馈列表（不分页），支持 kbId 过滤</li>
 *     <li>GET /api/feedback/statistics - 获取反馈统计数据（按知识库）</li>
 *     <li>GET /api/feedback/overview - 获取反馈概览数据</li>
 *     <li>POST /api/feedback - 创建新的用户反馈</li>
 *     <li>PUT /api/feedback/{id} - 更新指定反馈信息</li>
 *     <li>DELETE /api/feedback/{id} - 删除指定反馈</li>
 *     <li>POST /api/feedback/{id}/reply - 对指定反馈进行回复</li>
 * </ul>
 *
 * <p>委托 {@link FeedbackService} 完成具体的业务逻辑处理。
 */
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
    @GetMapping("/overview") public ApiResponse<?> overview(@RequestParam(required=false) String kbId) { return ApiResponse.success(svc.getOverview(kbId)); }
    @PostMapping public ApiResponse<?> create(@RequestBody UserFeedback fb) { svc.create(fb); return ApiResponse.success(); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable Long id,@RequestBody UserFeedback fb) { return ApiResponse.success(svc.update(id,fb)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable Long id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/reply") public ApiResponse<?> reply(@PathVariable Long id,@RequestBody Map<String,String> body) {
        return ApiResponse.success(svc.reply(id,body.get("reply"),body.getOrDefault("operator","system")));
    }
}
