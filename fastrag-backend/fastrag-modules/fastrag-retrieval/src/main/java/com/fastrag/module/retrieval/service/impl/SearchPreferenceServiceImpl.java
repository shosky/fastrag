package com.fastrag.module.retrieval.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.retrieval.entity.KbSearchPreference; import com.fastrag.module.retrieval.mapper.KbSearchPreferenceMapper;
import com.fastrag.module.retrieval.service.SearchPreferenceService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class SearchPreferenceServiceImpl implements SearchPreferenceService {
    private final KbSearchPreferenceMapper mapper;
    @Override public List<KbSearchPreference> list(String kbId, String userId, boolean mineOnly) {
        var w=new LambdaQueryWrapper<KbSearchPreference>().eq(KbSearchPreference::getKbId,kbId);
        if(mineOnly) w.eq(KbSearchPreference::getUserId,currentUser());
        else if(userId!=null&&!userId.isEmpty()) w.eq(KbSearchPreference::getUserId,userId);
        return mapper.selectList(w.orderByDesc(KbSearchPreference::getUpdatedAt));
    }
    @Override public KbSearchPreference get(String id) { return mapper.selectById(id); }
    @Override public KbSearchPreference create(String kbId,KbSearchPreference p) {
        p.setId(null); p.setKbId(kbId);
        if(p.getUserId()==null||p.getUserId().isEmpty()) p.setUserId(currentUser());
        if(p.getSearchMode()==null) p.setSearchMode("hybrid");
        if(p.getTopK()==null) p.setTopK(10);
        if(p.getEnabled()==null) p.setEnabled(1);
        mapper.insert(p); return p;
    }
    @Override public KbSearchPreference update(String id,KbSearchPreference p) {
        var e=mapper.selectById(id);
        if(e==null) throw new RuntimeException("检索偏好不存在");
        p.setId(id); mapper.updateById(p); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    private String currentUser() {
        try { var u=SecurityUtil.getCurrentUser(); return u!=null?u.getUsername():"anonymous"; } catch (Exception e) { return "anonymous"; }
    }
}
