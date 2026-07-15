package com.fastrag.module.tools.service;

import com.fastrag.module.tools.entity.SkillInstallDraft;

import java.util.List;
import java.util.Map;

public interface SkillDraftService {

    /**
     * 上传 ZIP 或 SKILL.md，创建安装草稿
     */
    SkillInstallDraft prepareUpload(String filename, byte[] fileBytes, String operator);

    /**
     * 确认安装草稿
     */
    List<SkillInstallDraft.DraftItem> confirmDraft(String draftId, Map<String, Object> shareConfig, String operator);

    /**
     * 丢弃草稿
     */
    void discardDraft(String draftId, String operator);
}
