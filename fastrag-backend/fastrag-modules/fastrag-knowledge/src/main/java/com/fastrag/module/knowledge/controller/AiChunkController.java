package com.fastrag.module.knowledge.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.model.AiChunkApplyRequest;
import com.fastrag.module.knowledge.model.AiChunkResult;
import com.fastrag.module.knowledge.service.AiChunkService;
import com.fastrag.security.annotation.KbAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI分片 控制器（设计文档 docs/design/ai-chunk-preview-semantic.md §7）。
 *
 * <p>提供的 REST API 端点（基础路径 {@code /api/kb/{kbId}/files}）：
 * <ul>
 *   <li>{@code GET /{id}/ai-chunk/stream} — SSE 流式分析：progress/fragment/chunk/done/error 事件，
 *       右侧分片卡逐个出现（viewer 权限）</li>
 *   <li>{@code POST /{id}/ai-chunk-preview} — 同步分析兜底：一次返回段落对齐模型 + 自动分片结果（viewer 权限）</li>
 *   <li>{@code POST /{id}/ai-chunk-apply} — 应用：尊重用户确认的分片边界落库，不重新切分/不重新解析（editor 权限）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/kb/{kbId}/files")
@RequiredArgsConstructor
public class AiChunkController {

    private final AiChunkService aiChunkService;

    /** SSE 流式 AI分片（fetch + ReadableStream 消费，事件协议见设计文档 §7.2） */
    @KbAuth(KBRole.viewer)
    @GetMapping(value = "/{id}/ai-chunk/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String kbId, @PathVariable String id) {
        return aiChunkService.stream(kbId, id);
    }

    /**
     * SSE 版面分析（原件渲染分块画框数据源）：layout 事件逐页推送内容块
     * （类型 + 归一化 bbox），done 事件收尾；MinIO 缓存命中时一次全量推送。
     */
    @KbAuth(KBRole.viewer)
    @GetMapping(value = "/{id}/ai-chunk/layout", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLayout(@PathVariable String kbId, @PathVariable String id) {
        return aiChunkService.streamLayout(kbId, id);
    }

    /** 同步 AI分片 预览（SSE 不可用时的兜底）。chunk=false 仅返回段落对齐模型（渲染左侧原件），不触发 LLM 分片 */
    @KbAuth(KBRole.viewer)
    @PostMapping("/{id}/ai-chunk-preview")
    public ApiResponse<AiChunkResult> preview(@PathVariable String kbId, @PathVariable String id,
                                              @RequestParam(defaultValue = "true") boolean chunk) {
        return ApiResponse.success(aiChunkService.analyze(kbId, id, chunk));
    }

    /** 应用 AI分片：以用户确认的分片列表为唯一事实源落库（删旧 → 向量化 → 写 parsed.md/json） */
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_updated, detail = "应用 AI分片")
    @PostMapping("/{id}/ai-chunk-apply")
    public ApiResponse<Integer> apply(@PathVariable String kbId, @PathVariable String id,
                                      @RequestBody AiChunkApplyRequest request) {
        return ApiResponse.success(aiChunkService.apply(kbId, id, request));
    }
}
