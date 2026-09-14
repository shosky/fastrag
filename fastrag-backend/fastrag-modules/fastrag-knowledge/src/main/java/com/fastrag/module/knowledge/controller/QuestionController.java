package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.service.StandardQuestionService;
import com.fastrag.module.knowledge.service.SimilarQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/kb/{kbId}/questions")
@RequiredArgsConstructor
public class QuestionController {
    private final StandardQuestionService standardQuestionService;
    private final SimilarQuestionService similarQuestionService;
    @GetMapping("/standard")
    public ApiResponse<?> listStandard(@PathVariable String kbId,
                                       @RequestParam(required = false) String category) {
        return ApiResponse.success(standardQuestionService.list(kbId, category));
    }
    @GetMapping("/standard/{id}")
    public ApiResponse<?> getStandard(@PathVariable String id) {
        return ApiResponse.success(standardQuestionService.get(id));
    }
    @PostMapping("/standard")
    public ApiResponse<?> createStandard(@PathVariable String kbId,
                                         @Valid @RequestBody Map<String, Object> body) {
        var q = new com.fastrag.module.knowledge.entity.KbStandardQuestion();
        q.setCategory((String) body.get("category"));
        q.setStandardQuestion((String) body.get("standardQuestion"));
        q.setAnswer((String) body.get("answer"));
        return ApiResponse.success(standardQuestionService.create(kbId, q));
    }
    @PutMapping("/standard/{id}")
    public ApiResponse<?> updateStandard(@PathVariable String id,
                                         @RequestBody Map<String, Object> body) {
        var q = new com.fastrag.module.knowledge.entity.KbStandardQuestion();
        q.setCategory((String) body.get("category"));
        q.setStandardQuestion((String) body.get("standardQuestion"));
        q.setAnswer((String) body.get("answer"));
        return ApiResponse.success(standardQuestionService.update(id, q));
    }
    @DeleteMapping("/standard/{id}")
    public ApiResponse<?> deleteStandard(@PathVariable String id) {
        standardQuestionService.delete(id);
        return ApiResponse.success();
    }
    @GetMapping("/standard/{id}/recommend")
    public ApiResponse<?> recommendSimilar(@PathVariable String kbId,
                                           @PathVariable String id,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(standardQuestionService.recommendSimilar(kbId, id, keyword, limit));
    }
    @GetMapping("/similar")
    public ApiResponse<?> listSimilar(@PathVariable String kbId,
                                      @RequestParam(required = false) String standardQuestionId) {
        return ApiResponse.success(similarQuestionService.list(kbId, standardQuestionId));
    }
    @GetMapping("/similar/{id}")
    public ApiResponse<?> getSimilar(@PathVariable String id) {
        return ApiResponse.success(similarQuestionService.get(id));
    }
    @PostMapping("/similar")
    public ApiResponse<?> createSimilar(@PathVariable String kbId,
                                        @Valid @RequestBody Map<String, Object> body) {
        var q = new com.fastrag.module.knowledge.entity.KbSimilarQuestion();
        q.setStandardQuestionId((String) body.get("standardQuestionId"));
        q.setQuestion((String) body.get("question"));
        if (body.get("similarity") instanceof Number n) {
            q.setSimilarity(n.doubleValue());
        }
        return ApiResponse.success(similarQuestionService.create(kbId, q));
    }
    @PutMapping("/similar/{id}")
    public ApiResponse<?> updateSimilar(@PathVariable String id,
                                        @RequestBody Map<String, Object> body) {
        var q = new com.fastrag.module.knowledge.entity.KbSimilarQuestion();
        q.setStandardQuestionId((String) body.get("standardQuestionId"));
        q.setQuestion((String) body.get("question"));
        if (body.get("similarity") instanceof Number n) {
            q.setSimilarity(n.doubleValue());
        }
        return ApiResponse.success(similarQuestionService.update(id, q));
    }
    @DeleteMapping("/similar/{id}")
    public ApiResponse<?> deleteSimilar(@PathVariable String id) {
        similarQuestionService.delete(id);
        return ApiResponse.success();
    }
}
