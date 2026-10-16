package com.fastrag.module.knowledge.controller;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbMatterKnowledgeRel;
import com.fastrag.module.knowledge.service.MatterKnowledgeRelService;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
// 事项知识关联管理：新增/查看/编辑/删除 + 事项热点推荐（座席端按事项取热度最高前 N 个知识点）
@RestController @RequestMapping("/api/kb/{kbId}/matter-knowledge-rels") @RequiredArgsConstructor
public class MatterKnowledgeRelController {
    private final MatterKnowledgeRelService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String matterName,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,matterName,keyword)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbMatterKnowledgeRel rel) { return ApiResponse.success(svc.create(kbId,rel)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbMatterKnowledgeRel rel) { return ApiResponse.success(svc.update(id,rel)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    // 事项列表（座席端下拉）
    @GetMapping("/matters") public ApiResponse<?> matters(@PathVariable String kbId) { return ApiResponse.success(svc.matters(kbId)); }
    // 事项热点知识：30 天(可配置)内热度最高的前 5 个(可配置)，带知识点链接；走知识库 ACL 鉴权
    @KbAuth(KBRole.viewer) @GetMapping("/matters/{matterCode}/hot-knowledge")
    public ApiResponse<?> hotKnowledge(@PathVariable String kbId,@PathVariable String matterCode,
        @RequestParam(required=false) Integer days,@RequestParam(required=false) Integer top) {
        return ApiResponse.success(svc.hotKnowledge(kbId,matterCode,days,top));
    }
    // 命中埋点：座席查看/点开知识点（hitType=click）、按事项建单（hitType=ticket），作为热度数据源
    @KbAuth(KBRole.viewer) @PostMapping("/matters/{matterCode}/hits")
    public ApiResponse<?> recordHit(@PathVariable String kbId,@PathVariable String matterCode,@RequestBody Map<String,String> body) {
        String userId = SecurityUtil.getCurrentUserId();
        int n = svc.recordHit(kbId,matterCode,body.get("matterName"),body.get("knowledgeId"),body.get("hitType"),userId);
        return ApiResponse.success(Map.of("recorded",n));
    }
}
