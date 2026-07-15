package com.fastrag.module.knowledge.controller;

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
