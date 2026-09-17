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
    // 库内向量缓存（JSON 数组）：语义检索向量来源，检索时懒计算回填
    private String embedding;
    private Double startTime;
    private Double endTime;
}
