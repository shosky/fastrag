package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbFolder; import com.fastrag.module.knowledge.mapper.KbFolderMapper;
import com.fastrag.module.knowledge.model.FolderNodeDto; import com.fastrag.module.knowledge.service.FolderService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*; import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {
    private final KbFolderMapper mapper;
    @Override public List<FolderNodeDto> list(String kbId) { return buildTree(mapper.selectList(new LambdaQueryWrapper<KbFolder>().eq(KbFolder::getKbId,kbId).orderByAsc(KbFolder::getSort)),null); }
    @Override public FolderNodeDto create(String kbId,String name,String parentId) { var f=new KbFolder(); f.setKbId(kbId); f.setName(name); f.setParentId(parentId); f.setSort(0); mapper.insert(f); var d=new FolderNodeDto(); d.setId(f.getId()); d.setLabel(f.getName()); return d; }
    @Override public String getName(String kbId,String folderId) { var f=mapper.selectOne(new LambdaQueryWrapper<KbFolder>().eq(KbFolder::getKbId,kbId).eq(KbFolder::getId,folderId)); return f!=null?f.getName():""; }
    private List<FolderNodeDto> buildTree(List<KbFolder> all,String pid) { return all.stream().filter(f->pid==null?f.getParentId()==null:pid.equals(f.getParentId())).map(f->{var n=new FolderNodeDto();n.setId(f.getId());n.setLabel(f.getName());n.setChildren(buildTree(all,f.getId()));return n;}).collect(Collectors.toList()); }
}
