package com.fastrag.module.tools.entity;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 技能分享配置 DTO，对应 share_config JSON 字段。
 *
 * accessLevel 含义：
 * - global: 全局可见（所有人）
 * - department: 指定部门可见
 * - user: 仅指定用户可见
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
