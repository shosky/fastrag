package com.fastrag.module.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识库标签创建/更新请求（分册四 rag-file-metadata-management.md）。
 *
 * <p>POST /api/kb-tags 创建（kbId 必填，用于权限归属与隔离）；
 * PUT /api/kb-tags/{id} 更新（kbId 可不传，归属取自已存在标签记录）。
 * 标签归属于其创建时的 KB（复用 kb_tag.kbId 语义），跨 KB 共享标签不做。</p>
 */
@Data
public class KbTagRequest {
    /** 归属知识库 ID（POST 创建时必填） */
    @NotBlank
    private String kbId;
    @NotBlank
    private String name;
    private String color;
    private String description;
    /** 标签类型（T1 文档分类 / T2 优先级 / T3 状态），可空 */
    private String tagTypeId;
}