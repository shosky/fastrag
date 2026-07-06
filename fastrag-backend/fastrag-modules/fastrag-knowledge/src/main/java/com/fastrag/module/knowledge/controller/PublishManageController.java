package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.*;
import com.fastrag.module.knowledge.service.PublishManageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class PublishManageController {
    private final PublishManageService svc;

    // ===== 发布管理 =====
    @GetMapping("/publish/history")
    public ApiResponse<?> history(@PathVariable String kbId, @RequestParam(required = false) String knowledgeId) {
        return ApiResponse.success(svc.listPublishHistory(kbId, knowledgeId));
    }
    @PostMapping("/publish/{knowledgeId}")
    public ApiResponse<?> publish(@PathVariable String kbId, @PathVariable String knowledgeId, @RequestBody KbPublishHistory body) {
        return ApiResponse.success(svc.publish(kbId, knowledgeId, body));
    }
    @PostMapping("/publish/{knowledgeId}/revoke")
    public ApiResponse<?> revoke(@PathVariable String kbId, @PathVariable String knowledgeId) {
        return ApiResponse.success(svc.revoke(kbId, knowledgeId));
    }
    @PostMapping("/publish/plans")
    public ApiResponse<?> createPlan(@PathVariable String kbId, @RequestBody KbPublishPlan plan) {
        plan.setKbId(kbId);
        return ApiResponse.success(svc.createPlan(plan));
    }
    @GetMapping("/publish/plans")
    public ApiResponse<?> plans(@PathVariable String kbId) {
        return ApiResponse.success(svc.listPlans(kbId));
    }
    @GetMapping("/publish/plans/{planId}/execution")
    public ApiResponse<?> planExecution(@PathVariable String planId) {
        return ApiResponse.success(svc.getPlanExecution(planId));
    }
    @GetMapping("/publish/strategy-effect")
    public ApiResponse<?> strategyEffect(@PathVariable String kbId) {
        return ApiResponse.success(svc.getStrategyEffect(kbId));
    }
    // 查看线上/线下版本
    @GetMapping("/publish/online-version")
    public ApiResponse<?> onlineVersion(@PathVariable String kbId, @RequestParam(required = false) String knowledgeId) {
        return ApiResponse.success(svc.getOnlineVersion(kbId, knowledgeId));
    }
    @GetMapping("/publish/offline-version")
    public ApiResponse<?> offlineVersion(@PathVariable String kbId, @RequestParam(required = false) String knowledgeId) {
        return ApiResponse.success(svc.getOfflineVersion(kbId, knowledgeId));
    }
    // 知识重置
    @GetMapping("/reset-configs")
    public ApiResponse<?> resetConfigs(@PathVariable String kbId) {
        return ApiResponse.success(svc.listResetConfigs(kbId));
    }
    @PostMapping("/reset-configs")
    public ApiResponse<?> saveResetConfig(@PathVariable String kbId, @RequestBody KbResetConfig c) {
        c.setKbId(kbId);
        return ApiResponse.success(svc.saveResetConfig(c));
    }
    @PostMapping("/reset/{knowledgeId}")
    public ApiResponse<?> resetKnowledge(@PathVariable String kbId, @PathVariable String knowledgeId) {
        svc.resetKnowledge(kbId, knowledgeId);
        return ApiResponse.success();
    }
    // 更新日志
    @GetMapping("/knowledge-update-logs")
    public ApiResponse<?> updateLogs(@PathVariable String kbId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(svc.getKnowledgeUpdateLogs(kbId, page, pageSize));
    }
}
