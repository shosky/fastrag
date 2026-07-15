package com.fastrag.module.tools.executor;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ToolContext {
    private String userId;
    private String appId;
    private String runId;
    private String query;

    /** 扩展属性（由中间件或调用方设置） */
    private Map<String, Object> extensions = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key) {
        return (T) extensions.get(key);
    }

    public void setExtension(String key, Object value) {
        extensions.put(key, value);
    }
}
