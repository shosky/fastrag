package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.List;

/**
 * AI分片 应用请求体（POST /{id}/ai-chunk-apply）。
 *
 * <p>契约：后端**尊重用户确认的分片边界**——只做向量化与落库，不重新切分、
 * 不重新解析原文件（跨页合并与手工边界因此得以保留，见设计文档 §7.3）。
 */
@Data
public class AiChunkApplyRequest {

    private List<Item> chunks;

    @Data
    public static class Item {
        /** 该分片覆盖的段落 id（回填 parsed.json 的 chunkId 与审计追溯） */
        private List<String> paragraphIds;
        /** 该分片最终文本（可能经用户编辑） */
        private String text;
        /** auto=自动分片 | manual=手动选中 */
        private String source;
        /** 页码范围展示串，如 "15-16"（可选） */
        private String pageRange;
    }
}
