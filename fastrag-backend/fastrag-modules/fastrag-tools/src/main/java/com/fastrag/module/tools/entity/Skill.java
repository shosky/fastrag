package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data; import java.time.LocalDateTime; import java.util.List; import java.util.Map;

/**
 * 技能实体 - 对应 Yuxi 的 SkillInfo 模型.
 * <p>
 * sourceType 含义:
 * <ul>
 *   <li>builtin - 内置技能，随应用发布，不可删除</li>
 *   <li>remote  - 从远程仓库安装的技能</li>
 *   <li>custom  - 用户自定义技能</li>
 * </ul>
 */
@Data
@TableName(value = "skill", autoResultMap = true)
public class Skill {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 唯一标识符，用于 URL 路由，如 "web-search-tool" */
    private String slug;

    /** 显示名称 */
    private String name;

    /** 旧版标识符，保留兼容 */
    private String identifier;

    private String description;

    private String icon;

    /** 技能来源: builtin / remote / custom */
    private String sourceType;

    /** 旧版来源字段，保留兼容 */
    private String source;

    private String category;

    /** 触发关键词 */
    @TableField("`trigger`")
    private String trigger;

    /** SKILL.md 原文内容 */
    private String content;

    private String codeType;

    private String code;

    private String inputs;

    private String outputs;

    /** 内置技能的磁盘目录绝对路径 */
    private String dirPath;

    /** 依赖的其他技能 slug 列表 (JSON) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> dependencies;

    /** 技能内容哈希，用于内置技能同步检测 */
    private String contentHash;

    /** 是否为内置技能 */
    private Integer isBuiltin;
    private String creator; // 创建者 userId（system=系统预置）
    private String orgId; // 归属组织

    /** SKILL.md frontmatter 元数据 (JSON) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /** 分享配置 JSON：{accessLevel, departmentIds[], userUids[]} */
    @TableField(typeHandler = JacksonTypeHandler.class, exist = false)
    private Map<String, Object> shareConfig;

    private Integer enabled;
    private Integer recommended;
    private Integer usageCount;

    private String author;

    private String version;

    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
