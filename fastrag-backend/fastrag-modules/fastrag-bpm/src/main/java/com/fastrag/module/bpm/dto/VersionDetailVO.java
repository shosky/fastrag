package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.util.List;

/** 版本详情(包含 nodes/edges) */
@Data
public class VersionDetailVO {
    private FlowVersionVO version;
    private List<NodeVO> nodes;
    private List<EdgeVO> edges;
    /** 校验结果(可选,validate=true 时填充) */
    private CanvasValidateResultVO validation;
}