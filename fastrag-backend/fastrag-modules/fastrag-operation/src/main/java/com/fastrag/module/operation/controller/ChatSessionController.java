package com.fastrag.module.operation.controller;

/**
 * 聊天会话控制器。
 *
 * <p>提供用户聊天会话（问答记录）的分页查询接口，主要用于 Feedback 页面的"问答明细"Tab展示。
 * 支持按关键词（模糊匹配用户提问内容）和用户ID进行筛选，按创建时间倒序排列。
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/chat-sessions - 分页查询聊天会话列表，支持 keyword、userId、page、pageSize 参数</li>
 * </ul>
 *
 * <p>依赖 {@link ChatSessionMapper} 直接操作数据库查询。
 */
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.operation.entity.ChatSession;
import com.fastrag.module.operation.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat-sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionMapper chatSessionMapper;

    /**
     * 问答明细分页查询（用于 Feedback 页面的"问答明细"Tab）
     */
    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        var w = new LambdaQueryWrapper<ChatSession>();
        if (keyword != null && !keyword.isEmpty()) {
            w.like(ChatSession::getQuery, keyword);
        }
        if (userId != null && !userId.isEmpty()) {
            w.like(ChatSession::getUserId, userId);
        }
        w.orderByDesc(ChatSession::getCreatedAt);
        var pg = chatSessionMapper.selectPage(new Page<>(page, pageSize), w);
        return ApiResponse.success(PageResult.of(pg.getRecords(), pg.getTotal(), page, pageSize));
    }
}
