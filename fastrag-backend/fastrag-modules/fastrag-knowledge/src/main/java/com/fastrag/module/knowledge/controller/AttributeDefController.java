package com.fastrag.module.knowledge.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbAttributeDef;
import com.fastrag.module.knowledge.mapper.KbAttributeDefMapper;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 属性管理：知识库自定义属性定义 CRUD（属性值存于 kb_knowledge.attributes JSON） */
@RestController @RequestMapping("/api/kb/{kbId}/attributes") @RequiredArgsConstructor
public class AttributeDefController {
    private final KbAttributeDefMapper mapper;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(mapper.selectList(new LambdaQueryWrapper<KbAttributeDef>().eq(KbAttributeDef::getKbId,kbId).orderByAsc(KbAttributeDef::getCreatedAt))); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbAttributeDef def) {
        def.setKbId(kbId); if(def.getAttrType()==null)def.setAttrType("text"); if(def.getRequired()==null)def.setRequired(0);
        mapper.insert(def); return ApiResponse.success(def); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody KbAttributeDef def) {
        def.setId(id); def.setKbId(kbId); mapper.updateById(def); return ApiResponse.success(mapper.selectById(id)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { mapper.deleteById(id); return ApiResponse.success(); }
}
