package com.fastrag.module.knowledge.controller;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.KbCreateRequest;
import com.fastrag.module.knowledge.model.KbDto;
import com.fastrag.module.knowledge.service.KbService; import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.util.SecurityUtil;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb") @RequiredArgsConstructor @Slf4j
public class KbController {
    private final KbService svc;
    private final LogService logService;

    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String category,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(svc.list(keyword,category,page,pageSize)); }
    @GetMapping("/categories") public ApiResponse<?> categories() { return ApiResponse.success(svc.getCategories()); }
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

    @Loggable(category = LogCategory.operation, action = ActionType.kb_updated, target = "#id", detail = "更新知识库信息")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@Valid @RequestBody KbCreateRequest req) { return ApiResponse.success(svc.update(id,req)); }

    @Loggable(category = LogCategory.operation, action = ActionType.kb_deleted, target = "#id", detail = "删除知识库")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
}
