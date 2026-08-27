package com.fastrag.module.knowledge.entity;
/**
 * 知识库实体类，对应数据库表 kb，是知识管理模块的核心聚合根。
 *
 * <p>核心职责：
 * 表示一个知识库实例，包含名称、描述、分类、标签、Embedding 模型配置、
 * 检索配置、文件类型限制等元信息。知识库按组织隔离（orgId），支持团队（team）
 * 和个人（personal）两种类型，以及 private/team/public 三种可见性级别。
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>id — 雪花算法生成的唯一标识（ASSIGN_ID）</li>
 *   <li>category — 关联 KbCategory 分类 ID</li>
 *   <li>tags — JSON 数组格式的标签 ID 列表</li>
 *   <li>embeddingModel / dimension — Embedding 模型名称与向量维度</li>
 *   <li>retrievalConfig — JSON 格式的检索策略配置（如 topK、相似度阈值等）</li>
 *   <li>fileTypeConfig — JSON 格式的允许上传文件类型配置</li>
 *   <li>parseMode / splitMode — 解析模式和分块模式</li>
 *   <li>graphAutoBuild — 是否自动构建知识图谱（0=关闭，1=开启）</li>
 *   <li>usedSize / totalSize — 已用和总存储配额（字节）</li>
 *   <li>type — 知识库类型：team（团队）/ personal（个人）</li>
 *   <li>permission — 可见性：private（私有）/ team（团队）/ public（公开）</li>
 *   <li>orgId — 归属组织 ID，同组织成员默认可见</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb")
public class KnowledgeBase {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String description;
    private String category;
    @JsonIgnore
    private String tags; // JSON array
    private String embeddingModel;
    private Integer dimension;
    private String creator;
    private String orgId; // 归属组织（同组织成员默认可见）
    private String type; // team / personal
    private String permission; // private / team / public
    private Long usedSize;
    private Long totalSize;
    private String retrievalConfig; // JSON
    private String fileTypeConfig; // JSON
    private String parseMode;
    private String splitMode;
    private Integer graphAutoBuild; // 是否自动构建知识图谱，默认 0（关闭）
    private String graphLlmModel;  // 知识图谱构建用 LLM 模型（fallback）
    /** KB 级自定义属性定义（customAttrs 复活落库）：JSON 数组串，如 [{"name":"文种","type":"select","options":["通知","办法"],"required":true}] */
    private String customAttrSchema;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @JsonProperty
    public void setTags(Object tags) { this.tags = tags == null ? null : tags.toString(); }
}
