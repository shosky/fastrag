package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledgeEdit; import com.fastrag.module.knowledge.mapper.KbKnowledgeEditMapper;
import com.fastrag.module.knowledge.service.KnowledgeEditService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeEditServiceImpl implements KnowledgeEditService {
    private final KbKnowledgeEditMapper mapper;
    @Override public List<KbKnowledgeEdit> list(String kbId,String status,String editor) {
        var w=new LambdaQueryWrapper<KbKnowledgeEdit>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeEdit::getKbId,kbId);
        if(status!=null&&!status.isEmpty()) w.eq(KbKnowledgeEdit::getStatus,status);
        if(editor!=null&&!editor.isEmpty()) w.eq(KbKnowledgeEdit::getEditor,editor);
        return mapper.selectList(w.orderByDesc(KbKnowledgeEdit::getCreatedAt));
    }
    @Override public KbKnowledgeEdit get(String id) { return mapper.selectById(id); }
    @Override public KbKnowledgeEdit create(KbKnowledgeEdit edit) {
        if(edit.getStatus()==null) edit.setStatus("draft");
        if(edit.getVersion()==null) edit.setVersion(1);
        if(edit.getEditType()==null) edit.setEditType("create");
        mapper.insert(edit); return edit;
    }
    @Override public KbKnowledgeEdit update(String id,KbKnowledgeEdit edit) {
        edit.setId(id); mapper.updateById(edit); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public KbKnowledgeEdit submit(String id) {
        var e=mapper.selectById(id);
        if(e!=null){ e.setStatus("submitted"); mapper.updateById(e); }
        return e;
    }
    @Override public KbKnowledgeEdit approve(String id,String reviewer) {
        var e=mapper.selectById(id);
        if(e!=null){ e.setStatus("approved"); e.setReviewer(reviewer); mapper.updateById(e); }
        return e;
    }
    @Override public KbKnowledgeEdit reject(String id,String reviewer,String comment) {
        var e=mapper.selectById(id);
        if(e!=null){ e.setStatus("rejected"); e.setReviewer(reviewer); e.setReviewComment(comment); mapper.updateById(e); }
        return e;
    }
    @Override public Map<String,Object> export(String kbId,String ids,String status,String editor) {
        var all=list(kbId,status,editor);
        if(ids!=null&&!ids.isEmpty()){
            var idSet=Arrays.asList(ids.split(","));
            all=all.stream().filter(e->idSet.contains(e.getId())).toList();
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("format","json");
        result.put("count",all.size());
        result.put("items",all);
        return result;
    }
}
