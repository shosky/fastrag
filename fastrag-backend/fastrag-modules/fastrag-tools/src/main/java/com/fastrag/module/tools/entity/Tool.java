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
 * 工具实体。
 * <p>
 * inputs 字段存储标准 JSON Schema，示例:
 * <pre>
 * {
 *   "type": "object",
 *   "properties": {
 *     "city": { "type": "string", "description": "城市名称" },
 *     "date": { "type": "string", "enum": ["今天","明天","后天"], "description": "查询日期" }
 *   },
 *   "required": ["city"]
 * }
 * </pre>
 * 该 schema 同时用于：前端参数表单渲染、LLM function calling parameters、后端参数校验。
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
