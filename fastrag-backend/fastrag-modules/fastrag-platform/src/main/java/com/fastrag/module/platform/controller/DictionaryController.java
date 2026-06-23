package com.fastrag.module.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SysDictionary;
import com.fastrag.module.platform.mapper.SysDictionaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private final SysDictionaryMapper mapper;

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String type) {
        try {
            LambdaQueryWrapper<SysDictionary> query = new LambdaQueryWrapper<>();
            if (type != null && !type.isEmpty()) {
                query.eq(SysDictionary::getDictType, type);
            }
            query.orderByAsc(SysDictionary::getId);
            List<SysDictionary> list = mapper.selectList(query);

            Map<String, List<Map<String, Object>>> grouped = new java.util.LinkedHashMap<>();
            for (SysDictionary d : list) {
                String dictType = d.getDictType() != null ? d.getDictType() : "未分类";
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", String.valueOf(d.getId()));
                item.put("key", d.getDictKey() != null ? d.getDictKey() : "");
                item.put("label", d.getDictKey() != null ? d.getDictKey() : "");
                item.put("value", d.getDictValue() != null ? d.getDictValue() : "");
                item.put("enabled", true);
                item.put("remark", "");
                grouped.computeIfAbsent(dictType, k -> new java.util.ArrayList<>()).add(item);
            }
            return ApiResponse.success(grouped);
        } catch (Exception e) {
            return ApiResponse.serverError("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/types")
    public ApiResponse<?> getTypes() {
        List<SysDictionary> all = mapper.selectList(null);
        java.util.Set<String> typeSet = new java.util.LinkedHashSet<>();
        for (SysDictionary d : all) {
            if (d.getDictType() != null && !d.getDictType().isEmpty()) {
                typeSet.add(d.getDictType());
            }
        }
        return ApiResponse.success(new java.util.ArrayList<>(typeSet));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> body) {
        SysDictionary dict = new SysDictionary();
        dict.setDictType((String) body.get("type"));
        dict.setDictKey((String) body.get("key"));
        dict.setDictValue((String) body.get("value"));
        mapper.insert(dict);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SysDictionary dict = mapper.selectById(id);
        if (dict == null) return ApiResponse.notFound("字典条目不存在");
        dict.setDictType((String) body.getOrDefault("type", dict.getDictType()));
        dict.setDictKey((String) body.getOrDefault("key", dict.getDictKey()));
        dict.setDictValue((String) body.getOrDefault("value", dict.getDictValue()));
        mapper.updateById(dict);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }
}
