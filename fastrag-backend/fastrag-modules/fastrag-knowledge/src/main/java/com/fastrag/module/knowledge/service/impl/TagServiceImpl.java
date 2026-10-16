package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.TagService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    private final KbTagTypeMapper tagTypeMapper; private final KbTagMapper tagMapper; private final KbTagRelationMapper relationMapper; private final KbKnowledgeMapper knowledgeMapper;
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
        var tags=tagMapper.selectList(w.orderByDesc(KbTag::getCreatedAt));
        fillUsageCount(tags);
        return tags;
    }
    @Override public KbTag createTag(KbTag tag) {
        if(tag.getUsageCount()==null) tag.setUsageCount(0);
        tagMapper.insert(tag); return tag;
    }
    @Override public KbTag updateTag(String id,KbTag tag) {
        tag.setId(id); tagMapper.updateById(tag); return tagMapper.selectById(id);
    }
    @Override public void deleteTag(String id) { tagMapper.deleteById(id); relationMapper.delete(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,id)); }
    /** 使用次数实时统计（关联表行数）：列值历史遗留不维护，读取时按真实关联数覆盖，避免界面展示假数据 */
    private void fillUsageCount(List<KbTag> tags) {
        if(tags==null||tags.isEmpty()) return;
        var ids=tags.stream().map(KbTag::getId).filter(Objects::nonNull).toList();
        if(ids.isEmpty()) return;
        var rels=relationMapper.selectList(new LambdaQueryWrapper<KbTagRelation>().in(KbTagRelation::getTagId,ids));
        Map<String,Integer> counts=new HashMap<>();
        for(var r:rels) counts.merge(r.getTagId(),1,Integer::sum);
        for(var t:tags) t.setUsageCount(counts.getOrDefault(t.getId(),0));
    }
    /** 关联/取消关联后回写 usage_count 列，保持列值与关联表一致 */
    private void recountUsage(String tagId) {
        if(tagId==null) return;
        long n=relationMapper.selectCount(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,tagId));
        var t=tagMapper.selectById(tagId);
        if(t!=null){ t.setUsageCount((int)n); tagMapper.updateById(t); }
    }
    // ===== 标签关联 =====
    @Override public void linkTag(String tagId,String targetType,String targetId) {
        var w=new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,tagId).eq(KbTagRelation::getTargetType,targetType).eq(KbTagRelation::getTargetId,targetId);
        if(relationMapper.selectCount(w)==0){ var r=new KbTagRelation(); r.setTagId(tagId); r.setTargetType(targetType); r.setTargetId(targetId); relationMapper.insert(r); recountUsage(tagId); }
    }
    @Override public void unlinkTag(String tagId,String targetType,String targetId) {
        relationMapper.delete(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,tagId).eq(KbTagRelation::getTargetType,targetType).eq(KbTagRelation::getTargetId,targetId));
        recountUsage(tagId);
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
    @Override public List<Map<String,Object>> getTagLinkedKnowledge(String tagId) {
        var list=relationMapper.selectList(new LambdaQueryWrapper<KbTagRelation>().eq(KbTagRelation::getTagId,tagId).eq(KbTagRelation::getTargetType,"knowledge"));
        List<Map<String,Object>> result=new ArrayList<>();
        for(var r:list){
            var k=knowledgeMapper.selectById(r.getTargetId());
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("relationId",r.getId()); m.put("tagId",tagId); m.put("knowledgeId",r.getTargetId());
            m.put("title",k!=null?k.getTitle():null);
            m.put("category",k!=null?k.getCategory():null);
            m.put("status",k!=null?k.getStatus():null);
            m.put("updatedAt",k!=null?k.getUpdatedAt():null);
            result.add(m);
        }
        return result;
    }
}
