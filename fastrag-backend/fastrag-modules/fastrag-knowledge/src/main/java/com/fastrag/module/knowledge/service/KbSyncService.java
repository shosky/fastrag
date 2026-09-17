package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.entity.KbSyncConfig;
import com.fastrag.module.knowledge.entity.KbSyncRecord;

import java.util.List;

/** 知识库同步机制：手动/定时将源库的知识条目与问答对同步到目标库 */
public interface KbSyncService {
    List<KbSyncConfig> listConfigs(String keyword);
    KbSyncRecord run(String configId);
    List<KbSyncRecord> records(String configId);
    /** 定时调度：扫描到期且启用的同步配置并执行 */
    void scheduleSync();
}
