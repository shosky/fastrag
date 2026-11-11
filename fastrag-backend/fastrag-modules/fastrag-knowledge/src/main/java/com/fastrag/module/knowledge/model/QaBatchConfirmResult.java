package com.fastrag.module.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量确认问答对结果（qa-pairs 2026-11-02 REQ-B04）。
 *
 * <p>混选语义：confirmedCount = 本次 draft→confirmed 的行数；
 * skippedCount = 已 confirmed 幂等跳过 + 不存在/跨库 id 的行数（对齐 REQ-B02 静默精神）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QaBatchConfirmResult {
    /** 本次实际确认的行数（draft→confirmed） */
    private int confirmedCount;
    /** 跳过行数（已 confirmed 幂等跳过 + 不存在/跨库 id） */
    private int skippedCount;
}
