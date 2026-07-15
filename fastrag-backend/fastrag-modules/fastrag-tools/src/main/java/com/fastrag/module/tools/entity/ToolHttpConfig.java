package com.fastrag.module.tools.entity;

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
