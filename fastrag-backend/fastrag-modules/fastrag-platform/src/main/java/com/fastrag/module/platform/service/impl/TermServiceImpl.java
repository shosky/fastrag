package com.fastrag.module.platform.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.*; import com.fastrag.module.platform.mapper.*;
import com.fastrag.module.platform.service.TermService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class TermServiceImpl implements TermService {
    private final TermLibraryMapper libMapper; private final TermRecordMapper termMapper;
    @Override public List<TermLibrary> listLibraries() { return libMapper.selectList(null); }
    @Override public TermLibrary createLibrary(Map<String,Object> f) { var l=new TermLibrary(); l.setName((String)f.get("name")); l.setDescription((String)f.get("description")); libMapper.insert(l); return l; }
    @Override public void deleteLibrary(String id) { libMapper.deleteById(id); }
    @Override public List<TermRecord> listTerms(String libId) { return termMapper.selectList(new LambdaQueryWrapper<TermRecord>().eq(libId!=null,TermRecord::getLibraryId,libId)); }
    @Override public TermRecord createTerm(Map<String,Object> f) { var t=new TermRecord(); t.setTerm((String)f.get("term")); t.setAlias((String)f.get("alias")); t.setLibraryId((String)f.get("libraryId")); termMapper.insert(t); return t; }
    @Override public void deleteTerm(String id) { termMapper.deleteById(id); }
}
