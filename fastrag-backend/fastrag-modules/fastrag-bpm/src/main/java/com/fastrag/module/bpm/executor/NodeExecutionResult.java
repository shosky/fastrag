package com.fastrag.module.bpm.executor;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/** 节点执行结果 */
@Data
public class NodeExecutionResult {
    /** 是否成功 */
    private boolean success = true;
    /** 输出(写入 variables 或后续节点 inputs) */
    private Map<String, Object> outputs = new HashMap<>();
    /** 命中的下一节点 nodeKey 列表(空=无出边,流程结束) */
    private java.util.List<String> nextHints = new java.util.ArrayList<>();
    /** 是否需要等待用户输入(由 UserInputNodeExecutor 设置) */
    private boolean waitingInput;
    /** 用户输入表单 schema(由 UserInputNodeExecutor 设置) */
    private String pendingInputForm;
    /** 失败/异常信息 */
    private String errorMessage;
    /** 子流程实例 ID(由 SubflowNodeExecutor 设置,用于 resume 关联) */
    private String subflowInstanceId;
}