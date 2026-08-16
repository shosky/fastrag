package com.fastrag.module.tools.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自定义工具（Tool）实体，对应数据库表 {@code tool}。
 *
 * <p>管理用户自定义的 HTTP 工具，通过 REST API 调用对接外部服务。
 * 工具与 MCP 工具共同构成 Agent 的工具集，由 {@link ToolRegistry} 统一注册管理。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} - 主键（雪花 ID）</li>
 *   <li>{@code name} - 工具名称</li>
 *   <li>{@code identifier} - 工具标识符</li>
 *   <li>{@code type} - 工具类型</li>
 *   <li>{@code inputs} - 输入参数 JSON Schema（用于前端表单渲染、LLM function calling、后端参数校验）</li>
 *   <li>{@code outputs} - 输出参数 JSON Schema</li>
 *   <li>{@code outputMapping} - 输出映射规则（JSONPath，如 {@code $.data.temperature}）</li>
 *   <li>{@code tags} - 标签（JSON 数组字符串，序列化/反序列化自动转换）</li>
 *   <li>{@code enabled} - 是否启用</li>
 *   <li>{@code isBuiltin} - 是否为内置工具</li>
 *   <li>{@code orgId} - 归属组织</li>
 *   <li>{@code httpConfig} - HTTP 配置（非 DB 字段，关联 {@link ToolHttpConfig}）</li>
 * </ul>
 *
 * @see ToolHttpConfig
 * @see ToolRegistry
 */
@Data
@TableName(value = "tool", autoResultMap = true)
public class Tool {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String identifier;
    private String description;
    private String type;
    private String icon;

    /** 输入参数 JSON Schema */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputs;

    /** 输出参数 JSON Schema（描述工具返回的数据结构） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputs;

    /** 输出映射规则（JSONPath，如 $.data.temperature） */
    private String outputMapping;

    @JsonIgnore
    private String tags;
    private Integer enabled;
    private String creator; // 创建者 userId（system=系统预置）
    private String orgId; // 归属组织
    private Integer isBuiltin; // 内置工具（全员可见）
    private LocalDateTime createdAt;

    /** HTTP 配置（非 DB 字段，仅用于 API 返回） */
    @TableField(exist = false)
    private ToolHttpConfig httpConfig;

    /** 返回 tags 时自动从 JSON 字符串解析为数组 */
    @JsonProperty("tags")
    @SneakyThrows
    public List<String> getTagsList() {
        if (tags == null || tags.isEmpty() || tags.equals("[]")) return List.of();
        if (tags.startsWith("[")) {
            return new ObjectMapper().readValue(tags, new TypeReference<List<String>>() {});
        }
        return List.of(tags.replaceAll("^\"|\"$", ""));
    }

    /** 接收 tags 时支持数组或字符串，统一存为 JSON 字符串 */
    @JsonProperty
    public void setTags(Object tags) {
        if (tags == null) { this.tags = null; return; }
        if (tags instanceof List || tags.getClass().isArray()) {
            try {
                this.tags = new ObjectMapper().writeValueAsString(tags);
            } catch (Exception e) {
                this.tags = tags.toString();
            }
        } else {
            this.tags = tags.toString();
        }
    }
}
