package com.fastrag.module.knowledge.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledge;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.entity.KbSyncConfig;
import com.fastrag.module.knowledge.entity.KbSyncRecord;
import com.fastrag.module.knowledge.mapper.KbKnowledgeMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KbSyncConfigMapper;
import com.fastrag.module.knowledge.mapper.KbSyncRecordMapper;
import com.fastrag.module.knowledge.service.KbSyncService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.List; import java.util.Map; import java.util.function.Function; import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class KbSyncServiceImpl implements KbSyncService {
    private final KbSyncConfigMapper configMapper;
    private final KbSyncRecordMapper recordMapper;
    private final KbKnowledgeMapper knowledgeMapper;
    private final KbQaPairMapper qaPairMapper;

    @Override public List<KbSyncConfig> listConfigs(String keyword) {
        var w = new LambdaQueryWrapper<KbSyncConfig>();
        if (keyword != null && !keyword.isEmpty()) w.like(KbSyncConfig::getName, keyword);
        return configMapper.selectList(w.orderByDesc(KbSyncConfig::getCreatedAt));
    }

    @Override public KbSyncRecord run(String configId) {
        KbSyncConfig c = configMapper.selectById(configId);
        if (c == null) throw new RuntimeException("同步配置不存在");
        KbSyncRecord r = new KbSyncRecord();
        r.setConfigId(configId);
        try {
            int entries = syncKnowledge(c);
            int qas = syncQaPairs(c);
            r.setSyncedEntries(entries); r.setSyncedQaPairs(qas);
            r.setStatus("success"); r.setMessage("同步完成");
            c.setLastSyncAt(LocalDateTime.now()); configMapper.updateById(c);
        } catch (Exception e) {
            log.error("知识库同步失败: config={}", configId, e);
            r.setSyncedEntries(0); r.setSyncedQaPairs(0);
            r.setStatus("failed"); r.setMessage(e.getMessage() == null ? "同步失败" : e.getMessage());
        }
        r.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(r);
        return r;
    }

    @Override public List<KbSyncRecord> records(String configId) {
        return recordMapper.selectList(new LambdaQueryWrapper<KbSyncRecord>().eq(KbSyncRecord::getConfigId, configId).orderByDesc(KbSyncRecord::getCreatedAt));
    }

    /** 每分钟扫描：到期且启用的配置自动执行（简易间隔调度） */
    @Override @Scheduled(fixedDelay = 60_000)
    public void scheduleSync() {
        try {
            for (KbSyncConfig c : configMapper.selectList(new LambdaQueryWrapper<KbSyncConfig>().eq(KbSyncConfig::getEnabled, 1))) {
                int interval = c.getIntervalMinutes() == null || c.getIntervalMinutes() <= 0 ? 60 : c.getIntervalMinutes();
                LocalDateTime due = (c.getLastSyncAt() == null ? c.getCreatedAt() : c.getLastSyncAt()).plusMinutes(interval);
                if (!LocalDateTime.now().isBefore(due)) {
                    log.info("定时同步触发: {}", c.getName());
                    run(c.getId());
                }
            }
        } catch (Exception e) {
            log.warn("同步调度异常", e);
        }
    }

    private int syncKnowledge(KbSyncConfig c) {
        List<KbKnowledge> source = knowledgeMapper.selectList(new LambdaQueryWrapper<KbKnowledge>()
                .eq(KbKnowledge::getKbId, c.getSourceKbId()).isNull(KbKnowledge::getDeletedAt));
        Map<String, KbKnowledge> targetById = knowledgeMapper.selectList(new LambdaQueryWrapper<KbKnowledge>()
                        .eq(KbKnowledge::getKbId, c.getTargetKbId()))
                .stream().collect(Collectors.toMap(KbKnowledge::getId, Function.identity()));
        int count = 0;
        for (KbKnowledge s : source) {
            KbKnowledge t = targetById.get(s.getId());
            boolean needSync = switch (c.getSyncMode() == null ? "incremental" : c.getSyncMode()) {
                case "full" -> t == null || s.getUpdatedAt() != null && (t.getUpdatedAt() == null || s.getUpdatedAt().isAfter(t.getUpdatedAt()));
                default -> t == null;
            };
            if (!needSync) continue;
            KbKnowledge copy = copy(s);
            copy.setKbId(c.getTargetKbId());
            if (t == null) knowledgeMapper.insert(copy); else { copy.setCreatedAt(null); copy.setUpdatedAt(LocalDateTime.now()); knowledgeMapper.updateById(copy); }
            count++;
        }
        return count;
    }

    private int syncQaPairs(KbSyncConfig c) {
        List<KbQaPair> source = qaPairMapper.selectList(new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId, c.getSourceKbId()));
        Map<String, KbQaPair> targetById = qaPairMapper.selectList(new LambdaQueryWrapper<KbQaPair>()
                        .eq(KbQaPair::getKbId, c.getTargetKbId()))
                .stream().collect(Collectors.toMap(KbQaPair::getId, Function.identity()));
        int count = 0;
        for (KbQaPair s : source) {
            if (targetById.containsKey(s.getId())) continue;
            KbQaPair copy = new KbQaPair();
            copy.setId(s.getId()); copy.setKbId(c.getTargetKbId()); copy.setFileId(s.getFileId()); copy.setFileName(s.getFileName());
            copy.setQuestion(s.getQuestion()); copy.setAnswer(s.getAnswer()); copy.setSource(s.getSource()); copy.setStatus(s.getStatus());
            copy.setFaqType(s.getFaqType()); copy.setKeywords(s.getKeywords());
            copy.setEffectiveStart(s.getEffectiveStart()); copy.setEffectiveEnd(s.getEffectiveEnd());
            copy.setEffectiveScope(s.getEffectiveScope()); copy.setRelatedKnowledgeIds(s.getRelatedKnowledgeIds());
            try { qaPairMapper.insert(copy); count++; } catch (Exception ignored) { }
        }
        return count;
    }

    private KbKnowledge copy(KbKnowledge s) {
        KbKnowledge k = new KbKnowledge();
        k.setId(s.getId()); k.setTitle(s.getTitle()); k.setContent(s.getContent()); k.setSummary(s.getSummary());
        k.setCategory(s.getCategory()); k.setSource(s.getSource()); k.setSourceId(s.getSourceId()); k.setStatus(s.getStatus());
        k.setCoverImage(s.getCoverImage()); k.setTags(s.getTags()); k.setAttributes(s.getAttributes());
        k.setQualityScore(s.getQualityScore());
        return k;
    }
}
