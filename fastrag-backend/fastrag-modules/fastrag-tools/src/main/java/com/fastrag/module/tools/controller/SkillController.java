package com.fastrag.module.tools.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.tools.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {
    private final SkillService svc;

    /** 列出所有技能 */
    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        return ApiResponse.success(svc.list(keyword, category));
    }

    /** 列出用户可访问的已启用技能 (用于 Agent 配置中可选技能列表) */
    @GetMapping("/accessible")
    public ApiResponse<?> listAccessible(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceType) {
        return ApiResponse.success(svc.listAccessible(keyword, category, sourceType));
    }

    /** 列出内置技能 */
    @GetMapping("/builtin")
    public ApiResponse<?> listBuiltin() {
        return ApiResponse.success(svc.listBuiltin());
    }

    /** 获取单个技能 */
    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String id) {
        return ApiResponse.success(svc.get(id));
    }

    /** 根据 slug 获取技能 */
    @GetMapping("/slug/{slug}")
    public ApiResponse<?> getBySlug(@PathVariable String slug) {
        return ApiResponse.success(svc.getBySlug(slug));
    }

    /** 创建技能 */
    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.create(form));
    }

    /** 更新技能 */
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Map<String, Object> form) {
        return ApiResponse.success(svc.update(id, form));
    }

    /** 删除技能 */
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String id) {
        svc.delete(id);
        return ApiResponse.success();
    }

    /** 切换技能启用状态 */
    @PostMapping("/{id}/toggle")
    public ApiResponse<?> toggle(@PathVariable String id) {
        svc.toggleEnabled(id);
        return ApiResponse.success();
    }
}
