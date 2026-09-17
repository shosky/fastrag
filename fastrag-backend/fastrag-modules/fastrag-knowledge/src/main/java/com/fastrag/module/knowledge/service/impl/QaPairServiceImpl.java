package com.fastrag.module.knowledge.service.impl;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledge;
import com.fastrag.module.knowledge.entity.KbQaPair; import com.fastrag.module.knowledge.mapper.KbKnowledgeMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.model.*; import com.fastrag.module.knowledge.service.QaPairService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class QaPairServiceImpl implements QaPairService {
    private final KbQaPairMapper mapper;
    private final KbKnowledgeMapper knowledgeMapper;
    @Override public List<QaPairDto> list(String kbId,String faqType,String keyword) {
        var w=new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId,kbId);
        if(faqType!=null&&!faqType.isEmpty()) w.eq(KbQaPair::getFaqType,faqType);
        if(keyword!=null&&!keyword.isEmpty()) w.and(q->q.like(KbQaPair::getQuestion,keyword).or().like(KbQaPair::getKeywords,keyword));
        return mapper.selectList(w.orderByDesc(KbQaPair::getCreatedAt)).stream().map(this::toDto).collect(Collectors.toList()); }
    @Override public QaPairDto create(String kbId,QaCreateRequest req) {
        var e=new KbQaPair(); e.setKbId(kbId); e.setQuestion(req.getQuestion()); e.setAnswer(req.getAnswer());
        e.setSource(req.getSource()!=null?req.getSource():"manual"); e.setFileId(req.getFileId()); e.setStatus("draft");
        applyFaqFields(e,req.getFaqType(),req.getKeywords(),req.getEffectiveStart(),req.getEffectiveEnd(),req.getEffectiveScope(),req.getRelatedKnowledgeIds());
        mapper.insert(e); return toDto(e); }
    @Override public QaPairDto update(String kbId,String id,Map<String,Object> p) {
        var e=mapper.selectById(id); if(e==null)throw new RuntimeException("Not found");
        if(p.containsKey("question"))e.setQuestion((String)p.get("question"));
        if(p.containsKey("answer"))e.setAnswer((String)p.get("answer"));
        applyFaqFields(e,(String)p.get("faqType"),(String)p.get("keywords"),toTime(p.get("effectiveStart")),toTime(p.get("effectiveEnd")),(String)p.get("effectiveScope"),p.get("relatedKnowledgeIds"));
        mapper.updateById(e); return toDto(e); }
    @Override public void delete(String kbId,String id) { mapper.deleteById(id); }
    @Override public void confirm(String kbId,String id) { var e=mapper.selectById(id); if(e!=null){e.setStatus("confirmed");mapper.updateById(e);} }
    @Override public int confirmAll(String kbId) {
        var pending=mapper.selectList(new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId,kbId).eq(KbQaPair::getStatus,"draft"));
        for(var e:pending){ e.setStatus("confirmed"); mapper.updateById(e); }
        return pending.size(); }
    @Override public List<QaPairDto> extractQa(String kbId,List<String> fileIds) { if(fileIds==null||fileIds.isEmpty())return List.of(); return mapper.selectList(new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId,kbId).in(KbQaPair::getFileId,fileIds)).stream().map(this::toDto).collect(Collectors.toList()); }
    /** 按应答添加知识：把答案固化为知识条目（标题=问题，正文=答案），并回写关联 */
    @Override public QaPairDto toKnowledge(String kbId,String id) {
        var e=mapper.selectById(id); if(e==null)throw new RuntimeException("Not found");
        var k=new KbKnowledge(); k.setKbId(kbId); k.setTitle(e.getQuestion()); k.setContent(e.getAnswer());
        k.setSummary(e.getAnswer()==null||e.getAnswer().length()<=100?e.getAnswer():e.getAnswer().substring(0,100));
        k.setCategory("FAQ"); k.setSource("qa_pair"); k.setSourceId(e.getId()); k.setStatus("published"); k.setVersion(1);
        knowledgeMapper.insert(k);
        List<String> rel=e.getRelatedKnowledgeIds()==null||e.getRelatedKnowledgeIds().isBlank()?new ArrayList<>():new ArrayList<>(JSONUtil.toList(e.getRelatedKnowledgeIds(),String.class));
        rel.add(k.getId()); e.setRelatedKnowledgeIds(JSONUtil.toJsonStr(rel)); mapper.updateById(e);
        return toDto(e); }
    private void applyFaqFields(KbQaPair e,String faqType,String keywords,LocalDateTime start,LocalDateTime end,String scope,Object relatedIds) {
        if(faqType!=null) e.setFaqType(faqType);
        if(keywords!=null) e.setKeywords(keywords);
        if(start!=null) e.setEffectiveStart(start);
        if(end!=null) e.setEffectiveEnd(end);
        if(scope!=null) e.setEffectiveScope(scope);
        if(relatedIds!=null) e.setRelatedKnowledgeIds(relatedIds instanceof CharSequence?relatedIds.toString():JSONUtil.toJsonStr(relatedIds)); }
    private LocalDateTime toTime(Object v) { if(v==null)return null; if(v instanceof LocalDateTime t)return t; try{return LocalDateTime.parse(v.toString().replace(" ","T"));}catch(Exception e){return null;} }
    private QaPairDto toDto(KbQaPair e) {
        var d=new QaPairDto(); d.setId(e.getId()); d.setKbId(e.getKbId()); d.setFileId(e.getFileId()); d.setFileName(e.getFileName());
        d.setQuestion(e.getQuestion()); d.setAnswer(e.getAnswer()); d.setSource(e.getSource()); d.setStatus(e.getStatus()); d.setCreatedAt(e.getCreatedAt());
        d.setFaqType(e.getFaqType()==null?"common":e.getFaqType()); d.setKeywords(e.getKeywords()); d.setEffectiveScope(e.getEffectiveScope());
        d.setEffectiveStart(e.getEffectiveStart()); d.setEffectiveEnd(e.getEffectiveEnd());
        d.setRelatedKnowledgeIds(e.getRelatedKnowledgeIds()==null||e.getRelatedKnowledgeIds().isBlank()?List.of():JSONUtil.toList(e.getRelatedKnowledgeIds(),String.class));
        LocalDateTime now=LocalDateTime.now();
        d.setActive((e.getEffectiveStart()==null||!now.isBefore(e.getEffectiveStart()))&&(e.getEffectiveEnd()==null||!now.isAfter(e.getEffectiveEnd())));
        return d; }
}
