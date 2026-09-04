package com.fastrag.module.bpm.dto;
import lombok.Data;

@Data
public class ImportFlowRequest {
    /** 导出的 JSON 文档(字符串形式,后端再反序列化) */
    private String payload;
    /** 新流程名(可选;不传则沿用导出名 + " 导入") */
    private String newName;
    /** 是否覆盖同名流程(默认 false=新建) */
    private Boolean overwrite;
}