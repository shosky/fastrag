package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.TagService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    private final KbTagTypeMapper tagTypeMapper; private final KbTagMapper tagMapper; private final KbTagRelationMapper relationMapper;
    // ===== 标签类型 =====
    @Override public List<KbTagType> listTagTypes(String kbId) {
        var w=new LambdaQueryWrapper<KbTagType>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbTagType::getKbId,kbId);
        return tagTypeMapper.selectList(w.orderByAsc(KbTagType::getSort));
    }
    @Override public KbTagType getTagType(String id) { return tagTypeMapper.selectById(id); }
    @Override public KbTagType createTagType(KbTagType tagType) {
        if(tagType.getIsSystem()==null) tagType.setIsSystem(0);
        if(tagType.getSort()==null) tagType.setSort(0);
        tagTypeMapper.insert(tagType); return tagType;
    }
    @Override public KbTagType updateTagType(String id,KbTagType tagType) {
        tagType.setId(id); tagTypeMapper.updateById(tagType); return tagTypeMapper.selectById(id);
    }
    @Override public void deleteTagType(String id) {
        tagTypeMapper.deleteById(id);
        // 其下标签的 tag_type_id 置空
        var tags=tagMapper.selectList(new LambdaQueryWrapper<KbTag>().eq(KbTag::getTagTypeId,id));
        for(var t:tags){ t.setTagTypeId(null); tagMapper.updateById(t); }
    }
    // ===== 标签 =====
    @Override public List<KbTag> listTags(String kbId,String tagTypeId,String keyword) {
        var w=new LambdaQueryWrapper<KbTag>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbTag::getKbId,kbId);
        if(tagTypeId!=null&&!tagTypeId.isEmpty()) w.eq(KbTag::getTagTypeId,tagTypeId);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbTag::getName,keyword);
        return tagMapper.selectList(w.orderByDesc(KbTag::getCreatedAt));
    }
    @Override public KbTag createTag(KbTag tag) {
        if(tag.getUsageCount()==null) tag.setUsageCount(0);
        tagMapper.insert(tag); return tag;
    }
    @Override public KbTag updateTag(String id,KbTag tag) {
        tag.setId(id); tagMapper.updateById(tag); return tagMapper.selectById(id);
    }
    @Override public void deleteTag(String id) { tagMapper.deleteById(id); relationMapper.delete(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,id)); }
    // ===== 标签关联 =====
    @Override public void linkTag(String tagId,String targetType,String targetId) {
        var w=new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,tagId).eq(KbTagRelation::getTargetType,targetType).eq(KbTagRelation::getTargetId,targetId);
        if(relationMapper.selectCount(w)==0){ var r=new KbTagRelation(); r.setTagId(tagId); r.setTargetType(targetType); r.setTargetId(targetId); relationMapper.insert(r); }
    }
    @Override public void unlinkTag(String tagId,String targetType,String targetId) {
        relationMapper.delete(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,tagId).eq(KbTagRelation::getTargetType,targetType).eq(KbTagRelation::getTargetId,targetId));
    }
    @Override public List<Map<String,Object>> getRelations(String targetType,String targetId) {
        var list=relationMapper.selectList(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTargetType,targetType).eq(KbTagRelation::getTargetId,targetId));
        List<Map<String,Object>> result=new ArrayList<>();
        for(var r:list){
            var tag=tagMapper.selectById(r.getTagId());
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("relationId",r.getId()); m.put("tagId",r.getTagId()); m.put("tagName",tag!=null?tag.getName():null); m.put("color",tag!=null?tag.getColor():null);
            result.add(m);
        }
        return result;
    }
}
