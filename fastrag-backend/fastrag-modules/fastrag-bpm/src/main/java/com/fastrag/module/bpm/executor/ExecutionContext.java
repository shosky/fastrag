package com.fastrag.module.bpm.executor;

import com.fastrag.module.bpm.entity.BpmFlowEdge;
import com.fastrag.module.bpm.entity.BpmFlowInstance;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.entity.BpmFlowVersion;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 执行上下文(一次节点执行的全部输入) */
@Data
public class ExecutionContext {
    /** 关联流程版本/定义 */
    private BpmFlowVersion flowVersion;
    private String flowDefId;
    /** 当前实例 */
    private BpmFlowInstance instance;
    /** 当前节点 */
    private BpmFlowNode currentNode;
    /** 以该节点为 source 的出边(已按 priority asc 排序) */
    private List<BpmFlowEdge> outgoingEdges;
    /** 流程全局变量(包含输入参数 + 之前节点的 outputs) */
    private Map<String, Object> variables = new HashMap<>();
    /** 节点入参(取自前一节点的 outputs 或 inputParams) */
    private Map<String, Object> nodeInputs = new HashMap<>();
    /** 链路追踪 ID,默认等于 instance.traceId */
    private String traceId;
    /** 当前子流程嵌套层级,根为 0,每嵌套一层 +1,>5 拒绝 */
    private int subflowDepth;
    /** 操作人 ID(触发者) */
    private String operatorId;
}