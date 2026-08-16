package com.fastrag.module.graph.controller;

/**
 * 知识图谱评测（Evaluation）任务管理控制器。
 *
 * <p>提供评测任务的创建、查询、状态监控和删除等REST API。评测任务是对知识图谱进行
 * 自动化质量评估的执行单元，系统会基于基准测试题目，利用LLM对图谱数据进行问答式评测，
 * 并记录每道题的评测结果，最终汇总生成评测报告。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>评测任务列表与详情查询</li>
 *   <li>启动评测执行（支持自定义评测配置）</li>
 *   <li>实时查询评测进度（已完成数/总数/百分比）</li>
 *   <li>评测任务删除与清理</li>
 *   <li>操作日志记录</li>
 * </ul>
 *
 * <p>REST API端点：</p>
 * <ul>
 *   <li>GET /api/kb/{kbId}/evaluations - 获取评测任务列表</li>
 *   <li>GET /api/kb/{kbId}/evaluations/{id} - 获取评测任务详情</li>
 *   <li>GET /api/kb/{kbId}/evaluations/{id}/status - 获取评测执行进度状态</li>
 *   <li>POST /api/kb/{kbId}/evaluations/run - 启动新的评测任务</li>
 *   <li>DELETE /api/kb/{kbId}/evaluations/{id} - 删除指定评测任务</li>
 * </ul>
 *
 * <p>依赖服务：{@link EvaluationService} 处理核心评测业务逻辑。</p>
 *
 * @see EvaluationService
 * @see EvaluationConfig
 * @see KbEvaluation
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.model.EvaluationConfig;
import com.fastrag.module.graph.service.EvaluationService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kb/{kbId}/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService svc;
    private final LogService logService;

    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getDetail(kbId, id));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<?> status(@PathVariable String kbId, @PathVariable String id) {
        KbEvaluation e = svc.getDetail(kbId, id);
        if (e == null) {
            return ApiResponse.success(Map.of(
                    "id", id,
                    "status", "not_found",
                    "completedCount", 0,
                    "dataCount", 0,
                    "progress", 0));
        }
        int completed = e.getCompletedCount() != null ? e.getCompletedCount() : 0;
        int total = e.getDataCount() != null ? e.getDataCount() : 0;
        int progress = total > 0 ? (int) ((double) completed / total * 100) : 0;
        return ApiResponse.success(Map.of(
                "id", e.getId(),
                "status", e.getStatus(),
                "completedCount", completed,
                "dataCount", total,
                "progress", progress));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.evaluation_run, detail = "启动评测任务")
    @PostMapping("/run")
    public ApiResponse<?> run(@PathVariable String kbId, @RequestBody(required = false) EvaluationConfig config) {
        return ApiResponse.success(svc.run(kbId, config != null ? config : new EvaluationConfig()));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.evaluation_deleted, detail = "删除评测任务")
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }
}
