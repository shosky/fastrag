package com.fastrag.module.knowledge.controller;
/**
 * 解析策略管理控制器，提供知识库级别的解析策略 CRUD 与冲突检测 REST API。
 *
 * <p>核心职责：
 * 管理知识库的文档解析策略（ParseStrategy），包括策略的创建、修改、删除、
 * 设为默认策略、按文件扩展名解析匹配策略以及检测扩展名冲突。所有写操作
 * 均通过 KbAuth 限制为 editor 及以上角色，并记录审计日志。
 *
 * <p>REST 端点（基础路径 /api/kb/{kbId}/parse-strategies）：
 * <ul>
 *   <li>GET    /                   — 查询该知识库下所有解析策略列表（viewer 及以上）</li>
 *   <li>GET    /{id}               — 获取单个解析策略详情（viewer 及以上）</li>
 *   <li>POST   /                   — 创建解析策略（editor 及以上）</li>
 *   <li>PUT    /{id}               — 更新解析策略（editor 及以上）</li>
 *   <li>DELETE /{id}               — 删除解析策略（editor 及以上）</li>
 *   <li>POST   /{id}/set-default   — 将指定策略设为默认解析策略（editor 及以上）</li>
 *   <li>GET    /resolve             — 按文件扩展名解析匹配策略（viewer 及以上）</li>
 *   <li>POST   /conflicts          — 检测扩展名冲突，传入扩展名列表和排除 ID（editor 及以上）</li>
 * </ul>
 *
 * <p>依赖服务：ParseStrategyService（策略业务逻辑）、LogService（操作审计日志）。
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.ParseStrategyRequest;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.module.knowledge.service.ParseStrategyService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/kb/{kbId}/parse-strategies") @RequiredArgsConstructor
public class ParseStrategyController {
    private final ParseStrategyService svc;
    private final LogService logService;
    @KbAuth(KBRole.viewer)
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(svc.get(kbId,id)); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.strategy_created, detail = "创建解析策略")
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody ParseStrategyRequest req) { return ApiResponse.success(svc.create(kbId,req)); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.strategy_updated, detail = "更新解析策略")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody ParseStrategyRequest req) { return ApiResponse.success(svc.update(kbId,id,req)); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.strategy_deleted, detail = "删除解析策略")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.strategy_set_default, detail = "设为默认解析策略")
    @PostMapping("/{id}/set-default") public ApiResponse<?> setDefault(@PathVariable String kbId,@PathVariable String id) { svc.setDefault(kbId,id); return ApiResponse.success(); }
    @KbAuth(KBRole.viewer)
    @GetMapping("/resolve") public ApiResponse<?> resolve(@PathVariable String kbId,@RequestParam String extension) { return ApiResponse.success(svc.resolveByExtension(kbId,extension)); }
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.strategy_updated, detail = "检测解析策略冲突")
    @PostMapping("/conflicts") public ApiResponse<?> conflicts(@PathVariable String kbId,@RequestBody java.util.Map<String,Object> b) { return ApiResponse.success(svc.detectConflicts(kbId,(List<String>)b.get("extensions"),(String)b.get("excludeId"))); }
}
