package com.fastrag.module.publish.service.impl;

/**
 * 知识库版本发布服务实现。
 *
 * <p>实现 {@link PublishService} 接口，管理知识库版本的生命周期。</p>
 *
 * <h3>核心实现逻辑：</h3>
 * <ul>
 *   <li>版本列表查询：按知识库ID查询所有版本，按创建时间倒序排列</li>
 *   <li>最新版本查询：按知识库ID获取版本号最大的记录</li>
 *   <li>已发布版本查询：按知识库ID和 publishStatus="published" 获取唯一发布版本</li>
 *   <li>创建版本：自动分配递增版本号（基于当前最大版本号+1），初始状态为 draft</li>
 *   <li>状态流转：直接更新版本状态，当状态变更为 "published" 时，
 *       自动向 {@code kb_publish_history} 表写入一条发布历史记录</li>
 * </ul>
 *
 * @see PublishService
 * @see KbVersionMapper
 * @see KbPublishHistoryMapper
 */
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.publish.entity.KbPublishHistory;
import com.fastrag.module.publish.entity.KbVersion;
import com.fastrag.module.publish.mapper.KbPublishHistoryMapper;
import com.fastrag.module.publish.mapper.KbVersionMapper;
import com.fastrag.module.publish.service.PublishService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class PublishServiceImpl implements PublishService {
    private final KbVersionMapper mapper;
    private final KbPublishHistoryMapper phMapper;
    @Override public List<KbVersion> listVersions(String kbId) { return mapper.selectList(new LambdaQueryWrapper<KbVersion>().eq(KbVersion::getKbId,kbId).orderByDesc(KbVersion::getCreatedAt)); }
    @Override public KbVersion getLatestVersion(String kbId) { return mapper.selectOne(new LambdaQueryWrapper<KbVersion>().eq(KbVersion::getKbId,kbId).orderByDesc(KbVersion::getVersion).last("LIMIT 1")); }
    @Override public KbVersion getPublishedVersion(String kbId) { return mapper.selectOne(new LambdaQueryWrapper<KbVersion>().eq(KbVersion::getKbId,kbId).eq(KbVersion::getPublishStatus,"published").last("LIMIT 1")); }
    @Override public KbVersion createVersion(String kbId,KbVersion data) {
        data.setKbId(kbId); data.setPublishStatus("draft");
        // 自动分配版本号
        var latest=mapper.selectOne(new LambdaQueryWrapper<KbVersion>().eq(KbVersion::getKbId,kbId).orderByDesc(KbVersion::getVersion).last("LIMIT 1"));
        data.setVersion(latest!=null?latest.getVersion()+1:1);
        mapper.insert(data); return data;
    }
    @Override public void transitionStatus(String kbId,String versionId,String targetStatus) {
        var v=mapper.selectById(versionId); if(v==null) return;
        v.setPublishStatus(targetStatus); mapper.updateById(v);
        // 发布时同步写入 kb_publish_history 表
        if("published".equals(targetStatus)) {
            KbPublishHistory ph=new KbPublishHistory();
            ph.setKbId(kbId); ph.setKnowledgeId(versionId);
            ph.setVersion(v.getVersion()); ph.setPublishType("publish");
            ph.setStatus("published"); ph.setOperator(v.getCreatedBy());
            ph.setPublishedAt(LocalDateTime.now());
            phMapper.insert(ph);
        }
    }
}
