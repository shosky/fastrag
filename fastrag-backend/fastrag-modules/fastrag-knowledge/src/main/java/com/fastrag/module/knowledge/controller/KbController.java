package com.fastrag.module.knowledge.controller;
/**
 * 知识库管理控制器，提供知识库的完整生命周期 REST API。
 *
 * <p>核心职责：
 * 对外暴露知识库的增删改查接口，支持按关键字和分类筛选分页列表，
 * 并提供分类聚合查询能力。所有写操作均记录审计日志至 LogService。
 *
 * <p>REST 端点：
 * <ul>
 *   <li>GET  /api/kb — 分页查询知识库列表，支持 keyword/category 筛选（无需 KbAuth，登录即可）</li>
 *   <li>GET  /api/kb/categories — 获取知识库分类聚合列表（无需 KbAuth）</li>
 *   <li>GET  /api/kb/{id} — 获取单个知识库详情（viewer 及以上）</li>
 *   <li>POST /api/kb — 创建知识库（登录即可，自动关联创建者）</li>
 *   <li>PUT  /api/kb/{id} — 更新知识库信息（editor 及以上）</li>
 *   <li>DELETE /api/kb/{id} — 删除知识库（仅 owner）</li>
 * </ul>
 *
 * <p>权限说明：列表和创建接口无 KbAuth 限制，仅需登录；详情和修改需对应 KB 角色。
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.KbCreateRequest;
import com.fastrag.module.knowledge.model.KbDto;
import com.fastrag.module.knowledge.service.KbService; import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.security.util.SecurityUtil;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb") @RequiredArgsConstructor @Slf4j
public class KbController {
    private final KbService svc;
    private final LogService logService;

    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String category,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(svc.list(keyword,category,page,pageSize)); }
    @GetMapping("/categories") public ApiResponse<?> categories() { return ApiResponse.success(svc.getCategories()); }

    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody KbCreateRequest req) {
        KbDto result = svc.create(req, SecurityUtil.getCurrentUserId());
        try {
            String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
            logService.addLog(result.getId(), LogCategory.operation, ActionType.kb_created,
                    result.getName(), "创建知识库: " + result.getName(), username, "success", null);
        } catch (Exception e) {
            log.warn("Failed to log KB creation", e);
        }
        return ApiResponse.success(result);
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.kb_updated, target = "#id", detail = "更新知识库信息")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@Valid @RequestBody KbCreateRequest req) { return ApiResponse.success(svc.update(id,req)); }

    @KbAuth(KBRole.owner)
    @Loggable(category = LogCategory.operation, action = ActionType.kb_deleted, target = "#id", detail = "删除知识库")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
}
