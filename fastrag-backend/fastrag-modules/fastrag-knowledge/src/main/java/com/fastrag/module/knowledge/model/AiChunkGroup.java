package com.fastrag.module.knowledge.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI分片 产出的一个分片（前端的一张分片卡）。
 *
 * <p>{@code source} 区分自动分片（LLM / Embedding 回退）与手动选中；
 * {@code paragraphIds} 为该分片覆盖的段落 id（跨页合并块多页片段共享同一 id）。
 */
@Data
@Builder
public class AiChunkGroup {

    private String id;

    private int index;

    /** auto=自动分片 | manual=手动选中 */
    private String source;

    /** 覆盖的段落 id（按文档顺序） */
    private List<String> paragraphIds;

    /** 分片文本（段落以空行拼接；apply 前可能被用户编辑） */
    private String text;

    /** 页码范围展示串，如 "15-16" */
    private String pageRange;
}
