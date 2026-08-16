package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 解析策略信息DTO。
 *
 * <p>表示知识库文件解析策略的完整信息，包含策略ID、名称、描述、文档类型（解析方法）、
 * LLM模型、适用的文件扩展名列表、是否为默认策略、
 * 高级配置和创建/更新时间。用于解析策略管理界面展示。</p>
 */
@Data
public class ParseStrategyDto {
    private String id;
    private String name;
    private String description;
    private String parseMethod;
    private String llmModel;
    private List<String> extensions;
    private boolean isDefault;
    private Map<String, Object> advanced;
    /** 引用该策略的未删除文件数（删除确认框据此提示影响面：删除后这些文件回退自动匹配） */
    private Integer fileCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
