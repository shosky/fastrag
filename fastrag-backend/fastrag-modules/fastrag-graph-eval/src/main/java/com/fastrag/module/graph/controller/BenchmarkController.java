package com.fastrag.module.graph.controller;

/**
 * 知识图谱基准测试（Benchmark）管理控制器。
 *
 * <p>提供基准测试的完整生命周期管理REST API，包括基准测试的创建、列表查询、详情获取、
 * 问题列表查看、自动生成测试题目以及删除操作。基准测试用于对知识图谱的质量进行
 * 自动化评估，通过生成基于图谱内容的问题来验证图谱的准确性和完整性。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>基准测试的CRUD操作管理</li>
 *   <li>调用LLM自动生成基准测试题目（基于图谱三元组数据）</li>
 *   <li>操作日志记录（通过{@link com.fastrag.common.annotation.Loggable}注解）</li>
 * </ul>
 *
 * <p>REST API端点：</p>
 * <ul>
 *   <li>GET /api/kb/{kbId}/benchmarks - 获取指定知识库下的基准测试列表</li>
 *   <li>GET /api/kb/{kbId}/benchmarks/{id} - 获取基准测试详情</li>
 *   <li>GET /api/kb/{kbId}/benchmarks/{id}/questions - 获取基准测试关联的题目列表</li>
 *   <li>POST /api/kb/{kbId}/benchmarks - 创建新的基准测试</li>
 *   <li>POST /api/kb/{kbId}/benchmarks/generate - 触发LLM生成基准测试题目</li>
 *   <li>DELETE /api/kb/{kbId}/benchmarks/{id} - 删除指定基准测试</li>
 * </ul>
 *
 * <p>依赖服务：{@link BenchmarkService} 处理核心业务逻辑，{@link com.fastrag.module.publish.service.LogService} 记录操作日志。</p>
 *
 * @see BenchmarkService
 * @see BenchmarkCreateForm
 * @see BenchmarkConfig
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.graph.model.BenchmarkConfig;
import com.fastrag.module.graph.model.BenchmarkCreateForm;
import com.fastrag.module.graph.service.BenchmarkService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb/{kbId}/benchmarks")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService svc;
    private final LogService logService;

    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getDetail(kbId, id));
    }

    @GetMapping("/{id}/questions")
    public ApiResponse<?> getQuestions(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.listQuestions(kbId, id));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.benchmark_created, detail = "创建基准测试")
    @PostMapping
    public ApiResponse<?> create(@PathVariable String kbId, @RequestBody BenchmarkCreateForm form) {
        return ApiResponse.success(svc.create(kbId, form));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.benchmark_generated, detail = "生成基准测试题目")
    @PostMapping("/generate")
    public ApiResponse<?> generate(@PathVariable String kbId, @RequestBody BenchmarkConfig config) {
        return ApiResponse.success(svc.generate(kbId, config));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.benchmark_deleted, detail = "删除基准测试")
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }
}
