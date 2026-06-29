package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.*;
import com.fastrag.module.knowledge.service.KnowledgeQaService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class KnowledgeQaController {
    private final KnowledgeQaService svc;
    // 多轮问答
    @GetMapping("/multi-turn-qa") public ApiResponse<?> mtList(@PathVariable String kbId) { return ApiResponse.success(svc.listMultiTurnQa(kbId)); }
    @GetMapping("/multi-turn-qa/{id}") public ApiResponse<?> mtGet(@PathVariable String id) { return ApiResponse.success(svc.getMultiTurnQa(id)); }
    @PostMapping("/multi-turn-qa") public ApiResponse<?> mtCreate(@PathVariable String kbId,@RequestBody KbMultiTurnQa qa) { qa.setKbId(kbId); return ApiResponse.success(svc.createMultiTurnQa(qa)); }
    @PutMapping("/multi-turn-qa/{id}") public ApiResponse<?> mtUpdate(@PathVariable String id,@RequestBody KbMultiTurnQa qa) { return ApiResponse.success(svc.updateMultiTurnQa(id,qa)); }
    @DeleteMapping("/multi-turn-qa/{id}") public ApiResponse<?> mtDelete(@PathVariable String id) { svc.deleteMultiTurnQa(id); return ApiResponse.success(); }
    // 多模态问答
    @GetMapping("/multimodal-qa") public ApiResponse<?> mmList(@PathVariable String kbId) { return ApiResponse.success(svc.listMultimodalQa(kbId)); }
    @GetMapping("/multimodal-qa/{id}") public ApiResponse<?> mmGet(@PathVariable String id) { return ApiResponse.success(svc.getMultimodalQa(id)); }
    @PostMapping("/multimodal-qa") public ApiResponse<?> mmCreate(@PathVariable String kbId,@RequestBody KbMultimodalQa qa) { qa.setKbId(kbId); return ApiResponse.success(svc.createMultimodalQa(qa)); }
    @PutMapping("/multimodal-qa/{id}") public ApiResponse<?> mmUpdate(@PathVariable String id,@RequestBody KbMultimodalQa qa) { return ApiResponse.success(svc.updateMultimodalQa(id,qa)); }
    @DeleteMapping("/multimodal-qa/{id}") public ApiResponse<?> mmDelete(@PathVariable String id) { svc.deleteMultimodalQa(id); return ApiResponse.success(); }
    // 文档导读
    @GetMapping("/doc-guides") public ApiResponse<?> dgList(@PathVariable String kbId) { return ApiResponse.success(svc.listDocGuides(kbId)); }
    @GetMapping("/doc-guides/{id}") public ApiResponse<?> dgGet(@PathVariable String id) { return ApiResponse.success(svc.getDocGuide(id)); }
    @PostMapping("/doc-guides") public ApiResponse<?> dgCreate(@PathVariable String kbId,@RequestBody KbDocGuide guide) { guide.setKbId(kbId); return ApiResponse.success(svc.createDocGuide(guide)); }
    @PutMapping("/doc-guides/{id}") public ApiResponse<?> dgUpdate(@PathVariable String id,@RequestBody KbDocGuide guide) { return ApiResponse.success(svc.updateDocGuide(id,guide)); }
    @DeleteMapping("/doc-guides/{id}") public ApiResponse<?> dgDelete(@PathVariable String id) { svc.deleteDocGuide(id); return ApiResponse.success(); }
    @PostMapping("/doc-guides/{id}/index") public ApiResponse<?> dgIndex(@PathVariable String id) { return ApiResponse.success(svc.indexDocGuide(id)); }
    // 多模态检索
    @PostMapping("/multimodal-retrieval/{modality}/search") public ApiResponse<?> mrSearch(@PathVariable String kbId,@PathVariable String modality,@RequestBody Map<String,Object> body) {
        return ApiResponse.success(svc.multimodalSearch(kbId,modality,(String)body.get("query"),body.get("topK") instanceof Number?((Number)body.get("topK")).intValue():10));
    }
    @PostMapping("/multimodal-retrieval/{modality}/sort") public ApiResponse<?> mrSort(@PathVariable String kbId,@PathVariable String modality,@RequestBody Map<String,Object> body) {
        @SuppressWarnings("unchecked") var ids=(List<String>)body.get("documentIds");
        return ApiResponse.success(svc.multimodalSort(kbId,modality,ids!=null?ids:List.of(),(String)body.getOrDefault("sortBy","relevance")));
    }
}
