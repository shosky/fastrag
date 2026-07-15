package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * install_skill 工具定义注册。
 * 提供 install_skill 工具的元数据和 JSON Schema。
 */
@Slf4j
@Component
public class InstallSkillToolDefinition {

    private static ToolDefinition DEFINITION;

    @PostConstruct
    public void init() {
        DEFINITION = createDefinition();
        log.info("[InstallSkillTool] install_skill 工具定义已注册");
    }

    public static ToolDefinition getDefinition() {
        if (DEFINITION == null) {
            DEFINITION = createDefinition();
        }
        return DEFINITION;
    }

    private static ToolDefinition createDefinition() {
        ToolDefinition def = new ToolDefinition();
        def.setToolId("builtin_install_skill");
        def.setName("install_skill");
        def.setDescription("从远程仓库或本地路径安装新技能包。安装后该技能将被自动启用。支持从 GitHub 仓库 (owner/repo 或 owner/repo@skill-name) 安装，也支持从本地沙箱路径安装。");
        def.setType("builtin");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of(
            "type", "string",
            "description", "技能来源。GitHub 仓库 (owner/repo) 或 (owner/repo@skill-name)"
        ));
        properties.put("skill_name", Map.of(
            "type", "string",
            "description", "要安装的具体技能名称（当 source 为仓库时指定具体技能）"
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of());

        def.setInputSchema(schema);
        return def;
    }
}
