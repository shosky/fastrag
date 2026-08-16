package com.fastrag.module.platform.controller;

/**
 * 术语库管理控制器
 * <p>
 * 提供专业术语库（TermLibrary）和术语条目（Term）的管理功能。
 * 术语库用于组织和管理特定领域的专业词汇，支持创建多个术语库并在每个库下维护术语条目。
 * 业务逻辑委托给 {@link com.fastrag.module.platform.service.TermService} 处理。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>GET /api/terminology/libraries — 查询所有术语库列表</li>
 *   <li>POST /api/terminology/libraries — 创建术语库</li>
 *   <li>DELETE /api/terminology/libraries/{id} — 删除术语库</li>
 *   <li>GET /api/terminology/terms — 查询术语条目列表，支持按 library 过滤</li>
 *   <li>POST /api/terminology/terms — 创建术语条目</li>
 *   <li>DELETE /api/terminology/terms/{id} — 删除术语条目</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.TermService
 * @see com.fastrag.module.platform.entity.TermLibrary
 * @see com.fastrag.module.platform.entity.TermRecord
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.platform.service.TermService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class TermController {
    private final TermService svc;
    @GetMapping("/api/terminology/libraries") public ApiResponse<?> libs() { return ApiResponse.success(svc.listLibraries()); }
    @PostMapping("/api/terminology/libraries") public ApiResponse<?> createLib(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.createLibrary(f)); }
    @DeleteMapping("/api/terminology/libraries/{id}") public ApiResponse<?> deleteLib(@PathVariable String id) { svc.deleteLibrary(id); return ApiResponse.success(); }
    @GetMapping("/api/terminology/terms") public ApiResponse<?> terms(@RequestParam(required=false) String library) { return ApiResponse.success(svc.listTerms(library)); }
    @PostMapping("/api/terminology/terms") public ApiResponse<?> createTerm(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.createTerm(f)); }
    @DeleteMapping("/api/terminology/terms/{id}") public ApiResponse<?> deleteTerm(@PathVariable String id) { svc.deleteTerm(id); return ApiResponse.success(); }
}
