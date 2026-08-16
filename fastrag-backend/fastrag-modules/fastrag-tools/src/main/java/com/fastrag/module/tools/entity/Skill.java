package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data; import java.time.LocalDateTime; import java.util.List; import java.util.Map;

/**
 * 技能（Skill）实体，对应数据库表 {@code skill}。
 *
 * <p>技能是 Agent 可调用的能力单元，支持 builtin（内置，随应用发布不可删除）、
 * remote（从远程仓库安装）和 custom（用户自定义）三种来源类型。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code slug} - 唯一标识符（用于 URL 路由，如 "web-search-tool"）</li>
 *   <li>{@code name} - 显示名称</li>
 *   <li>{@code sourceType} - 来源类型：builtin / remote / custom</li>
 *   <li>{@code content} - SKILL.md 原文内容</li>
 *   <li>{@code contentHash} - 内容 MD5 哈希，用于内置技能同步检测</li>
 *   <li>{@code dependencies} - 依赖的其他技能 slug 列表（JSON）</li>
 *   <li>{@code metadata} - SKILL.md frontmatter 元数据（JSON）</li>
 *   <li>{@code shareConfig} - 分享配置（非 DB 字段，DTO）：accessLevel / departmentIds / userUids</li>
 *   <li>{@code isBuiltin} - 是否为内置技能（由 {@link SkillDataInitializer} 种子初始化）</li>
 *   <li>{@code orgId} - 归属组织</li>
 *   <li>{@code trigger} - 触发关键词</li>
 *   <li>{@code enabled / recommended / usageCount} - 启用状态、推荐标记、使用计数</li>
 * </ul>
 *
 * @see SkillDependency
 * @see SkillScope
 * @see SkillShareConfig
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
