package com.fastrag.module.bpm.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.BpmFlowVersion;

import java.util.List;

public interface BpmFlowVersionService extends IService<BpmFlowVersion> {
    /** 流程的全部版本(按 versionNo desc) */
    List<FlowVersionVO> listByFlow(String flowDefId);
    /** 获取指定 versionNo 的完整详情(含 nodes/edges) */
    VersionDetailVO detail(String flowDefId, Integer versionNo);
    /** 基于指定源版本创建新草稿版本(返回新 version_no) */
    Integer createDraft(String flowDefId, VersionCreateRequest req);
    /** 发布版本:status=draft → published,并把 current_version_id 指向它,旧版本降为 archived */
    FlowVersionVO publish(String flowDefId, Integer versionNo, String operatorId);
    /** 回滚 current_version_id 到历史 versionNo(目标版本 status 置为 published,其它 archived) */
    FlowVersionVO rollback(String flowDefId, Integer versionNo);
    /** 软删除版本(状态置 archived,保留记录) */
    void archive(String flowDefId, Integer versionNo);
}