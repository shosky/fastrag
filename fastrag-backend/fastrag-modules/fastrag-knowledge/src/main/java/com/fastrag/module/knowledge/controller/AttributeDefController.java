package com.fastrag.module.knowledge.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.entity.KbAttributeDef;
import com.fastrag.module.knowledge.mapper.KbAttributeDefMapper;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Set;

/** 属性管理：知识库自定义属性定义 CRUD（属性值存于 kb_knowledge.attributes JSON） */
@RestController @RequestMapping("/api/kb/{kbId}/attributes") @RequiredArgsConstructor
public class AttributeDefController {
    private final KbAttributeDefMapper mapper;
    private static final Set<String> TYPES = Set.of("text","number","date","select");
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(mapper.selectList(new LambdaQueryWrapper<KbAttributeDef>().eq(KbAttributeDef::getKbId,kbId)
            .orderByAsc(KbAttributeDef::getSort).orderByAsc(KbAttributeDef::getCreatedAt))); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbAttributeDef def) {
        def.setId(null); def.setKbId(kbId);
        normalizeAndCheck(def);
        mapper.insert(def); return ApiResponse.success(def); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody KbAttributeDef def) {
        def.setId(id); def.setKbId(kbId);
        normalizeAndCheck(def);
        mapper.updateById(def); return ApiResponse.success(mapper.selectById(id)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { mapper.deleteById(id); return ApiResponse.success(); }

    /** 校验：类型白名单、名称必填且同库不重复；select 必须给出选项集 */
    private void normalizeAndCheck(KbAttributeDef def) {
        if(def.getName()==null||def.getName().isBlank()) throw BusinessException.badRequest("属性名不能为空");
        def.setName(def.getName().trim());
        if(def.getAttrType()==null||def.getAttrType().isBlank()) def.setAttrType("text");
        if(!TYPES.contains(def.getAttrType())) throw BusinessException.badRequest("属性类型仅支持 text/number/date/select");
        if(def.getRequired()==null) def.setRequired(0);
        if(def.getSort()==null) def.setSort(0);
        if("select".equals(def.getAttrType())) {
            Object opts=def.getOptions();
            if(!(opts instanceof List<?> list)||list.isEmpty()) throw BusinessException.badRequest("选项类型属性必须配置至少一个选项");
        } else {
            def.setOptions(null); // 非 select 类型不保留选项集，避免脏数据
        }
        var exist=mapper.selectCount(new LambdaQueryWrapper<KbAttributeDef>()
            .eq(KbAttributeDef::getKbId,def.getKbId()).eq(KbAttributeDef::getName,def.getName())
            .ne(def.getId()!=null,KbAttributeDef::getId,def.getId()));
        if(exist!=null&&exist>0) throw BusinessException.badRequest("属性名已存在："+def.getName());
    }
}
