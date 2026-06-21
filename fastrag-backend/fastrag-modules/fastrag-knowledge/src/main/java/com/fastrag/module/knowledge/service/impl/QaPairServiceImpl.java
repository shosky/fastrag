package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbQaPair; import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.model.*; import com.fastrag.module.knowledge.service.QaPairService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class QaPairServiceImpl implements QaPairService {
    private final KbQaPairMapper mapper;
    @Override public List<QaPairDto> list(String kbId) { return mapper.selectList(new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId,kbId).orderByDesc(KbQaPair::getCreatedAt)).stream().map(this::toDto).collect(Collectors.toList()); }
    @Override public QaPairDto create(String kbId,QaCreateRequest req) { var e=new KbQaPair(); e.setKbId(kbId); e.setQuestion(req.getQuestion()); e.setAnswer(req.getAnswer()); e.setSource(req.getSource()!=null?req.getSource():"manual"); e.setFileId(req.getFileId()); e.setStatus("draft"); mapper.insert(e); return toDto(e); }
    @Override public QaPairDto update(String kbId,String id,Map<String,Object> p) { var e=mapper.selectById(id); if(e==null)throw new RuntimeException("Not found"); if(p.containsKey("question"))e.setQuestion((String)p.get("question")); if(p.containsKey("answer"))e.setAnswer((String)p.get("answer")); mapper.updateById(e); return toDto(e); }
    @Override public void delete(String kbId,String id) { mapper.deleteById(id); }
    @Override public void confirm(String kbId,String id) { var e=mapper.selectById(id); if(e!=null){e.setStatus("confirmed");mapper.updateById(e);} }
    @Override public List<QaPairDto> extractQa(String kbId,List<String> fileIds) { if(fileIds==null||fileIds.isEmpty())return List.of(); return mapper.selectList(new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId,kbId).in(KbQaPair::getFileId,fileIds)).stream().map(this::toDto).collect(Collectors.toList()); }
    private QaPairDto toDto(KbQaPair e) { var d=new QaPairDto(); d.setId(e.getId()); d.setKbId(e.getKbId()); d.setFileId(e.getFileId()); d.setFileName(e.getFileName()); d.setQuestion(e.getQuestion()); d.setAnswer(e.getAnswer()); d.setSource(e.getSource()); d.setStatus(e.getStatus()); d.setCreatedAt(e.getCreatedAt()); return d; }
}
