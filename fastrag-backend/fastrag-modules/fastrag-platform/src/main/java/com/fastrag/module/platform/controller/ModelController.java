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
    // 模型调用（真实 LLM 推理）
    @PostMapping("/{id}/invoke") public ApiResponse<?> invoke(@PathVariable String id,@RequestBody Map<String,Object> params) { return ApiResponse.success(svc.invoke(id,params)); }
    // 模型调用日志
    @GetMapping("/{id}/call-logs") public ApiResponse<?> callLogs(@PathVariable String id) { return ApiResponse.success(svc.listCallLogs(id)); }
    @PostMapping("/import") public ApiResponse<?> importModels(@RequestBody List<Map<String,Object>> models) { return ApiResponse.success(svc.importModels(models)); }
    // ===== M4 模型预置 =====
    @GetMapping("/presets") public ApiResponse<?> presets() { return ApiResponse.success(svc.listPresets()); }
    @PostMapping("/presets") public ApiResponse<?> createPreset(@RequestBody Map<String,Object> preset) { return ApiResponse.success(svc.createPreset(preset)); }
    @PutMapping("/presets/{id}") public ApiResponse<?> updatePreset(@PathVariable String id,@RequestBody Map<String,Object> preset) { return ApiResponse.success(svc.updatePreset(id,preset)); }
    @DeleteMapping("/presets/{id}") public ApiResponse<?> deletePreset(@PathVariable String id) { svc.deletePreset(id); return ApiResponse.success(); }


    // ===== 模型阈值设置（新增/查看/修改） =====
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.fastrag.module.platform.mapper.ModelRecordMapper modelMapper;
    @GetMapping("/{id}/threshold")
    public ApiResponse<?> getThreshold(@PathVariable String id) {
        if (modelMapper == null) return ApiResponse.success(null);
        var m = modelMapper.selectById(id);
        if (m == null) throw new RuntimeException("模型不存在");
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("id", m.getId());
        try { r.put("threshold", m.getThreshold() == null ? new java.util.HashMap<>() : new com.fasterxml.jackson.databind.ObjectMapper().readValue(m.getThreshold(), java.util.Map.class)); }
        catch (Exception e) { r.put("threshold", new java.util.HashMap<>()); }
        return ApiResponse.success(r);
    }
    @PutMapping("/{id}/threshold")
    public ApiResponse<?> updateThreshold(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (modelMapper == null) throw new RuntimeException("modelMapper 不可用");
        var m = modelMapper.selectById(id);
        if (m == null) throw new RuntimeException("模型不存在");
        Object threshold = body.get("threshold");
        m.setThreshold(threshold instanceof String ? (String) threshold : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(threshold).toString());
        modelMapper.updateById(m);
        return ApiResponse.success();
    }}
