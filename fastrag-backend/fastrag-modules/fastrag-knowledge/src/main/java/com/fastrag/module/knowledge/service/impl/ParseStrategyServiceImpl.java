package com.fastrag.module.knowledge.service.impl;
import cn.hutool.core.util.StrUtil; import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbParseStrategy; import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.*; import com.fastrag.module.knowledge.service.ParseStrategyService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class ParseStrategyServiceImpl implements ParseStrategyService {
    private final KbParseStrategyMapper mapper;
    @Override public List<ParseStrategyDto> list(String kbId) { return mapper.selectList(new LambdaQueryWrapper<KbParseStrategy>().eq(KbParseStrategy::getKbId,kbId)).stream().map(this::toDto).collect(Collectors.toList()); }
    @Override public ParseStrategyDto get(String kbId,String id) { return toDto(getOne(kbId,id)); }
    @Override public ParseStrategyDto create(String kbId,ParseStrategyRequest req) { var e=new KbParseStrategy(); e.setKbId(kbId); e.setName(req.getName()); e.setDescription(req.getDescription()); e.setExtensions(req.getExtensions()!=null?JSONUtil.toJsonStr(req.getExtensions()):null); e.setParseMethod(req.getParseMethod()); e.setIsDefault(0); e.setAdvanced(req.getAdvanced()!=null?JSONUtil.toJsonStr(req.getAdvanced()):null); mapper.insert(e); return toDto(e); }
    @Override public ParseStrategyDto update(String kbId,String id,ParseStrategyRequest req) { var e=getOne(kbId,id); if(req.getName()!=null)e.setName(req.getName()); if(req.getExtensions()!=null)e.setExtensions(JSONUtil.toJsonStr(req.getExtensions())); mapper.updateById(e); return toDto(e); }
    @Override public void delete(String kbId,String id) { var e=getOne(kbId,id); if(e.getIsDefault()==1)throw new RuntimeException("Cannot delete default"); mapper.deleteById(id); }
    @Override public void setDefault(String kbId,String id) { mapper.selectList(new LambdaQueryWrapper<KbParseStrategy>().eq(KbParseStrategy::getKbId,kbId).eq(KbParseStrategy::getIsDefault,1)).forEach(s->{s.setIsDefault(0);mapper.updateById(s);}); var e=getOne(kbId,id); e.setIsDefault(1); mapper.updateById(e); }
    @Override public ParseStrategyDto resolveByExtension(String kbId,String ext) { var all=mapper.selectList(new LambdaQueryWrapper<KbParseStrategy>().eq(KbParseStrategy::getKbId,kbId)); for(var s:all){if(StrUtil.isNotBlank(s.getExtensions())&&JSONUtil.toList(s.getExtensions(),String.class).contains(ext))return toDto(s);} return all.stream().filter(s->s.getIsDefault()==1).findFirst().map(this::toDto).orElse(null); }
    @Override public List<String> detectConflicts(String kbId,List<String> exts,String excludeId) { return List.of(); }
    private KbParseStrategy getOne(String kbId,String id) { var e=mapper.selectOne(new LambdaQueryWrapper<KbParseStrategy>().eq(KbParseStrategy::getKbId,kbId).eq(KbParseStrategy::getId,id)); if(e==null)throw new RuntimeException("Not found"); return e; }
    private ParseStrategyDto toDto(KbParseStrategy e) { var d=new ParseStrategyDto(); d.setId(e.getId()); d.setName(e.getName()); d.setDescription(e.getDescription()); d.setExtensions(StrUtil.isNotBlank(e.getExtensions())?JSONUtil.toList(e.getExtensions(),String.class):null); d.setParseMethod(e.getParseMethod()); d.setDefault(e.getIsDefault()==1); d.setCreatedAt(e.getCreatedAt()); d.setUpdatedAt(e.getUpdatedAt()); d.setAdvanced(StrUtil.isNotBlank(e.getAdvanced())?JSONUtil.toBean(e.getAdvanced(),Map.class):null); return d; }
}
