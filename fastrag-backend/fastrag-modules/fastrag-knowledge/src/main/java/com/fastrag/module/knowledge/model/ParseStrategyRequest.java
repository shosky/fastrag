package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 解析策略创建/更新请求DTO。
 *
 * <p>封装创建或更新解析策略的请求参数，包含策略名称、描述、文档类型（解析方法）、
 * LLM模型、适用的文件扩展名列表和高级配置。
 * 扩展名必须与 parseMethod 兼容（由 Service 校验，详见 ParseMethodRegistry）。
 * 被 KbController 的解析策略管理接口接收。</p>
 */
@Data
public class ParseStrategyRequest {
    private String name;
    private String description;
    private String parseMethod;
    private String llmModel;
    private List<String> extensions;
    private Map<String, Object> advanced;
}
