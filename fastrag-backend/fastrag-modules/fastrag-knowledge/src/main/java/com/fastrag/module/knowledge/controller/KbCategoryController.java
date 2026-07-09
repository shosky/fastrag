package com.fastrag.module.knowledge.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbCategory;
import com.fastrag.module.knowledge.mapper.KbCategoryMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/kb-categories") @RequiredArgsConstructor
public class KbCategoryController {
    private final KbCategoryMapper mapper;
    private final KnowledgeBaseMapper kbMapper;
    private final LogService logService;

    @GetMapping public ApiResponse<?> list() {
        var cats = mapper.selectList(new LambdaQueryWrapper<KbCategory>().orderByAsc(KbCategory::getSort));
        // 统计每个分类下的知识库数量
        var allKbs = kbMapper.selectList(null);
        Map<String, Long> countMap = new HashMap<>();
        for (var kb : allKbs) {
            if (kb.getCategory() != null) countMap.merge(kb.getCategory(), 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var c : cats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId()); m.put("name", c.getName());
            m.put("description", c.getDescription()); m.put("color", c.getColor());
            m.put("icon", c.getIcon()); m.put("sort", c.getSort());
            m.put("count", countMap.getOrDefault(c.getName(), 0L));
            result.add(m);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) {
        return ApiResponse.success(mapper.selectById(id));
    }

    @GetMapping("/{id}/usage") public ApiResponse<?> usage(@PathVariable String id) {
        KbCategory c = mapper.selectById(id);
        if (c == null) return ApiResponse.success(Map.of("count", 0));
        long usage = kbMapper.selectCount(new LambdaQueryWrapper<com.fastrag.module.knowledge.entity.KnowledgeBase>().eq(com.fastrag.module.knowledge.entity.KnowledgeBase::getCategory, c.getName()));
        return ApiResponse.success(Map.of("categoryId", id, "categoryName", c.getName(), "count", usage));
    }

    @PostMapping public ApiResponse<?> create(@RequestBody KbCategory c) {
        if (c.getSort() == null) c.setSort(0);
        c.setCreatedAt(LocalDateTime.now());
        mapper.insert(c);
        try {
            logService.addLog("global", LogCategory.operation, ActionType.config_changed,
                    c.getId(), "创建知识库分类: " + c.getName(), "system", "success", null);
        } catch (Exception e) {
            // ignore log failure
        }
        return ApiResponse.success(c);
    }

    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id, @RequestBody KbCategory c) {
        c.setId(id); mapper.updateById(c);
        try {
            logService.addLog("global", LogCategory.operation, ActionType.config_changed,
                    id, "更新知识库分类: " + c.getName(), "system", "success", null);
        } catch (Exception e) {
            // ignore log failure
        }
        return ApiResponse.success(mapper.selectById(id));
    }

    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) {
        KbCategory c = mapper.selectById(id);
        mapper.deleteById(id);
        try {
            logService.addLog("global", LogCategory.operation, ActionType.config_changed,
                    id, "删除知识库分类: " + (c != null ? c.getName() : id), "system", "success", null);
        } catch (Exception e) {
            // ignore log failure
        }
        return ApiResponse.success();
    }
}
