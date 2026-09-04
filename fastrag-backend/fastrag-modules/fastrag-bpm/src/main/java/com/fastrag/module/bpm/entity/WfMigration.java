package com.fastrag.module.bpm.entity;

import lombok.Data;

/**
 * 旧 /api/workflows/* 接口的迁移记录入参 DTO。
 */
@Data
public class WfMigration {
    private String fromFlowKey;
    private String toFlowKey;
    private String note;
    /** pending / running / success / failed */
    private String status;
}