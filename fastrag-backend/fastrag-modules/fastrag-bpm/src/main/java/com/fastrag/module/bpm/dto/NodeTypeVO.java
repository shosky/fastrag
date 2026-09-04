package com.fastrag.module.bpm.dto;
import lombok.Data;

/** 节点类型元数据视图(来自 bpm_node_type_meta) */
@Data
public class NodeTypeVO {
    private String type, icon, color, category, description;
    private String label;
    /** JSON 数组字符串:[{key,label,type,defaultValue,options,validation}] */
    private String configSchema;
    /** JSON 对象字符串 */
    private String defaultConfig;
    private Boolean enabled;
    private Integer sortOrder;
}