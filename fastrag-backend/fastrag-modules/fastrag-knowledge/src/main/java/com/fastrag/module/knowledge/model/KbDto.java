package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库信息DTO。
 *
 * <p>表示一个知识库的完整信息，包含知识库ID、名称、描述、分类、Embedding模型、
 * 创建者、所属组织、类型、解析模式、分块模式、访问权限、图谱自动构建开关、
 * 标签列表、向量维度、创建时间、已用/总容量、文件类型配置和检索配置。
 * 用于知识库列表和详情展示。</p>
 */
@Data
public class KbDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private String embeddingModel;
    private String creator;
    private String orgId;
    private String type;
    private String parseMode;
    private String splitMode;
    private String permission;
    private Integer graphAutoBuild;
    private String graphLlmModel;
    private List<String> tags;
    private Integer dimension;
    private LocalDateTime createdAt;
    private Long usedSize;
    private Long totalSize;
    private Object fileTypeConfig;
    private Object retrievalConfig;
}
