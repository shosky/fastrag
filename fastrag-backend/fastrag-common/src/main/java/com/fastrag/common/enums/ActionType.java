package com.fastrag.common.enums;

/**
 * 知识库操作动作类型枚举
 * <p>统一所有知识库相关操作的动作标识，替代散落的字符串字面量。
 */
public enum ActionType {
    // 知识库本体
    kb_created,
    kb_updated,
    kb_deleted,

    // 文件生命周期
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

    // 文件夹
    folder_created,
    folder_updated,
    folder_deleted,

    // QA 对
    qa_pair_created,
    qa_pair_updated,
    qa_pair_deleted,
    qa_pair_confirmed,

    // 分片
    chunk_created,
    chunk_updated,
    chunk_deleted,

    // 解析策略
    strategy_created,
    strategy_updated,
    strategy_deleted,
    strategy_set_default,

    // 知识图谱
    graph_build_started,
    graph_build_completed,
    graph_build_failed,
    graph_build_retried,
    graph_deleted,
    graph_settings_updated,

    // 评测
    evaluation_run,
    evaluation_deleted,

    // 基准测试
    benchmark_created,
    benchmark_generated,
    benchmark_deleted,
    benchmark_imported,

    // 发布
    publish_published,
    publish_revoked,
    publish_reset,
    publish_plan_created,

    // 其他
    reset_config_saved,

    // 配置
    config_changed
}
