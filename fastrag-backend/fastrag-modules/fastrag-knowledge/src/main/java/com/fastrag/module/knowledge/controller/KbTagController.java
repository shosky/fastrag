package com.fastrag.module.knowledge.controller;
/**
 * 知识库标签查询控制器，提供标签的全局查询接口。
 *
 * <p>核心职责：
 * 对外暴露知识库标签的只读查询 API，供前端实现标签自动补全等功能。
 * 标签列表按名称升序排列，返回完整的 KbTag 实体信息。
 *
 * <p>REST 端点（基础路径 /api/kb-tags）：
 * <ul>
 *   <li>GET /      — 查询所有标签列表，按 name 升序排列（无权限限制，需登录）</li>
 *   <li>GET /{id}  — 获取单个标签详情</li>
 * </ul>
 *
 * <p>依赖服务：KbTagMapper（标签数据访问，MyBatis-Plus）。
 */
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbTag;
import com.fastrag.module.knowledge.mapper.KbTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/kb-tags")
@RequiredArgsConstructor
public class KbTagController {

    private final KbTagMapper kbTagMapper;

    /**
     * 列出所有标签（用于前端自动补全）
     */
    @GetMapping
    public ApiResponse<List<KbTag>> list() {
        List<KbTag> tags = kbTagMapper.selectList(
                new LambdaQueryWrapper<KbTag>().orderByAsc(KbTag::getName)
        );
        return ApiResponse.success(tags);
    }

    /**
     * 获取单个标签详情
     */
    @GetMapping("/{id}")
    public ApiResponse<KbTag> get(@PathVariable String id) {
        KbTag tag = kbTagMapper.selectById(id);
        return ApiResponse.success(tag);
    }
}
