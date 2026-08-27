package com.fastrag.module.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 知识库创建请求DTO。
 *
 * <p>封装创建知识库的请求参数，包含知识库名称（必填）、分类、描述、访问权限、
 * Embedding模型、解析模式、分块模式、图谱自动构建开关、标签列表、
 * 文件类型配置和检索配置。被 KbController 的创建接口接收。</p>
 */
@Data
public class KbCreateRequest {
    @NotBlank
    private String name;
    private String category;
    private String description;
    private String permission;
    private String embeddingModel;
    private String parseMode;
    private String splitMode;
    private Boolean graphAutoBuild;
    private String graphLlmModel;
    private List<String> tags;
    private Object fileTypeConfig;
    private Object retrievalConfig;
    /** KB 级自定义属性定义（customAttrs 复活：JSON 数组或结构化列表，落库 kb.custom_attr_schema） */
    private Object customAttrSchema;
}
