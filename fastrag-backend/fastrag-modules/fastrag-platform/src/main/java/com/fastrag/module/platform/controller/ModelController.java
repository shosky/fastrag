package com.fastrag.module.platform.controller;

/**
 * 模型管理控制器
 * <p>
 * 提供AI模型的完整生命周期管理，包括模型的增删改查、启用/禁用切换、
 * 批量导入，以及模型在线测试（对话、向量化、重排序）功能。
 * 同时支持M4模型预置配置的管理。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>模型列表与详情：GET /api/models（支持keyword和purpose过滤）、GET /api/models/{id}</li>
 *   <li>模型在线测试：POST /api/models/{id}/test-chat（对话测试）、
 *       POST /api/models/{id}/test-embedding（向量化测试）、POST /api/models/{id}/test-rerank（重排序测试）</li>
 *   <li>模型CRUD：POST /api/models（创建）、PUT /api/models/{id}（更新）、DELETE /api/models/{id}（删除）</li>
 *   <li>模型操作：POST /api/models/{id}/toggle（启用/禁用切换）、POST /api/models/import（批量导入）</li>
 *   <li>M4模型预置：GET /api/models/presets（列表）、POST /api/models/presets（创建）、
 *       PUT /api/models/presets/{id}（更新）、DELETE /api/models/presets/{id}（删除）</li>
 * </ul>
 *
 * <p>所有写操作通过 {@link com.fastrag.common.annotation.Loggable} 注解记录操作日志。
 * 测试端点允许前端直接对模型发起对话、Embedding、Rerank请求以验证模型可用性。</p>
 *
 * @see com.fastrag.module.platform.service.ModelService
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.platform.service.ModelService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
@RestController @RequestMapping("/api/models") @RequiredArgsConstructor
public class ModelController {
    private final ModelService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String purpose) { return ApiResponse.success(svc.list(keyword,purpose)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    // ===== 模型测试 =====
    @PostMapping("/{id}/test-chat") public ApiResponse<?> testChat(@PathVariable String id, @RequestBody Map<String,Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "你好，请简单介绍一下你自己");
        return ApiResponse.success(svc.testChat(id, prompt));
    }
    @PostMapping("/{id}/test-embedding") public ApiResponse<?> testEmbedding(@PathVariable String id, @RequestBody Map<String,Object> body) {
        String text = (String) body.getOrDefault("text", "你好世界");
        return ApiResponse.success(svc.testEmbedding(id, text));
    }
    @PostMapping("/{id}/test-rerank") public ApiResponse<?> testRerank(@PathVariable String id, @RequestBody Map<String,Object> body) {
        String query = (String) body.getOrDefault("query", "测试查询");
        @SuppressWarnings("unchecked") List<String> docs = (List<String>) body.getOrDefault("documents", List.of("文档1", "文档2", "文档3"));
        return ApiResponse.success(svc.testRerank(id, query, docs));
    }
    // ===== 模型 CRUD =====
    @Loggable(category=LogCategory.operation,action=ActionType.model_created)
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @Loggable(category=LogCategory.operation,action=ActionType.model_updated,target="#id")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @Loggable(category=LogCategory.operation,action=ActionType.model_deleted,target="#id")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @Loggable(category=LogCategory.operation,action=ActionType.model_updated,target="#id")
    @PostMapping("/{id}/toggle") public ApiResponse<?> toggle(@PathVariable String id) { svc.toggle(id); return ApiResponse.success(); }
    @Loggable(category=LogCategory.operation,action=ActionType.model_imported)
    @PostMapping("/import") public ApiResponse<?> importModels(@RequestBody List<Map<String,Object>> models) { return ApiResponse.success(svc.importModels(models)); }
    // ===== M4 模型预置 =====
    @GetMapping("/presets") public ApiResponse<?> presets() { return ApiResponse.success(svc.listPresets()); }
    @Loggable(category=LogCategory.operation,action=ActionType.model_created)
    @PostMapping("/presets") public ApiResponse<?> createPreset(@RequestBody Map<String,Object> preset) { return ApiResponse.success(svc.createPreset(preset)); }
    @Loggable(category=LogCategory.operation,action=ActionType.model_updated,target="#id")
    @PutMapping("/presets/{id}") public ApiResponse<?> updatePreset(@PathVariable String id,@RequestBody Map<String,Object> preset) { return ApiResponse.success(svc.updatePreset(id,preset)); }
    @Loggable(category=LogCategory.operation,action=ActionType.model_deleted,target="#id")
    @DeleteMapping("/presets/{id}") public ApiResponse<?> deletePreset(@PathVariable String id) { svc.deletePreset(id); return ApiResponse.success(); }
}
