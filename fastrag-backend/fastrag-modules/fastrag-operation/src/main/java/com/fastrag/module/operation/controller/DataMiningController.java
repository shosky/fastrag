package com.fastrag.module.operation.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.operation.entity.DataMiningTask;
import com.fastrag.module.operation.service.DataMiningService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/data-mining") @RequiredArgsConstructor
public class DataMiningController {
    private final DataMiningService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String kbId,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,keyword)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@RequestBody DataMiningTask task) { return ApiResponse.success(svc.create(task)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/run") public ApiResponse<?> run(@PathVariable String id) { return ApiResponse.success(svc.run(id)); }
}
