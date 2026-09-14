package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledge; import com.fastrag.module.knowledge.mapper.KbKnowledgeMapper;
import com.fastrag.module.knowledge.entity.KbMatterKnowledgeRel; import com.fastrag.module.knowledge.mapper.KbMatterKnowledgeRelMapper;
import com.fastrag.module.knowledge.service.MatterKnowledgeRelService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class MatterKnowledgeRelServiceImpl implements MatterKnowledgeRelService {
    private final KbMatterKnowledgeRelMapper mapper;
    private final KbKnowledgeMapper knowledgeMapper;
    @Override public List<KbMatterKnowledgeRel> list(String kbId,String matterName,String keyword) {
        var w=new LambdaQueryWrapper<KbMatterKnowledgeRel>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbMatterKnowledgeRel::getKbId,kbId);
        if(matterName!=null&&!matterName.isEmpty()) w.eq(KbMatterKnowledgeRel::getMatterName,matterName);
        if(keyword!=null&&!keyword.isEmpty()) w.and(q->q.like(KbMatterKnowledgeRel::getMatterName,keyword).or().like(KbMatterKnowledgeRel::getKnowledgeTitle,keyword));
        return mapper.selectList(w.orderByDesc(KbMatterKnowledgeRel::getCreatedAt));
    }
    @Override public KbMatterKnowledgeRel get(String id) { return mapper.selectById(id); }
    @Override public KbMatterKnowledgeRel create(String kbId,KbMatterKnowledgeRel rel) {
        rel.setId(null); rel.setKbId(kbId);
        if(rel.getRelationType()==null) rel.setRelationType("reference");
        fillKnowledgeTitle(rel);
        mapper.insert(rel); return rel;
    }
    @Override public KbMatterKnowledgeRel update(String id,KbMatterKnowledgeRel rel) {
        rel.setId(id); fillKnowledgeTitle(rel);
        mapper.updateById(rel); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    // 关联知识条目时冗余标题，便于列表展示与查看
    private void fillKnowledgeTitle(KbMatterKnowledgeRel rel) {
        if(rel.getKnowledgeId()!=null&&!rel.getKnowledgeId().isEmpty()) {
            var k=knowledgeMapper.selectById(rel.getKnowledgeId());
            if(k!=null) rel.setKnowledgeTitle(k.getTitle());
        }
    }
}
