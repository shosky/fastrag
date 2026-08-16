package com.fastrag.module.operation.controller;

/**
 * 数据挖掘任务控制器。
 *
 * <p>提供数据挖掘任务的完整 CRUD 操作和执行触发接口，支持按知识库ID和关键词过滤任务列表。
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/data-mining - 查询数据挖掘任务列表，支持 kbId、keyword 参数</li>
 *     <li>GET /api/data-mining/{id} - 获取单个数据挖掘任务详情</li>
 *     <li>POST /api/data-mining - 创建新的数据挖掘任务</li>
 *     <li>DELETE /api/data-mining/{id} - 删除指定数据挖掘任务</li>
 *     <li>POST /api/data-mining/{id}/run - 触发执行指定的数据挖掘任务</li>
 * </ul>
 *
 * <p>委托 {@link DataMiningService} 完成具体的业务逻辑处理。
 */
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
