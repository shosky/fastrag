package com.fastrag.module.bpm.dto;
import lombok.Data;

@Data
public class VersionCreateRequest {
    private String remark;
    /** 是否基于当前已发布版本创建(默认 false=基于当前 draft) */
    private Boolean fromPublished;
}