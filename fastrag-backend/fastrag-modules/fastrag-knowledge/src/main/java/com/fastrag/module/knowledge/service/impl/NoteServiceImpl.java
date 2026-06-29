package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbNote; import com.fastrag.module.knowledge.mapper.KbNoteMapper;
import com.fastrag.module.knowledge.service.NoteService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {
    private final KbNoteMapper mapper;
    @Override public List<KbNote> list(String kbId,String keyword) {
        var w=new LambdaQueryWrapper<KbNote>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbNote::getKbId,kbId);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbNote::getTitle,keyword);
        return mapper.selectList(w.orderByDesc(KbNote::getCreatedAt));
    }
    @Override public KbNote get(String id) { return mapper.selectById(id); }
    @Override public KbNote create(KbNote note) { mapper.insert(note); return note; }
    @Override public KbNote update(String id,KbNote note) { note.setId(id); mapper.updateById(note); return mapper.selectById(id); }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public Map<String,Object> export(String kbId,String ids) {
        var all=list(kbId,null);
        if(ids!=null&&!ids.isEmpty()){
            var idSet=Arrays.asList(ids.split(","));
            all=all.stream().filter(n->idSet.contains(n.getId())).toList();
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("format","md"); result.put("count",all.size()); result.put("items",all);
        return result;
    }
}
