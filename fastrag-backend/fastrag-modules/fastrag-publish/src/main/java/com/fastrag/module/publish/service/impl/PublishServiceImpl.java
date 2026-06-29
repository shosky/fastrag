package com.fastrag.module.publish.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.publish.entity.KbVersion; import com.fastrag.module.publish.mapper.KbVersionMapper;
import com.fastrag.module.publish.service.PublishService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class PublishServiceImpl implements PublishService {
    private final KbVersionMapper mapper;
    @Override public List<KbVersion> listVersions(String kbId) { return mapper.selectList(new LambdaQueryWrapper<KbVersion>().eq(KbVersion::getKbId,kbId).orderByDesc(KbVersion::getCreatedAt)); }
    @Override public KbVersion getLatestVersion(String kbId) { return mapper.selectOne(new LambdaQueryWrapper<KbVersion>().eq(KbVersion::getKbId,kbId).orderByDesc(KbVersion::getVersion).last("LIMIT 1")); }
    @Override public KbVersion getPublishedVersion(String kbId) { return mapper.selectOne(new LambdaQueryWrapper<KbVersion>().eq(KbVersion::getKbId,kbId).eq(KbVersion::getPublishStatus,"published").last("LIMIT 1")); }
    @Override public KbVersion createVersion(String kbId,KbVersion data) { data.setKbId(kbId); data.setPublishStatus("draft"); if(data.getVersion()==null) data.setVersion(1); mapper.insert(data); return data; }
    @Override public void transitionStatus(String kbId,String versionId,String targetStatus) { var v=mapper.selectById(versionId); if(v!=null){v.setPublishStatus(targetStatus);mapper.updateById(v);} }
}
