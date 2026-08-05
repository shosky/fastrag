package com.fastrag.common.enums;

/**
 * 系统操作动作类型枚举
 * <p>统一所有模块的重要操作的动作标识，记录到 {@code kb_log} 和 {@code sys_audit_log}。
 * <p>命名规范：{模块}_{动作}，使用小写 snake_case。
 */
public enum ActionType {
    // ==================== 知识库本体 ====================
    kb_created,
    kb_updated,
    kb_deleted,

    // ==================== 文件生命周期 ====================
    file_uploaded,
    file_processed,
    file_processing_started,
    file_processing_failed,
    file_updated,
    file_removed,
    file_restored,
    file_permanent_deleted,
    file_copied,
    file_retried,
    file_downloaded,
    file_moved,

    // ==================== 文件夹 ====================
    folder_created,
    folder_updated,
    folder_deleted,

    // ==================== QA 对 ====================
    qa_pair_created,
    qa_pair_updated,
    qa_pair_deleted,
    qa_pair_confirmed,

    // ==================== 分片 ====================
    chunk_created,
    chunk_updated,
    chunk_deleted,

    // ==================== 解析策略 ====================
    strategy_created,
    strategy_updated,
    strategy_deleted,
    strategy_set_default,

    // ==================== 知识图谱 ====================
    graph_build_started,
    graph_build_completed,
    graph_build_failed,
    graph_build_retried,
    graph_deleted,
    graph_settings_updated,

    // ==================== 评测 ====================
    evaluation_run,
    evaluation_deleted,

    // ==================== 基准测试 ====================
    benchmark_created,
    benchmark_generated,
    benchmark_deleted,
    benchmark_imported,

    // ==================== 知识发布 ====================
    publish_published,
    publish_revoked,
    publish_reset,
    publish_plan_created,

    // ==================== 应用管理 ====================
    app_created,
    app_updated,
    app_deleted,
    app_published,
    app_config_updated,
    app_bind_kb,
    app_unbind_kb,
    app_bind_db,
    app_unbind_db,
    app_bind_skill,
    app_unbind_skill,
    app_bind_tool,
    app_unbind_tool,
    app_bind_mcp,
    app_unbind_mcp,
    app_trigger_created,
    app_trigger_updated,
    app_trigger_deleted,
    app_dialog_test_created,
    app_dialog_test_updated,
    app_dialog_test_deleted,
    app_optimization_created,
    app_optimization_updated,
    app_optimization_deleted,
    app_optimization_applied,

    // ==================== 工作流管理 ====================
    workflow_created,
    workflow_updated,
    workflow_deleted,
    workflow_published,
    workflow_node_added,
    workflow_node_updated,
    workflow_node_deleted,
    workflow_node_moved,
    workflow_template_created,
    workflow_template_updated,
    workflow_template_deleted,
    workflow_test_case_created,
    workflow_test_case_deleted,

    // ==================== Agent 管理 ====================
    agent_created,
    agent_updated,
    agent_deleted,
    agent_config_updated,
    agent_set_default,

    // ==================== IAM 用户管理 ====================
    user_created,
    user_updated,
    user_deleted,
    user_status_changed,
    user_role_assigned,
    role_created,
    role_updated,
    role_deleted,
    role_set_default,
    permission_created,
    permission_updated,
    permission_deleted,

    // ==================== 系统配置 ====================
    config_created,
    config_updated,
    config_deleted,
    config_imported,
    config_exported,
    config_reset_default,
    security_policy_created,
    security_policy_updated,
    security_policy_deleted,
    publish_strategy_created,
    publish_strategy_updated,
    publish_strategy_deleted,

    // ==================== 模型管理 ====================
    model_created,
    model_updated,
    model_deleted,
    model_imported,
    model_trained,
    model_tested,

    
    // ==================== 工具、技能、MCP ====================
    tool_created,
    tool_updated,
    tool_deleted,
    skill_created,
    skill_updated,
    skill_deleted,
    mcp_service_created,
    mcp_service_updated,
    mcp_service_deleted,
    db_instance_created,
    db_instance_updated,
    db_instance_deleted,

    // ==================== 对话管理 ====================
    conversation_deleted,

    // ==================== API Token ====================
    token_created,
    token_deleted,

    // ==================== 其他 ====================
    reset_config_saved,
    config_changed
}
