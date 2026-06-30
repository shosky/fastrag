package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbTag; import com.fastrag.module.knowledge.entity.KbTagType; import java.util.*;
public interface TagService {
    // 标签类型
    List<KbTagType> listTagTypes(String kbId);
    KbTagType getTagType(String id);
    KbTagType createTagType(KbTagType tagType);
    KbTagType updateTagType(String id,KbTagType tagType);
    void deleteTagType(String id);
    // 标签
    List<KbTag> listTags(String kbId,String tagTypeId,String keyword);
    KbTag createTag(KbTag tag);
    KbTag updateTag(String id,KbTag tag);
    void deleteTag(String id);
    // 标签关联
    void linkTag(String tagId,String targetType,String targetId);
    void unlinkTag(String tagId,String targetType,String targetId);
    List<Map<String,Object>> getRelations(String targetType,String targetId);
    // 查询标签关联的知识列表（按 tagId + targetType 过滤）
    List<Map<String,Object>> getTagLinkedKnowledge(String tagId);
}
