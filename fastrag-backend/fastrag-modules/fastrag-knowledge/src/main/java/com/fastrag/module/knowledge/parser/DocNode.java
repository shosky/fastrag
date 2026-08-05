package com.fastrag.module.knowledge.parser;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 结构化文档节点 — 按文档出现顺序排列的展平序列。
 *
 * 多个 DocNode 构成 List<DocNode> 表示一篇文章的全部结构，
 * 标题层级通过 headingPath 栈算法在分片时动态构建，不需要 children 字段。
 */
@Data
@Builder
public class DocNode {
    private NodeType type;              // 节点类型
    private Integer level;              // 标题层级 1-6（仅 HEADING）
    private String title;               // 标题文本（仅 HEADING）
    private String content;             // 文本内容（PARAGRAPH / CODE_BLOCK 等）
    private List<String> headers;       // 表头列表（仅 TABLE）
    private List<List<String>> rows;    // 表格行数据（仅 TABLE）
    private String imageKey;            // MinIO 图片 key（仅 IMAGE）
    private String imageCaption;        // 图片描述（仅 IMAGE）
    private String codeLanguage;        // 代码语言（仅 CODE_BLOCK）
    private String latex;               // LaTeX 公式（仅 DISPLAY_MATH / INLINE_MATH）
    private Integer pageNumber;         // 所在页码

    public enum NodeType {
        HEADING, PARAGRAPH, TABLE, IMAGE,
        CODE_BLOCK, DISPLAY_MATH, INLINE_MATH,
        LIST, LIST_ITEM, HORIZONTAL_RULE, PAGE_BREAK
    }
}
