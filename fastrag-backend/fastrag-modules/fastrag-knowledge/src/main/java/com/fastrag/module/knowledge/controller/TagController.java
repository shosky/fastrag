package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbTag;
import com.fastrag.module.knowledge.entity.KbTagType;
import com.fastrag.module.knowledge.service.TagService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class TagController {
    private final TagService svc;
    // ===== 标签类型 =====
    @GetMapping("/tag-types") public ApiResponse<?> tagTypes(@PathVariable String kbId) { return ApiResponse.success(svc.listTagTypes(kbId)); }
    @GetMapping("/tag-types/{id}") public ApiResponse<?> tagType(@PathVariable String id) { return ApiResponse.success(svc.getTagType(id)); }
    @PostMapping("/tag-types") public ApiResponse<?> createTagType(@PathVariable String kbId,@RequestBody KbTagType tagType) { tagType.setKbId(kbId); return ApiResponse.success(svc.createTagType(tagType)); }
    @PutMapping("/tag-types/{id}") public ApiResponse<?> updateTagType(@PathVariable String id,@RequestBody KbTagType tagType) { return ApiResponse.success(svc.updateTagType(id,tagType)); }
    @DeleteMapping("/tag-types/{id}") public ApiResponse<?> deleteTagType(@PathVariable String id) { svc.deleteTagType(id); return ApiResponse.success(); }
    // ===== 标签 =====
    @GetMapping("/tags") public ApiResponse<?> tags(@PathVariable String kbId,@RequestParam(required=false) String tagTypeId,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.listTags(kbId,tagTypeId,keyword)); }
    @PostMapping("/tags") public ApiResponse<?> createTag(@PathVariable String kbId,@RequestBody KbTag tag) { tag.setKbId(kbId); return ApiResponse.success(svc.createTag(tag)); }
    @PutMapping("/tags/{id}") public ApiResponse<?> updateTag(@PathVariable String id,@RequestBody KbTag tag) { return ApiResponse.success(svc.updateTag(id,tag)); }
    @DeleteMapping("/tags/{id}") public ApiResponse<?> deleteTag(@PathVariable String id) { svc.deleteTag(id); return ApiResponse.success(); }
    // ===== 标签关联 =====
    @PostMapping("/tags/{id}/relations") public ApiResponse<?> linkTag(@PathVariable String id,@RequestBody Map<String,String> body) { svc.linkTag(id,body.get("targetType"),body.get("targetId")); return ApiResponse.success(); }
    @DeleteMapping("/tags/{id}/relations/{targetId}") public ApiResponse<?> unlinkTag(@PathVariable String id,@PathVariable String targetId,@RequestParam String targetType) { svc.unlinkTag(id,targetType,targetId); return ApiResponse.success(); }
    @GetMapping("/tag-relations") public ApiResponse<?> relations(@RequestParam String targetType,@RequestParam String targetId) { return ApiResponse.success(svc.getRelations(targetType,targetId)); }
}
