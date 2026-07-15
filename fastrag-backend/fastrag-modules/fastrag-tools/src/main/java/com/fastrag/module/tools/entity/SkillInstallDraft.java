package com.fastrag.module.tools.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SkillInstallDraft {
    private String draftId;
    private String sourceType;  // upload / remote
    private String source;      // 文件名 / 远程源
    private List<DraftItem> items;
    private LocalDateTime expiresAt;
    private String createdBy;

    @Data
    public static class DraftItem {
        private String slug;
        private String name;
        private String description;
        private boolean success;
        private String errorMsg;
        private Map<String, Object> metadata;
    }
}
