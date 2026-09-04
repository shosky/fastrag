package com.fastrag.module.bpm.dto;
import lombok.Data;

/** 流程分页查询请求 */
@Data
public class FlowDefPageReq {
    private Integer page = 1;
    private Integer size = 20;
    /** 关键字(名称/描述模糊) */
    private String keyword;
    /** 分类 */
    private String category;
    /** 可见性过滤 */
    private String visibility;
    /** 仅看自己的(默认 true) */
    private Boolean mineOnly = Boolean.TRUE;
}