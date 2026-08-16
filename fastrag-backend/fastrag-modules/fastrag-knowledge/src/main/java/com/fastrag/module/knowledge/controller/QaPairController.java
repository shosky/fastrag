package com.fastrag.module.knowledge.controller;
/**
 * QA 问答对管理控制器，提供知识库级别的问答对 CRUD REST API。
 *
 * <p>核心职责：
 * 管理知识库中的 QA 问答对（KbQaPair），支持问答对的创建、查询、更新、
 * 删除和确认操作。QA 对可用于知识问答场景中人工补充标准问答内容，确认操作
 * 标记 QA 对为已审核状态。所有写操作需 editor 角色并记录审计日志。
 *
 * <p>REST 端点（基础路径 /api/kb/{kbId}/qa-pairs）：
 * <ul>
 *   <li>GET    /           — 查询问答对列表，可按 fileId 筛选（viewer 及以上）</li>
 *   <li>POST   /           — 创建问答对（editor 及以上），请求体为 QaCreateRequest</li>
 *   <li>PUT    /{id}       — 更新问答对（editor 及以上）</li>
 *   <li>DELETE /{id}       — 删除问答对（editor 及以上）</li>
 *   <li>POST   /{id}/confirm — 确认问答对（editor 及以上），标记为已审核</li>
 * </ul>
 *
 * <p>依赖服务：QaPairService（问答对业务逻辑）、LogService（操作审计日志）。
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.QaCreateRequest;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.module.knowledge.service.QaPairService;
import com.fastrag.module.publish.service.LogService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/qa-pairs") @RequiredArgsConstructor
public class QaPairController {
    private final QaPairService svc;
    private final LogService logService;
    @KbAuth(KBRole.viewer)
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId, @RequestParam(required = false) String fileId) { return ApiResponse.success(svc.list(kbId, fileId)); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_created, detail = "创建QA对")
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@Valid @RequestBody QaCreateRequest req) { return ApiResponse.success(svc.create(kbId,req)); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_updated, detail = "更新QA对")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody Map<String,Object> p) { return ApiResponse.success(svc.update(kbId,id,p)); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_deleted, detail = "删除QA对")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.qa_pair_confirmed, detail = "确认QA对")
    @PostMapping("/{id}/confirm") public ApiResponse<?> confirm(@PathVariable String kbId,@PathVariable String id) { svc.confirm(kbId,id); return ApiResponse.success(); }
}
