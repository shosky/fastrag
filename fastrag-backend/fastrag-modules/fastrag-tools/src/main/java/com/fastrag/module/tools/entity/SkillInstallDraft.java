package com.fastrag.module.tools.entity;

/**
 * 技能安装草稿 DTO，用于技能安装流水线（prepare → confirm → discard）。
 *
 * <p>非数据库实体，表示上传 ZIP/SKILL.md 后解析出的待安装技能列表，
 * 用户确认后正式写入 {@link Skill} 表。草稿有过期机制。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code draftId} - 草稿唯一标识</li>
 *   <li>{@code sourceType} - 来源类型：upload（上传）/ remote（远程）</li>
 *   <li>{@code source} - 来源描述（文件名 / 远程地址）</li>
 *   <li>{@code items} - 待安装技能列表（{@link DraftItem}）</li>
 *   <li>{@code expiresAt} - 草稿过期时间</li>
 *   <li>{@code createdBy} - 创建者</li>
 * </ul>
 *
 * @see SkillInstallDraft.DraftItem
 * @see Skill
 */
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
