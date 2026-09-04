package com.fastrag.module.bpm.executor;

import java.util.Map;

/** 节点执行器接口 */
public interface NodeExecutor {
    /** 节点类型,需与 BpmNodeTypeMeta.type + executor 路由 key 完全一致 */
    String type();
    /** 节点分类(control/execute/input/terminal),用于画布分组 */
    default String category() { return "execute"; }
    /** 校验节点 config JSON,失败抛 BpmErrorCode.NODE_CONFIG_INVALID */
    default void validateConfig(Map<String, Object> config) {}
    /** 执行逻辑,抛出 BpmErrorCode.NODE_* / EXECUTION_* 等 */
    NodeExecutionResult execute(ExecutionContext ctx);
}