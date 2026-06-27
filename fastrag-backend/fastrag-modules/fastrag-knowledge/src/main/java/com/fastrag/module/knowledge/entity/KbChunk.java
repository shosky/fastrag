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
}
