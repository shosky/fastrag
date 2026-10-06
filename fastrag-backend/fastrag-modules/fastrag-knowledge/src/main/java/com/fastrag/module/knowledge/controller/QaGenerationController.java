package com.fastrag.module.knowledge.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.model.QaGenerationConfig;
import com.fastrag.module.knowledge.model.QaGenerationTaskView;
import com.fastrag.module.knowledge.service.QaGenerationService;
import com.fastrag.security.annotation.KbAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 问答对提炼控制器（qa-generation，(S) 薄片——trigger/status 透传 + KB ACL + 审计）。
 *
 * <p>REST 端点（基础路径 /api/kb/{kbId}/qa-pairs/generation）：
 * <ul>
 *   <li>POST /{fileId} — 触发问答对提炼（editor 及以上），请求体可省略（全缺省：
 *       count=3 / mode=replace / model=默认网关）；批量场景由前端对多选文件逐个调用</li>
 *   <li>GET  /{fileId} — 查询最近一次提炼任务状态（viewer 及以上），从未触发返回 data=null</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/kb/{kbId}/qa-pairs/generation")
@RequiredArgsConstructor
public class QaGenerationController {

    private final QaGenerationService svc;

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.qa_generation_triggered, detail = "触发问答对提炼")
    @PostMapping("/{fileId}")
    public ApiResponse<QaGenerationTaskView> trigger(@PathVariable String kbId,
                                                     @PathVariable String fileId,
                                                     @RequestBody(required = false) QaGenerationConfig config) {
        return ApiResponse.success(svc.trigger(kbId, fileId, config));
    }

    @KbAuth(KBRole.viewer)
    @GetMapping("/{fileId}")
    public ApiResponse<QaGenerationTaskView> status(@PathVariable String kbId,
                                                    @PathVariable String fileId) {
        return ApiResponse.success(svc.status(kbId, fileId));
    }
}
