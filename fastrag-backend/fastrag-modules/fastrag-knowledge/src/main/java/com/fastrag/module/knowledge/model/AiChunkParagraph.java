package com.fastrag.module.knowledge.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI分片 段落对齐模型的一个段落（parsed.json 的 paragraph 元素）。
 *
 * <p>单一事实源：左侧原件片段锚点、右侧分片卡、跨页合并标记均以 {@code id}（paragraphId）关联。
 * 跨页合并块在 {@code pages} 中保留全部出现页（前端在每页各渲染一个片段，共享同一 id）。
 */
@Data
@Builder
public class AiChunkParagraph {

    /** 稳定、全局唯一；分片卡经 paragraphIds 引用 */
    private String id;

    /** 文档顺序 */
    private int order;

    /** paragraph|heading|table|code|list|image|caption */
    private String type;

    /** 段落文本：table 为制表符分隔（首行表头）；code 为代码/JSON；heading 为标题行 */
    private String text;

    /** 图片段落：MinIO 图片 key（如 page_3_img_0.png / docx_img_1.png），前端按 {kbId}/{fileId}/images/{key} 渲染 */
    private String imageKey;

    /** 段落文字块坐标（PDF 用，归一化 0~1 顶左原点；跨页合并块多页各有一个盒），前端 pdf.js overlay 据此在原文件上画分片边界 */
    private List<RectItem> rects;

    @Data
    @Builder
    public static class RectItem {
        /** 页码（1-based） */
        private int page;
        private float x;
        private float y;
        private float width;
        private float height;
    }

    /** 首次出现页（1-based；PPTX 记录 slide 序号） */
    private int page;

    /** 全部出现页（跨页合并块为多页，如 [15,16]） */
    private List<Integer> pages;

    /** 页码范围展示串，如 "15-16"；未跨页为 "15" */
    private String pageRange;

    /** 章节层级路径，如 "5.8" 或 "第一章 > 1.1 背景" */
    private String headingPath;

    /** 所属分片 id（自动分片后回填；手动/编辑后由 apply 回填） */
    private String chunkId;

    /** 是否跨页合并块 */
    private boolean crossPage;

    /** 是否被人工编辑过（校对标记） */
    private boolean edited;
}
