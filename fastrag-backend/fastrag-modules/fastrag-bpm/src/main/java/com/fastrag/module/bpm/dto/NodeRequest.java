package com.fastrag.module.bpm.dto;
import lombok.Data;

/** 节点创建/更新请求 */
@Data
public class NodeRequest {
    /** 版本内唯一 key(创建时必填,更新时路径传) */
    private String nodeKey;
    /** 节点类型:start/end/user_input/llm/kb_retrieval/intent/http/condition/subflow */
    private String nodeType;
    private String name;
    private Integer positionX, positionY;
    /** 节点参数 JSON 字符串 */
    private String config;
    private Integer timeoutMs, retryCount, retryIntervalMs;
    /** fail/ignore/branch */
    private String onFailure;
    private String failureBranchNodeKey;
    private Boolean enabled;
}