package com.fastrag.module.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI分片 原件渲染的版面分析块（VLM 逐页识别）。
 *
 * <p>坐标为相对页面宽高的<b>归一化 0~1（顶左原点）</b>，与前端 pdf.js overlay 的
 * 百分比定位直接对应，页宽自适应缩放时框随页面等比变化。缓存于 MinIO
 * {@code {kbId}/{fileId}/layout.json}，文件不变即有效（版面与分片无关）。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChunkLayoutBlock {

    /** 页码（1-based） */
    private int page;

    /** title|text|table|image|code|formula|caption（VLM 输出白名单） */
    private String type;

    /** 归一化 0~1，顶左原点 */
    private double x;
    private double y;
    private double width;
    private double height;

    /** 块内文字摘要（VLM 输出，≤120 字符；供前端与段落做 hover 联动匹配） */
    private String text;
}
