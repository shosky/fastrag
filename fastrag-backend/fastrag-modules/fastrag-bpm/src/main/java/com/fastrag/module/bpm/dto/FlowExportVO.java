package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.util.List;
import java.util.Map;

/** 流程导出/导入 JSON 文档(单流程完整快照) */
@Data
public class FlowExportVO {
    /** 文档格式版本,导出侧固定 1 */
    private Integer exportVersion = 1;
    /** 流程定义 */
    private FlowDefVO def;
    /** 当前版本 */
    private FlowVersionVO currentVersion;
    private List<NodeVO> nodes;
    private List<EdgeVO> edges;
    /** 导出时间 */
    private String exportedAt;
    /** 导出者 */
    private String exportedBy;
    /** 其它扩展字段 */
    private Map<String, Object> extras;
}