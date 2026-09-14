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
}
