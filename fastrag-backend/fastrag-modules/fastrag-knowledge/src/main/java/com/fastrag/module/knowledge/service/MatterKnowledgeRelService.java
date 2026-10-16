package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbMatterKnowledgeRel; import java.util.*;
public interface MatterKnowledgeRelService {
    List<KbMatterKnowledgeRel> list(String kbId,String matterName,String keyword);
    KbMatterKnowledgeRel get(String id);
    KbMatterKnowledgeRel create(String kbId,KbMatterKnowledgeRel rel);
    KbMatterKnowledgeRel update(String id,KbMatterKnowledgeRel rel);
    void delete(String id);
    /** 事项列表（去重，供座席端工单受理下拉；含每事项关联知识数） */
    List<Map<String,Object>> matters(String kbId);
    /** 事项热点知识：窗口内（默认 30 天）命中次数最高的前 N 个（默认 5），含知识链接；窗口与数量可在系统配置调整 */
    Map<String,Object> hotKnowledge(String kbId,String matterCode,Integer days,Integer top);
    /** 记录一次命中（座席查看/点开知识、按事项建单），作为热度数据源 */
    int recordHit(String kbId,String matterCode,String matterName,String knowledgeId,String hitType,String userId);
}
