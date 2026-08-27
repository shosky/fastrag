package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.List;

/**
 * 批量打标请求（分册四 rag-file-metadata-management.md）。
 *
 * <p>对一批文件批量增加/移除标签：addTagIds 与 removeTagIds 可同时非空（先加后减，
 * 同一标签同时出现在两边时以 add 优先）。</p>
 */
@Data
public class FileBatchTagRequest {
    /** 目标文件 ID 列表（必填，非空） */
    private List<String> fileIds;
    /** 要增加的标签 ID 列表（可空） */
    private List<String> addTagIds;
    /** 要移除的标签 ID 列表（可空） */
    private List<String> removeTagIds;
}