package com.fastrag.module.bpm.dto;
import lombok.AllArgsConstructor; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;

/** 校验结果(画布/版本) */
@Data @AllArgsConstructor @NoArgsConstructor
public class CanvasValidateResultVO {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private Integer nodeCount;
    private Integer edgeCount;
}