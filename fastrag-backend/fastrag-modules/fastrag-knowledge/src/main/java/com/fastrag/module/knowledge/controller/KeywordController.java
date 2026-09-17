package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.service.StandardQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/kb/{kbId}/keywords")
@RequiredArgsConstructor
public class KeywordController {
    private final StandardQuestionService standardQuestionService;
    private final com.fastrag.module.knowledge.service.QaPairService qaPairService;
    @GetMapping("/recommend")
    public ApiResponse<?> recommend(@PathVariable String kbId,
                                    @RequestParam String query,
                                    @RequestParam(defaultValue = "10") int limit) {
        if (query == null || query.isBlank()) return ApiResponse.success(Collections.emptyList());
        String q = query.trim();
        // 从标准问法中匹配
        List<Map<String, Object>> stdList = new ArrayList<>();
        for (var s : standardQuestionService.list(kbId, null)) {
            if (s.getStandardQuestion() != null && s.getStandardQuestion().contains(q)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "standard");
                m.put("id", s.getId());
                m.put("text", s.getStandardQuestion());
                m.put("score", 0.9);
                stdList.add(m);
                if (stdList.size() >= limit) break;
            }
        }
        // 从相似问法中匹配
        List<Map<String, Object>> simList = new ArrayList<>();
        for (var std : standardQuestionService.list(kbId, null)) {
            if (std.getStandardQuestion() == null || !std.getStandardQuestion().contains(q)) continue;
            for (var m : standardQuestionService.recommendSimilar(kbId, std.getId(), q, limit / 2)) {
                m.put("type", "similar");
                simList.add(m);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(stdList);
        result.addAll(simList);
        // 去重并按 score 降序
        result.sort((a, b) -> Double.compare(
                (Double) b.getOrDefault("score", 0.5),
                (Double) a.getOrDefault("score", 0.5)
        ));
        List<Map<String, Object>> finalList = result.stream().limit(limit).toList();
        return ApiResponse.success(finalList);
    }

    /** 关键词推荐判断：判定 query 是否命中已配置关键词/标准问法（管理端-关键词推荐-判断） */
    @PostMapping("/judge")
    public ApiResponse<?> judge(@PathVariable String kbId, @RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : body.get("query").toString().trim();
        Map<String, Object> result = new LinkedHashMap<>();
        if (query.isEmpty()) { result.put("matched", false); result.put("keywords", List.of()); return ApiResponse.success(result); }
        List<Map<String, Object>> hits = new ArrayList<>();
        for (var std : standardQuestionService.list(kbId, null)) {
            if (std.getStandardQuestion() != null && (std.getStandardQuestion().contains(query) || query.contains(std.getStandardQuestion()))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("source", "standard"); m.put("id", std.getId()); m.put("text", std.getStandardQuestion()); m.put("score", 0.9);
                hits.add(m);
            }
        }
        for (var qa : qaPairService.list(kbId, null, null)) {
            if (qa.getKeywords() == null) continue;
            for (String kw : qa.getKeywords().split("[,，]")) {
                kw = kw.trim();
                if (!kw.isEmpty() && (query.contains(kw) || kw.contains(query))) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("source", "qa_keyword"); m.put("id", qa.getId()); m.put("text", kw); m.put("score", 0.8);
                    hits.add(m);
                }
            }
        }
        result.put("matched", !hits.isEmpty());
        result.put("keywords", hits.size() > 10 ? hits.subList(0, 10) : hits);
        result.put("query", query);
        return ApiResponse.success(result);
    }
}
