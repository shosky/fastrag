package com.fastrag.module.bpm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务流配置迁移记录（wf_migration）。
 * validate_result 存迁移结果 JSON：nodeCount/edgeCount/extCount/skipped/warnings/remark/durationMs。
 */
@Data
@TableName("wf_migration")
public class WfMigration {
    @TableId
    private String id;
    private String sourceWorkflowId;
    private String targetWorkflowId;
    private String targetEnv;
    /** overwrite / merge */
    private String strategy;
    /** running / completed / failed */
    private String status;
    private Integer progress;
    private String validateResult;
    private String operator;
    private LocalDateTime createdAt;
}
