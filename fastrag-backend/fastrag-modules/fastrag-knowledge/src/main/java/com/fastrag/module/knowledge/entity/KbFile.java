package com.fastrag.module.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_file")
public class KbFile {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String kbId;
    private String name;
    private String category; // document / image / audio / video
    private String extension;
    private Long size;
    private String objectKey;
    private String status; // pending / processing / completed / failed
    private Integer progress;
    private String stage;
    private Integer duration;
    private Integer pages;
    private String parseStrategyId;
    private String parseStrategyName;
    private Integer chunkCount;
    private String folderId;
    private Long viewCount;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
