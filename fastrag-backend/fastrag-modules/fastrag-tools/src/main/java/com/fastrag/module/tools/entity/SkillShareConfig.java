package com.fastrag.module.tools.entity;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 技能分享配置 DTO，对应 {@link Skill} 实体的 {@code shareConfig} JSON 字段。
 *
 * <p>非数据库实体，用于 API 请求/响应中的分享配置序列化。
 * 控制技能的可见范围。</p>
 *
 * <h3>accessLevel 含义：</h3>
 * <ul>
 *   <li>{@code global} - 全局可见（所有人可访问）</li>
 *   <li>{@code department} - 指定部门可见（通过 departmentIds 指定）</li>
 *   <li>{@code user} - 仅指定用户可见（通过 userUids 指定，默认值）</li>
 * </ul>
 *
 * <p>提供 {@link #defaultConfig()} 和 {@link #builtinConfig()} 静态工厂方法，
 * 以及 {@link #toMap()} 转换为 Map 供持久化使用。</p>
 *
 * @see Skill
 */
@Data
public class SkillShareConfig {
    private String accessLevel = "user";
    private List<String> departmentIds = List.of();
    private List<String> userUids = List.of();

    public static SkillShareConfig defaultConfig() {
        SkillShareConfig c = new SkillShareConfig();
        c.setAccessLevel("user");
        return c;
    }

    public static SkillShareConfig builtinConfig() {
        SkillShareConfig c = new SkillShareConfig();
        c.setAccessLevel("global");
        return c;
    }

    public Map<String, Object> toMap() {
        return Map.of(
            "accessLevel", accessLevel,
            "departmentIds", departmentIds,
            "userUids", userUids
        );
    }
}
