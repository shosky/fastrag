package com.fastrag.module.knowledge.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbCategory;
import com.fastrag.module.knowledge.mapper.KbCategoryMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/kb-categories") @RequiredArgsConstructor
public class KbCategoryController {
    private final KbCategoryMapper mapper;
    private final KnowledgeBaseMapper kbMapper;

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

    @PostMapping public ApiResponse<?> create(@RequestBody KbCategory c) {
        if (c.getSort() == null) c.setSort(0);
        c.setCreatedAt(LocalDateTime.now());
        mapper.insert(c);
        return ApiResponse.success(c);
    }

    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id, @RequestBody KbCategory c) {
        c.setId(id); mapper.updateById(c);
        return ApiResponse.success(mapper.selectById(id));
    }

    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }
}
