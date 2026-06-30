package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.*;
import com.fastrag.module.knowledge.service.KnowledgeManageService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class KnowledgeManageController {
    private final KnowledgeManageService svc;
    // ===== M10 知识管理 =====
    @GetMapping("/knowledge") public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String keyword,@RequestParam(required=false) String category) { return ApiResponse.success(svc.list(kbId,keyword,category)); }
    @GetMapping("/knowledge/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping("/knowledge") public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbKnowledge knowledge) { knowledge.setKbId(kbId); return ApiResponse.success(svc.create(knowledge)); }
    @PutMapping("/knowledge/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbKnowledge knowledge) { return ApiResponse.success(svc.update(id,knowledge)); }
    @DeleteMapping("/knowledge/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    // 知识测试
    @GetMapping("/knowledge-tests") public ApiResponse<?> tests(@PathVariable String kbId,@RequestParam(required=false) String knowledgeId) { return ApiResponse.success(svc.listTests(kbId,knowledgeId)); }
    @PostMapping("/knowledge-tests") public ApiResponse<?> createTest(@PathVariable String kbId,@RequestBody KbKnowledgeTest test) { test.setKbId(kbId); return ApiResponse.success(svc.createTest(test)); }
    @PutMapping("/knowledge-tests/{id}") public ApiResponse<?> updateTest(@PathVariable String id,@RequestBody KbKnowledgeTest test) { return ApiResponse.success(svc.updateTest(id,test)); }
    @DeleteMapping("/knowledge-tests/{id}") public ApiResponse<?> deleteTest(@PathVariable String id) { svc.deleteTest(id); return ApiResponse.success(); }
    // 知识对话
    @GetMapping("/knowledge-dialogs") public ApiResponse<?> dialogs(@PathVariable String kbId,@RequestParam(required=false) String knowledgeId) { return ApiResponse.success(svc.listDialogs(kbId,knowledgeId)); }
    @PostMapping("/knowledge-dialogs") public ApiResponse<?> createDialog(@PathVariable String kbId,@RequestBody KbKnowledgeDialog dialog) { dialog.setKbId(kbId); return ApiResponse.success(svc.createDialog(dialog)); }
    @DeleteMapping("/knowledge-dialogs/{id}") public ApiResponse<?> deleteDialog(@PathVariable String id) { svc.deleteDialog(id); return ApiResponse.success(); }
    @PostMapping("/knowledge-dialogs/{id}/judge") public ApiResponse<?> judge(@PathVariable String id,@RequestBody Map<String,String> body) { return ApiResponse.success(svc.judge(id,body.get("query"),body.get("model"))); }
}
