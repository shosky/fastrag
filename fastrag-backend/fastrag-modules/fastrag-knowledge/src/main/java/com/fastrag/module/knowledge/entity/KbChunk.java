package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("kb_chunk")
public class KbChunk {
    @TableId(type = IdType.INPUT)
    private String id; // format: {fileId}_chunk_{index}
    private String kbId;
    private String fileId;
    private String fileName;
    private Integer chunkIndex;
    private String content;
    private String embeddingId;
    private Integer vectorStored;
    private Double startTime;
    private Double endTime;

    // PDF 页感知分块
    private Integer pageNumber;       // 所属页码
    private String pageRange;         // 页码范围 "3-4"
    private String imageKeys;         // JSON 数组 ["page_1_img_0.png"]
    private String chunkType;         // "text" | "image"，默认 "text"
}
