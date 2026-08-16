package com.fastrag.module.knowledge.controller;
/**
 * 发布管理控制器，提供知识发布、撤销、发布计划及知识重置的 REST API。
 *
 * <p>核心职责：
 * 管理知识库中知识的发布生命周期，包括发布知识到线上、撤销已发布知识、
 * 创建和查询发布计划、查看策略效果、对比线上线下版本差异，以及知识重置
 * 配置和执行。发布和重置等关键操作均记录审计日志。
 *
 * <p>REST 端点（基础路径 /api/kb/{kbId}）：
 * <ul>
 *   <li>GET    /publish/history                    — 查询发布历史记录，可按 knowledgeId 筛选（viewer）</li>
 *   <li>POST   /publish/{knowledgeId}               — 发布指定知识（editor）</li>
 *   <li>POST   /publish/{knowledgeId}/revoke         — 撤销已发布知识（editor）</li>
 *   <li>POST   /publish/plans                       — 创建发布计划（editor）</li>
 *   <li>GET    /publish/plans                       — 查询发布计划列表（viewer）</li>
 *   <li>GET    /publish/plans/{planId}/execution    — 查询发布计划执行详情（viewer）</li>
 *   <li>GET    /publish/strategy-effect             — 查询解析策略效果分析（viewer）</li>
 *   <li>GET    /publish/online-version              — 查看线上版本配置，可按 knowledgeId 筛选（viewer）</li>
 *   <li>GET    /publish/offline-version             — 查看线下版本配置，可按 knowledgeId 筛选（viewer）</li>
 *   <li>GET    /reset-configs                       — 查询知识重置配置列表（viewer）</li>
 *   <li>POST   /reset-configs                       — 保存知识重置配置（editor）</li>
 *   <li>POST   /reset/{knowledgeId}                 — 执行知识重置（editor）</li>
 *   <li>GET    /knowledge-update-logs               — 分页查询知识更新日志（viewer）</li>
 * </ul>
 *
 * <p>依赖服务：PublishManageService（发布业务逻辑）、LogService（操作审计日志）。
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.module.knowledge.entity.*;
import com.fastrag.module.knowledge.service.PublishManageService;
import com.fastrag.module.publish.service.LogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class PublishManageController {
    private final PublishManageService svc;
    private final LogService logService;

    // ===== 发布管理 =====
    @KbAuth(KBRole.viewer)
    @GetMapping("/publish/history")
    public ApiResponse<?> history(@PathVariable String kbId, @RequestParam(required = false) String knowledgeId) {
        return ApiResponse.success(svc.listPublishHistory(kbId, knowledgeId));
    }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.publish, action = ActionType.publish_published, target = "#knowledgeId", detail = "发布知识")
    @PostMapping("/publish/{knowledgeId}")
    public ApiResponse<?> publish(@PathVariable String kbId, @PathVariable String knowledgeId, @RequestBody KbPublishHistory body) {
        return ApiResponse.success(svc.publish(kbId, knowledgeId, body));
    }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.publish, action = ActionType.publish_revoked, target = "#knowledgeId", detail = "撤销发布")
    @PostMapping("/publish/{knowledgeId}/revoke")
    public ApiResponse<?> revoke(@PathVariable String kbId, @PathVariable String knowledgeId) {
        return ApiResponse.success(svc.revoke(kbId, knowledgeId));
    }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.publish, action = ActionType.publish_plan_created, detail = "创建发布计划")
    @PostMapping("/publish/plans")
    public ApiResponse<?> createPlan(@PathVariable String kbId, @RequestBody KbPublishPlan plan) {
        plan.setKbId(kbId);
        return ApiResponse.success(svc.createPlan(plan));
    }
    @KbAuth(KBRole.viewer)
    @GetMapping("/publish/plans")
    public ApiResponse<?> plans(@PathVariable String kbId) {
        return ApiResponse.success(svc.listPlans(kbId));
    }
    @KbAuth(KBRole.viewer)
    @GetMapping("/publish/plans/{planId}/execution")
    public ApiResponse<?> planExecution(@PathVariable String planId) {
        return ApiResponse.success(svc.getPlanExecution(planId));
    }
    @KbAuth(KBRole.viewer)
    @GetMapping("/publish/strategy-effect")
    public ApiResponse<?> strategyEffect(@PathVariable String kbId) {
        return ApiResponse.success(svc.getStrategyEffect(kbId));
    }
    // 查看线上/线下版本
    @KbAuth(KBRole.viewer)
    @GetMapping("/publish/online-version")
    public ApiResponse<?> onlineVersion(@PathVariable String kbId, @RequestParam(required = false) String knowledgeId) {
        return ApiResponse.success(svc.getOnlineVersion(kbId, knowledgeId));
    }
    @KbAuth(KBRole.viewer)
    @GetMapping("/publish/offline-version")
    public ApiResponse<?> offlineVersion(@PathVariable String kbId, @RequestParam(required = false) String knowledgeId) {
        return ApiResponse.success(svc.getOfflineVersion(kbId, knowledgeId));
    }
    // 知识重置
    @KbAuth(KBRole.viewer)
    @GetMapping("/reset-configs")
    public ApiResponse<?> resetConfigs(@PathVariable String kbId) {
        return ApiResponse.success(svc.listResetConfigs(kbId));
    }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.reset_config_saved, detail = "保存重置配置")
    @PostMapping("/reset-configs")
    public ApiResponse<?> saveResetConfig(@PathVariable String kbId, @RequestBody KbResetConfig c) {
        c.setKbId(kbId);
        return ApiResponse.success(svc.saveResetConfig(c));
    }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.publish, action = ActionType.publish_reset, target = "#knowledgeId", detail = "重置知识")
    @PostMapping("/reset/{knowledgeId}")
    public ApiResponse<?> resetKnowledge(@PathVariable String kbId, @PathVariable String knowledgeId) {
        svc.resetKnowledge(kbId, knowledgeId);
        return ApiResponse.success();
    }
    // 更新日志
    @KbAuth(KBRole.viewer)
    @GetMapping("/knowledge-update-logs")
    public ApiResponse<?> updateLogs(@PathVariable String kbId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(svc.getKnowledgeUpdateLogs(kbId, page, pageSize));
    }
}
