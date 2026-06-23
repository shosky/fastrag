package com.fastrag.module.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SensitiveWord;
import com.fastrag.module.platform.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sensitive-words")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordMapper mapper;

    @GetMapping
    public ApiResponse<?> list() {
        return ApiResponse.success(new java.util.ArrayList<>());
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> body) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord((String) body.get("word"));
        sw.setReplacement((String) body.getOrDefault("reply", ""));
        sw.setCategory(buildCategoryJson(body));
        sw.setEnabled(1);
        mapper.insert(sw);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SensitiveWord sw = mapper.selectById(id);
        if (sw == null) return ApiResponse.notFound("敏感词不存在");
        sw.setWord((String) body.getOrDefault("word", sw.getWord()));
        sw.setReplacement((String) body.getOrDefault("reply", sw.getReplacement()));
        sw.setCategory(buildCategoryJson(body));
        mapper.updateById(sw);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }

    private String buildCategoryJson(Map<String, Object> body) {
        boolean blockInput = Boolean.TRUE.equals(body.get("blockInput"));
        boolean blockSearch = Boolean.TRUE.equals(body.get("blockSearch"));
        boolean replaceAnswer = Boolean.TRUE.equals(body.get("replaceAnswer"));
        return String.format("{\"blockInput\":%s,\"blockSearch\":%s,\"replaceAnswer\":%s}",
                blockInput, blockSearch, replaceAnswer);
    }
}
