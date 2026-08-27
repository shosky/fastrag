package com.fastrag.module.knowledge.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI分片 解析结果（ai-chunk-preview 与 SSE done 事件的载荷）。
 *
 * <p>左侧原件片段（paragraphs，跨页合并块多页共享 id）+ 右侧分片卡（chunks）。
 */
@Data
@Builder
public class AiChunkResult {

    private String fileId;

    private String fileName;

    private String extension;

    private String kbId;

    private DocumentInfo document;

    /** 段落对齐模型（含跨页合并结果） */
    private List<AiChunkParagraph> paragraphs;

    /** PDF 内容图片渲染位置（仅 PDF；归一化 0~1 顶左原点，与 layoutBlocks 同坐标系；原件可视化兜底数据） */
    private List<AiChunkParagraph.RectItem> imageBoxes;

    /** 版面分析块（VLM 逐页识别，归一化坐标；MinIO layout.json 缓存命中时随预览返回，否则前端走 SSE 按需生成） */
    private List<AiChunkLayoutBlock> layoutBlocks;

    /** 自动分片结果 */
    private List<AiChunkGroup> chunks;

    /** 实际采用的分片路径：llm | embedding | rule（供前端提示降级） */
    private String chunkSource;

    @Data
    @Builder
    public static class DocumentInfo {
        private String type;
        private int pages;
        /** 跨页合并块数量 */
        private int mergedBlocks;
    }
}
