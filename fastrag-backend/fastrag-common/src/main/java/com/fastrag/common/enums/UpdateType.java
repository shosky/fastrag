package com.fastrag.common.enums;

/**
 * 知识库内容更新类型枚举
 * <p>用于 {@code kb_update_log} 表的 updateType 字段，标识变更的类型。
 */
public enum UpdateType {
    // 文件生命周期
    file_added,
    file_updated,
    file_removed,
    file_restored,
    file_permanent_deleted,
    file_copied,
    file_retried,

    // 切片
    chunk_added,
    chunk_removed,
    chunk_updated,

    // 文件夹
    folder_created,

    // QA 对
    qa_pair_created,
    qa_pair_updated,
    qa_pair_deleted,
    qa_pair_confirmed,

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

    // 配置
    config_changed,
    reset_config_saved,
    publish_plan_created
}
