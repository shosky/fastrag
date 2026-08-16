package com.fastrag.module.tools.entity;

/**
 * 工具 HTTP 配置实体，对应数据库表 {@code tool_http_config}。
 *
 * <p>存储自定义工具的 HTTP 请求配置，包括请求方法、URL、认证方式、
 * 请求头和查询参数等。通过 {@code toolId} 与 {@link Tool} 一对一关联。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code toolId} - 关联工具 ID（主键，与 {@link Tool#id} 一对一）</li>
 *   <li>{@code method} - HTTP 方法（GET/POST/PUT/PATCH/DELETE）</li>
 *   <li>{@code url} - 请求 URL（支持模板变量）</li>
 *   <li>{@code authType} - 认证方式（none/bearer/basic）</li>
 *   <li>{@code bodyType} - 请求体类型</li>
 *   <li>{@code body} - 请求体模板</li>
 *   <li>{@code params / headers} - 查询参数和请求头（JSON 字符串存储，API 层自动序列化/反序列化）</li>
 * </ul>
 *
 * @see Tool
 */
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;

@Data
@TableName(value = "tool_http_config", autoResultMap = true)
public class ToolHttpConfig {
    @TableId(type = IdType.ASSIGN_ID)
    private String toolId;
    private String method;
    private String url;
    private String authType;
    private String bodyType;
    private String body;
    private String authValue;

    /** 原始 JSON 字符串（数据库存储格式） */
    @JsonIgnore
    private String params;
    /** 原始 JSON 字符串（数据库存储格式） */
    @JsonIgnore
    private String headers;

    /** 返回给前端时自动解析为数组 */
    @JsonProperty("params")
    @SneakyThrows
    public List<Map<String, String>> getParamsList() {
        if (params == null || params.isEmpty()) return List.of();
        return new ObjectMapper().readValue(params, new TypeReference<List<Map<String, String>>>() {});
    }

    /** 接收前端数组，存为 JSON 字符串 */
    @JsonProperty
    public void setParams(Object v) {
        if (v == null) { this.params = null; return; }
        if (v instanceof String) { this.params = (String) v; return; }
        try { this.params = new ObjectMapper().writeValueAsString(v); }
        catch (Exception e) { this.params = v.toString(); }
    }

    @JsonProperty("headers")
    @SneakyThrows
    public List<Map<String, String>> getHeadersList() {
        if (headers == null || headers.isEmpty()) return List.of();
        return new ObjectMapper().readValue(headers, new TypeReference<List<Map<String, String>>>() {});
    }

    @JsonProperty
    public void setHeaders(Object v) {
        if (v == null) { this.headers = null; return; }
        if (v instanceof String) { this.headers = (String) v; return; }
        try { this.headers = new ObjectMapper().writeValueAsString(v); }
        catch (Exception e) { this.headers = v.toString(); }
    }
}
