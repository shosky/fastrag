package com.fastrag.module.platform.controller;

/**
 * 敏感词管理控制器
 * <p>
 * 提供敏感词的增删改查、批量导入导出功能。敏感词配置包含三级拦截策略：
 * 阻止用户输入（blockInput）、联网检索屏蔽（blockSearch）、模型生成答案时替换（replaceAnswer）。
 * 敏感词的分类信息以 JSON 字符串形式存储在 category 字段中，查询接口会将其解析为独立布尔字段供前端使用。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>GET /api/sensitive-words — 查询敏感词列表，返回格式化的前端表格数据（word、reply、blockInput、blockSearch、replaceAnswer 等）</li>
 *   <li>POST /api/sensitive-words — 创建敏感词，包含词语、替换文本、分类标志和级别</li>
 *   <li>PUT /api/sensitive-words/{id} — 更新敏感词</li>
 *   <li>DELETE /api/sensitive-words/{id} — 删除敏感词</li>
 *   <li>GET /api/sensitive-words/template — 下载 CSV 模板文件，用于批量导入</li>
 *   <li>POST /api/sensitive-words/import — 从 CSV 文件批量导入敏感词</li>
 * </ul>
 *
 * <p>批量导入时跳过 CSV 文件头行，逐行解析敏感词、指定回复和三个布尔标志。
 * 文件上传使用 Spring 的 MultipartFile 处理。</p>
 *
 * @see com.fastrag.module.platform.service.SensitiveWordService
 * @see com.fastrag.module.platform.entity.SensitiveWord
 */

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SensitiveWord;
import com.fastrag.module.platform.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/sensitive-words")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    /**
     * GET /api/sensitive-words
     * Returns list formatted for frontend table: each item has word, reply, blockInput, blockSearch, replaceAnswer.
     */
    @GetMapping
    public ApiResponse<?> list() {
        List<SensitiveWord> words = sensitiveWordService.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SensitiveWord sw : words) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", sw.getId());
            item.put("word", sw.getWord() != null ? sw.getWord() : "");
            // Frontend expects 'reply' field for the replacement text
            item.put("reply", sw.getReplacement() != null ? sw.getReplacement() : "");
            // Parse category JSON into individual boolean fields for frontend
            item.put("blockInput", parseCategoryFlag(sw.getCategory(), "blockInput"));
            item.put("blockSearch", parseCategoryFlag(sw.getCategory(), "blockSearch"));
            item.put("replaceAnswer", parseCategoryFlag(sw.getCategory(), "replaceAnswer"));
            item.put("level", sw.getLevel() != null ? sw.getLevel() : "");
            item.put("enabled", sw.getEnabled() != null ? sw.getEnabled() == 1 : true);
            item.put("createdAt", sw.getCreatedAt() != null ? sw.getCreatedAt().toString() : "");
            result.add(item);
        }
        return ApiResponse.success(result);
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> body) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord((String) body.get("word"));
        sw.setReplacement((String) body.getOrDefault("reply", ""));
        sw.setCategory(buildCategoryJson(body));
        sw.setEnabled(1);
        sw.setLevel((String) body.getOrDefault("level", ""));
        sensitiveWordService.save(sw);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SensitiveWord sw = sensitiveWordService.getById(id);
        if (sw == null) return ApiResponse.notFound("敏感词不存在");
        sw.setWord((String) body.getOrDefault("word", sw.getWord()));
        sw.setReplacement((String) body.getOrDefault("reply", sw.getReplacement()));
        sw.setCategory(buildCategoryJson(body));
        if (body.containsKey("level")) {
            sw.setLevel((String) body.get("level"));
        }
        sensitiveWordService.update(sw);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        return ApiResponse.success();
    }

    /**
     * GET /api/sensitive-words/template
     * Download CSV template for batch import.
     */
    @GetMapping("/template")
    public org.springframework.http.ResponseEntity<byte[]> downloadTemplate() {
        String csv = "敏感词,指定回复,阻止用户输入,联网检索屏蔽,模型生成答案时替换\n"
                   + "违禁词,您的问题包含不适当内容,true,false,false\n";
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=sensitive_words_template.csv")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(bytes);
    }

    /**
     * POST /api/sensitive-words/import
     * Batch import sensitive words from CSV file.
     */
    @PostMapping("/import")
    public ApiResponse<?> batchImport(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.serverError("请上传文件");
        }
        try {
            String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            int imported = 0;
            for (int i = 1; i < lines.length; i++) { // skip header
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length < 1 || parts[0].trim().isEmpty()) continue;
                SensitiveWord sw = new SensitiveWord();
                sw.setWord(parts[0].trim());
                sw.setReplacement(parts.length > 1 ? parts[1].trim() : "");
                sw.setEnabled(1);
                // Parse boolean flags
                boolean blockInput = parts.length > 2 && Boolean.parseBoolean(parts[2].trim());
                boolean blockSearch = parts.length > 3 && Boolean.parseBoolean(parts[3].trim());
                boolean replaceAnswer = parts.length > 4 && Boolean.parseBoolean(parts[4].trim());
                sw.setCategory(String.format(
                    "{\"blockInput\":%s,\"blockSearch\":%s,\"replaceAnswer\":%s}",
                    blockInput, blockSearch, replaceAnswer));
                sensitiveWordService.save(sw);
                imported++;
            }
            return ApiResponse.success(Map.of("imported", imported));
        } catch (Exception e) {
            return ApiResponse.serverError("导入失败: " + e.getMessage());
        } finally {
            sensitiveWordService.invalidate();
        }
    }

    private String buildCategoryJson(Map<String, Object> body) {
        boolean blockInput = Boolean.TRUE.equals(body.get("blockInput"));
        boolean blockSearch = Boolean.TRUE.equals(body.get("blockSearch"));
        boolean replaceAnswer = Boolean.TRUE.equals(body.get("replaceAnswer"));
        return String.format("{\"blockInput\":%s,\"blockSearch\":%s,\"replaceAnswer\":%s}",
                blockInput, blockSearch, replaceAnswer);
    }

    private boolean parseCategoryFlag(String categoryJson, String flag) {
        if (categoryJson == null || categoryJson.isEmpty()) return false;
        return categoryJson.contains("\"" + flag + "\":true");
    }
}
