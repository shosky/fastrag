package com.fastrag.module.tools.registry;

import cn.hutool.core.util.StrUtil;
import com.fastrag.ai.model.ChatRequest;
import com.fastrag.module.tools.entity.*;
import com.fastrag.module.tools.executor.InstallSkillToolDefinition;
import com.fastrag.module.tools.mapper.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {
    private final ToolMapper toolMapper;
    private final ToolHttpConfigMapper httpConfigMapper;
    private final McpToolMapper mcpToolMapper;
    private final McpServiceMapper mcpServiceMapper;
    private final SkillMapper skillMapper;

    /** 根据 toolIds 列表从 DB 加载所有工具定义 */
    public List<ToolDefinition> resolveTools(List<String> toolIds) {
        List<ToolDefinition> result = new ArrayList<>();

        // 始终注入 install_skill 工具（Agent 安装技能能力）
        result.add(InstallSkillToolDefinition.getDefinition());

        if (toolIds == null || toolIds.isEmpty()) return result;

        for (String id : toolIds) {
            if (StrUtil.isBlank(id)) continue;
            try {
                ToolDefinition def = resolveSingleTool(id.trim());
                if (def != null) result.add(def);
            } catch (Exception e) {
                log.warn("Failed to resolve tool '{}': {}", id, e.getMessage());
            }
        }
        return result;
    }

    private ToolDefinition resolveSingleTool(String id) {
        // MCP tool: mcp_xxx or mcp:xxx
        if (id.startsWith("mcp_") || id.startsWith("mcp:")) {
            return resolveMcpTool(id);
        }
        // skill: skill_xxx or skill:xxx
        if (id.startsWith("skill_") || id.startsWith("skill:")) {
            return resolveSkillTool(id);
        }
        // Default: try as HTTP/builtin tool
        return resolveHttpTool(id);
    }

    private ToolDefinition resolveHttpTool(String id) {
        Tool tool = toolMapper.selectById(id);
        if (tool == null || tool.getEnabled() == null || tool.getEnabled() != 1) return null;

        ToolDefinition def = new ToolDefinition();
        def.setToolId(tool.getId());
        def.setName(tool.getIdentifier() != null ? tool.getIdentifier() : tool.getName());
        def.setDescription(tool.getDescription());
        def.setType(tool.getType() != null ? tool.getType() : "http");
        def.setInputSchema(tool.getInputs());  // 直接就是 JSON Schema
        def.setOutputSchema(tool.getOutputs());
        def.setOutputMapping(tool.getOutputMapping());

        // Build config map from ToolHttpConfig
        ToolHttpConfig httpConfig = httpConfigMapper.selectById(id);
        if (httpConfig != null) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("method", httpConfig.getMethod());
            config.put("url", httpConfig.getUrl());
            config.put("authType", httpConfig.getAuthType());
            config.put("bodyType", httpConfig.getBodyType());
            config.put("body", httpConfig.getBody());
            config.put("params", parseJsonMap(httpConfig.getParams()));
            config.put("headers", parseJsonMap(httpConfig.getHeaders()));
            def.setConfig(config);
        }
        return def;
    }

    private ToolDefinition resolveMcpTool(String id) {
        // Parse mcp__serverName__toolName or mcp_xxx
        McpTool mcpTool = mcpToolMapper.selectById(Long.parseLong(id));
        if (mcpTool == null || mcpTool.getEnabled() == null || mcpTool.getEnabled() != 1) return null;

        McpService service = mcpServiceMapper.selectById(mcpTool.getServiceId());
        if (service == null) return null;

        ToolDefinition def = new ToolDefinition();
        def.setToolId(mcpTool.getToolId() != null ? mcpTool.getToolId() : "mcp_" + id);
        def.setName(mcpTool.getName());
        def.setDescription(mcpTool.getDescription());
        def.setType("mcp");
        def.setInputSchema(mcpTool.getParams());  // MCP tools already have JSON Schema

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("mcpServiceId", service.getId());
        config.put("mcpServiceName", service.getName());
        config.put("mcpToolId", mcpTool.getId());
        config.put("transport", service.getTransport());
        config.put("mcpUrl", service.getMcpUrl());
        config.put("command", service.getCommand());
        config.put("args", service.getArgs());
        config.put("env", service.getEnv());
        config.put("authType", service.getAuthType());
        config.put("authValue", service.getAuthValue());
        config.put("enabled", service.getEnabled());
        def.setConfig(config);
        return def;
    }

    private ToolDefinition resolveSkillTool(String id) {
        // 提取技能 ID/slug
        String skillRef = id.replaceAll("^(skill_|skill:)", "");

        // 先按 ID 查，再按 slug 查
        Skill skill = skillMapper.selectById(skillRef);
        if (skill == null) {
            skill = skillMapper.selectBySlug(skillRef);
        }
        if (skill == null || skill.getEnabled() == null || skill.getEnabled() != 1) {
            return null;
        }

        ToolDefinition def = new ToolDefinition();
        def.setToolId("skill_" + skill.getId());
        def.setName(skill.getSlug() != null ? skill.getSlug() : skill.getName());
        def.setDescription(skill.getDescription());
        def.setType("skill");
        def.setInputSchema(Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of("type", "string", "enum", List.of("read_skill", "activate"),
                    "description", "read_skill: 读取技能描述; activate: 激活技能"),
                "slug", Map.of("type", "string", "description", "技能 slug")
            ),
            "required", List.of("action")
        ));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("skillId", skill.getId());
        config.put("slug", skill.getSlug());
        config.put("name", skill.getName());
        config.put("description", skill.getDescription());
        config.put("content", skill.getContent());
        config.put("dependencies", skill.getDependencies());
        def.setConfig(config);

        return def;
    }

    /** 将 ToolDefinition 列表转为 OpenAI function calling 格式 */
    public List<ChatRequest.ToolDefinition> toOpenAITools(List<ToolDefinition> tools) {
        if (tools == null) return List.of();
        return tools.stream().map(t -> {
            ChatRequest.ToolDefinition td = new ChatRequest.ToolDefinition();
            td.setType("function");
            ChatRequest.FunctionDef fn = new ChatRequest.FunctionDef();
            fn.setName(t.getName());
            fn.setDescription(t.getDescription());
            // 如果 inputSchema 是完整 JSON Schema（有 type: "object"），直接用
            // 否则包装成标准格式
            if (t.getInputSchema() != null && t.getInputSchema().containsKey("type")) {
                fn.setParameters(t.getInputSchema());
            } else if (t.getInputSchema() != null && t.getInputSchema().containsKey("properties")) {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                schema.put("properties", t.getInputSchema());
                fn.setParameters(schema);
            } else {
                fn.setParameters(Map.of("type", "object", "properties", Map.of()));
            }
            td.setFunction(fn);
            return td;
        }).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseJsonMap(String json) {
        if (StrUtil.isBlank(json)) return Map.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            // 尝试解析为 JSON 对象: {"key":"value"}
            JsonNode node = om.readTree(json);
            if (node.isObject()) {
                Map<String, Object> map = om.convertValue(node, Map.class);
                Map<String, String> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(k, v != null ? v.toString() : ""));
                return result;
            }
            // 尝试解析为 JSON 数组: [{"key":"name","value":"val"}]
            if (node.isArray()) {
                Map<String, String> result = new LinkedHashMap<>();
                for (JsonNode item : node) {
                    String key = item.path("key").asText(null);
                    String value = item.path("value").asText(null);
                    if (key != null && value != null) {
                        result.put(key, value);
                    }
                }
                return result;
            }
            return Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }
}
