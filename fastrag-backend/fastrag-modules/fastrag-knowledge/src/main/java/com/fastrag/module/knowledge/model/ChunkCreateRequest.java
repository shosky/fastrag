package com.fastrag.module.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增分片请求
 */
@Data
public class ChunkCreateRequest {
    @NotBlank(message = "文件ID不能为空")
    private String fileId;

    @NotBlank(message = "分片内容不能为空")
    private String content;

    /** 插入到指定 chunkIndex 之后，null 则追加到末尾 */
    private Integer insertAfterIndex;

    /** 音视频开始时间（秒） */
    private Double startTime;
    /** 音视频结束时间（秒） */
    private Double endTime;
    /** PDF 页码 */
    private Integer pageNumber;
    /** 分片类型：text / image */
    private String chunkType;
}
