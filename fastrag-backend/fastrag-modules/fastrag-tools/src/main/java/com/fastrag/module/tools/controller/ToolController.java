package com.fastrag.module.tools.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.tools.entity.ToolHttpConfig;
import com.fastrag.module.tools.service.ToolService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/tools") @RequiredArgsConstructor
public class ToolController {
    private final ToolService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String type) { return ApiResponse.success(svc.list(keyword,type)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/toggle") public ApiResponse<?> toggle(@PathVariable String id) { svc.toggleEnabled(id); return ApiResponse.success(); }
    // ===== M4 API插件配置 =====
    @GetMapping("/{id}/api-config") public ApiResponse<?> getApiConfig(@PathVariable String id) { return ApiResponse.success(svc.getApiConfig(id)); }
    @PutMapping("/{id}/api-config") public ApiResponse<?> saveApiConfig(@PathVariable String id,@RequestBody ToolHttpConfig config) { return ApiResponse.success(svc.saveApiConfig(id,config)); }

    // ===== 插件管理 - 上传插件 =====
    @PostMapping("/upload") public ApiResponse<?> uploadPlugin(@RequestParam("file") MultipartFile file,
        @RequestParam(required=false) String name, @RequestParam(required=false) String description) {
        return ApiResponse.success(svc.uploadPlugin(file, name, description));
    }

    // ===== 插件管理 - JSON导入 =====
    @PostMapping("/import-json") public ApiResponse<?> importJson(@RequestBody List<Map<String,Object>> plugins) {
        return ApiResponse.success(svc.importFromJson(plugins));
    }
}
