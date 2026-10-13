package com.fastrag.module.bpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 业务流调试日志（wf_debug_log）。node_key='__settings__' 的行用于保存调试级别设置，不作为日志展示。 */
@Data
@TableName("wf_debug_log")
public class WfDebugLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workflowId;
    private String nodeKey;
    private String level;
    private String message;
    private String context;
    private LocalDateTime createdAt;
}
