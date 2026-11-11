package com.fastrag.module.knowledge.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量确认问答对请求体（qa-pairs 2026-11-02 REQ-B04）。
 */
@Data
public class QaBatchConfirmRequest {
    /** 待确认 QA 对 id 集合（去重后 ≤10000，超限业务错误拒绝） */
    @NotEmpty(message = "请选择要确认的问答对")
    private List<String> ids;
}
