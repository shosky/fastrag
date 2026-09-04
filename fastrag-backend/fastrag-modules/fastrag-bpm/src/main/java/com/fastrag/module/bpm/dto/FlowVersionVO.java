package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.time.LocalDateTime;

/** 流程版本读视图 */
@Data
public class FlowVersionVO {
    private String id, flowDefId, status, remark, publisherId;
    private Integer versionNo;
    private LocalDateTime publishedAt, createdAt;
}