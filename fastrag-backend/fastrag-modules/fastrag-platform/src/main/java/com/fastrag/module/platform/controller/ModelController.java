package com.fastrag.module.platform.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.platform.service.ModelService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/models") @RequiredArgsConstructor
public class ModelController {
    private final ModelService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String purpose) { return ApiResponse.success(svc.list(keyword,purpose)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/toggle") public ApiResponse<?> toggle(@PathVariable String id) { svc.toggle(id); return ApiResponse.success(); }
    @PostMapping("/import") public ApiResponse<?> importModels(@RequestBody List<Map<String,Object>> models) { return ApiResponse.success(svc.importModels(models)); }
    // ===== M4 模型预置 =====
    @GetMapping("/presets") public ApiResponse<?> presets() { return ApiResponse.success(svc.listPresets()); }
    @PostMapping("/presets") public ApiResponse<?> createPreset(@RequestBody Map<String,Object> preset) { return ApiResponse.success(svc.createPreset(preset)); }
    @PutMapping("/presets/{id}") public ApiResponse<?> updatePreset(@PathVariable String id,@RequestBody Map<String,Object> preset) { return ApiResponse.success(svc.updatePreset(id,preset)); }
    @DeleteMapping("/presets/{id}") public ApiResponse<?> deletePreset(@PathVariable String id) { svc.deletePreset(id); return ApiResponse.success(); }
}
