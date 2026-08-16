package com.fastrag.module.knowledge.parser;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ParseResult {
    private String text;
    private int pages;
    private String metadata;
    private List<DocNode> nodes;            // 结构化文档节点列表（新增）
    private List<ChunkTimeSegment> segments;
    private List<ParseImage> images;        // 文档内嵌图片（DOCX 等），由消费方上传 MinIO
    private Map<Integer, String> pageTitles; // PDF 页 → 最近标题（1-based），供图片分片上下文

    @Data
    @Builder
    public static class ChunkTimeSegment {
        private String text;
        private Double startTime;
        private Double endTime;
    }

    /** 解析过程中提取的内嵌图片（如 DOCX 段落图片），imageKey 与 DocNode(IMAGE).imageKey 对应 */
    @Data
    @Builder
    public static class ParseImage {
        /** MinIO 图片 key（相对路径，如 "docx_img_0.png"），存储于 {kbId}/{fileId}/images/ 下 */
        private String imageKey;
        /** 图片原始字节 */
        private byte[] data;
        /** MIME 类型（如 image/png） */
        private String contentType;
        /** 图片宽度（像素），未知为 null（用于过滤装饰性小图） */
        private Integer width;
        /** 图片高度（像素），未知为 null */
        private Integer height;
        /** 图片所在位置的文档上下文（如最近标题"3.2 产数收入完成情况"），可为 null */
        private String context;
        /** 图片所在页码/slide 序号（0-based，PPT 图片为 slide 序号），可为 null */
        private Integer pageNumber;
    }
}
