package com.fastrag.module.knowledge.service.impl;
import cn.hutool.core.util.StrUtil; import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.module.knowledge.entity.KnowledgeBase; import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.*; import com.fastrag.module.knowledge.service.KbService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class KbServiceImpl implements KbService {
    private final KnowledgeBaseMapper mapper;
    @Override public Map<String,Object> list(String kw,String cat,int page,int pageSize) {
        var w=new LambdaQueryWrapper<KnowledgeBase>();
        if(StrUtil.isNotBlank(kw)) w.like(KnowledgeBase::getName,kw).or().like(KnowledgeBase::getDescription,kw);
        if(StrUtil.isNotBlank(cat)) w.eq(KnowledgeBase::getCategory,cat);
        w.orderByDesc(KnowledgeBase::getCreatedAt);
        var r=mapper.selectPage(new Page<>(page,pageSize),w);
        var result=new HashMap<String,Object>();
        result.put("list",r.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        result.put("total",r.getTotal()); result.put("page",page); result.put("pageSize",pageSize);
        return result;
    }
    @Override public KbDto get(String id) { var e=mapper.selectById(id); if(e==null)throw new RuntimeException("KB not found: "+id); return toDto(e); }
    @Override public KbDto create(KbCreateRequest req,String creator) {
        var e=new KnowledgeBase(); e.setName(req.getName()); e.setCategory(req.getCategory()); e.setDescription(req.getDescription());
        e.setTags(req.getTags()!=null?JSONUtil.toJsonStr(req.getTags()):null); e.setPermission(req.getPermission()!=null?req.getPermission():"private");
        e.setEmbeddingModel(req.getEmbeddingModel()); e.setParseMode(req.getParseMode()); e.setSplitMode(req.getSplitMode());
        e.setFileTypeConfig(req.getFileTypeConfig()!=null?JSONUtil.toJsonStr(req.getFileTypeConfig()):null);
        e.setRetrievalConfig(req.getRetrievalConfig()!=null?JSONUtil.toJsonStr(req.getRetrievalConfig()):null);
        e.setCreator(creator); e.setUsedSize(0L); e.setTotalSize(0L); e.setType("personal"); mapper.insert(e); return toDto(e);
    }
    @Override public KbDto update(String id,KbCreateRequest req) {
        var e=mapper.selectById(id); if(e==null)throw new RuntimeException("KB not found: "+id);
        if(req.getName()!=null)e.setName(req.getName()); if(req.getCategory()!=null)e.setCategory(req.getCategory());
        if(req.getDescription()!=null)e.setDescription(req.getDescription());
        if(req.getTags()!=null)e.setTags(JSONUtil.toJsonStr(req.getTags()));
        mapper.updateById(e); return toDto(e);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public List<Map<String,Object>> getCategories() {
        var all=mapper.selectList(null);
        var grouped=all.stream().filter(e->StrUtil.isNotBlank(e.getCategory())).collect(Collectors.groupingBy(KnowledgeBase::getCategory,Collectors.counting()));
        var result=new ArrayList<Map<String,Object>>();
        grouped.forEach((k,v)->{ var m=new HashMap<String,Object>(); m.put("name",k); m.put("count",v); result.add(m); });
        return result;
    }
    private KbDto toDto(KnowledgeBase e) { var d=new KbDto(); d.setId(e.getId()); d.setName(e.getName()); d.setDescription(e.getDescription()); d.setCategory(e.getCategory()); d.setTags(StrUtil.isNotBlank(e.getTags())?JSONUtil.toList(e.getTags(),String.class):null); d.setEmbeddingModel(e.getEmbeddingModel()); d.setDimension(e.getDimension()); d.setCreator(e.getCreator()); d.setCreatedAt(e.getCreatedAt()); d.setUsedSize(e.getUsedSize()); d.setTotalSize(e.getTotalSize()); d.setType(e.getType()); return d; }
}
