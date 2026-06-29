-- ============================================================================
-- 迁移脚本：补充 M3~M19 新增数据表（2026-06-28）
-- 用途：在已有数据库上创建 schema.sql 中新增的表，不会影响已有数据
-- 安全：使用存储过程逐列检测兼容低版本 MySQL
-- ============================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
USE fastrag;

-- 先删除已有的存储过程避免重复创建报错
DROP PROCEDURE IF EXISTS sp_add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE sp_add_column_if_not_exists(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    SET @db = DATABASE();
    SET @sql = CONCAT(
        "SELECT IF(COUNT(*)>0,'EXISTS','MISSING') INTO @res FROM information_schema.COLUMNS ",
        "WHERE TABLE_SCHEMA='", @db, "' AND TABLE_NAME='", p_table, "' AND COLUMN_NAME='", p_column, "'"
    );
    PREPARE s FROM @sql;
    EXECUTE s;
    DEALLOCATE PREPARE s;
    IF @res = 'MISSING' THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE s2 FROM @ddl;
        EXECUTE s2;
        DEALLOCATE PREPARE s2;
    END IF;
END //
DELIMITER ;

-- ==================== M2 知识检索增强（补充） ====================
CREATE TABLE IF NOT EXISTS kb_retrieval_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id VARCHAR(32),
    query VARCHAR(512),
    user_id VARCHAR(32),
    hit_count INT DEFAULT 0,
    top_score DECIMAL(5,2),
    latency_ms INT,
    has_result TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_time (kb_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_update_remind (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    enabled TINYINT DEFAULT 1,
    cron_expr VARCHAR(64) DEFAULT '0 9 * * *',
    channels JSON,
    last_remind_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M1 用户反馈扩展字段（补充） ====================
CALL sp_add_column_if_not_exists('user_feedback', 'status', "VARCHAR(16) DEFAULT 'pending'");
CALL sp_add_column_if_not_exists('user_feedback', 'reply', 'TEXT');
CALL sp_add_column_if_not_exists('user_feedback', 'processed_by', 'VARCHAR(32)');
CALL sp_add_column_if_not_exists('user_feedback', 'processed_at', 'DATETIME');
CALL sp_add_column_if_not_exists('user_feedback', 'category', 'VARCHAR(32)');

-- ==================== M3 实体库 ====================
CREATE TABLE IF NOT EXISTS kb_entity (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    name VARCHAR(128) NOT NULL,
    entity_type VARCHAR(32) DEFAULT 'enum',
    description TEXT,
    values_json JSON,
    creator VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M6 数据挖掘 ====================
CREATE TABLE IF NOT EXISTS data_mining_task (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    kb_id VARCHAR(32),
    rule_type VARCHAR(32),
    rule_config JSON,
    status VARCHAR(16) DEFAULT 'enabled',
    last_run_at DATETIME,
    result_summary JSON,
    creator VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M7 多媒体存储 ====================
CREATE TABLE IF NOT EXISTS kb_media_storage (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    media_type VARCHAR(16) NOT NULL,
    name VARCHAR(256) NOT NULL,
    original_name VARCHAR(256),
    extension VARCHAR(16),
    size BIGINT DEFAULT 0,
    object_key VARCHAR(512),
    duration INT,
    resolution VARCHAR(32),
    width INT,
    height INT,
    thumbnail_key VARCHAR(512),
    ocr_text TEXT,
    transcript LONGTEXT,
    description TEXT,
    tags JSON,
    status VARCHAR(16) DEFAULT 'uploaded',
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_media_type (media_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M7 问答抽取任务 ====================
CREATE TABLE IF NOT EXISTS kb_qa_extract_task (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(128),
    source_type VARCHAR(16),
    source_ids JSON,
    llm_model VARCHAR(128),
    total_count INT DEFAULT 0,
    completed_count INT DEFAULT 0,
    status VARCHAR(16) DEFAULT 'pending',
    result JSON,
    params JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M8 知识加工与采编 ====================
CREATE TABLE IF NOT EXISTS kb_knowledge_edit (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    knowledge_id VARCHAR(32),
    title VARCHAR(256) NOT NULL,
    content LONGTEXT NOT NULL,
    edit_type VARCHAR(16),
    tags JSON,
    status VARCHAR(16) DEFAULT 'draft',
    editor VARCHAR(32),
    reviewer VARCHAR(32),
    review_comment TEXT,
    version INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_knowledge_validate (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    validate_type VARCHAR(32),
    target_scope VARCHAR(32),
    target_value VARCHAR(256),
    total_count INT DEFAULT 0,
    passed_count INT DEFAULT 0,
    warning_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    result JSON,
    status VARCHAR(16) DEFAULT 'pending',
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M9 标签与笔记 ====================
CREATE TABLE IF NOT EXISTS kb_tag_type (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    color VARCHAR(16),
    icon VARCHAR(64),
    sort INT DEFAULT 0,
    is_system TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_tag (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    tag_type_id VARCHAR(32),
    name VARCHAR(128) NOT NULL,
    color VARCHAR(16),
    description TEXT,
    usage_count INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_tag_type_id (tag_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_tag_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_id VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id VARCHAR(32) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tag_id (tag_id),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_note (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content LONGTEXT,
    target_type VARCHAR(16),
    target_id VARCHAR(32),
    tags JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M10 知识管理 ====================
CREATE TABLE IF NOT EXISTS kb_knowledge (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content LONGTEXT NOT NULL,
    summary TEXT,
    category VARCHAR(64),
    tags JSON,
    source VARCHAR(16) DEFAULT 'manual',
    source_id VARCHAR(32),
    version INT DEFAULT 1,
    status VARCHAR(16) DEFAULT 'draft',
    quality_score DECIMAL(5,2),
    view_count INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_knowledge_test (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    knowledge_id VARCHAR(32) NOT NULL,
    test_query TEXT NOT NULL,
    expected_answer TEXT,
    actual_answer TEXT,
    relevance_score DECIMAL(5,2),
    is_passed TINYINT,
    test_model VARCHAR(128),
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_knowledge_dialog (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    knowledge_id VARCHAR(32) NOT NULL,
    dialog_type VARCHAR(16),
    messages JSON,
    result VARCHAR(16),
    confidence DECIMAL(5,2),
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M11 知识更新 ====================
CREATE TABLE IF NOT EXISTS kb_knowledge_update (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    knowledge_id VARCHAR(32),
    update_type VARCHAR(16) NOT NULL,
    title VARCHAR(256),
    old_value LONGTEXT,
    new_value LONGTEXT,
    change_summary TEXT,
    status VARCHAR(16) DEFAULT 'pending',
    applied_at DATETIME,
    operator VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M12 智能搜索 ====================
CREATE TABLE IF NOT EXISTS kb_search_association (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    dimension VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    pattern VARCHAR(256),
    suggestions JSON,
    priority INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    conditions JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_dimension (dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_auto_correction (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    wrong_text VARCHAR(256) NOT NULL,
    correct_text VARCHAR(256) NOT NULL,
    match_type VARCHAR(16) DEFAULT 'exact',
    priority INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    hit_count INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M13 知识问答 ====================
CREATE TABLE IF NOT EXISTS kb_multi_turn_qa (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    turns JSON NOT NULL,
    category VARCHAR(64),
    tags JSON,
    status VARCHAR(16) DEFAULT 'active',
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_multimodal_qa (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT,
    modal_type VARCHAR(16),
    media_ids JSON,
    category VARCHAR(64),
    tags JSON,
    status VARCHAR(16) DEFAULT 'active',
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_doc_guide (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    file_id VARCHAR(32),
    title VARCHAR(256) NOT NULL,
    summary TEXT,
    outline JSON,
    key_points JSON,
    index_status VARCHAR(16) DEFAULT 'pending',
    index_progress INT DEFAULT 0,
    category VARCHAR(64),
    tags JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M14 系统设置管理 ====================
CREATE TABLE IF NOT EXISTS sys_config (
    id VARCHAR(32) PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(32),
    description TEXT,
    is_default TINYINT DEFAULT 0,
    is_system TINYINT DEFAULT 0,
    updated_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_config_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id VARCHAR(32) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    change_type VARCHAR(16),
    operator VARCHAR(32),
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_config_id (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M15 知识审核管理 ====================
CREATE TABLE IF NOT EXISTS kb_publish_history (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    knowledge_id VARCHAR(32) NOT NULL,
    version INT,
    publish_type VARCHAR(16),
    online_version LONGTEXT,
    offline_version LONGTEXT,
    status VARCHAR(16),
    scheduled_at DATETIME,
    published_at DATETIME,
    operator VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_knowledge_id (knowledge_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_publish_plan (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(256) NOT NULL,
    knowledge_ids JSON,
    strategy VARCHAR(32),
    scheduled_time DATETIME,
    executed_time DATETIME,
    execution_status VARCHAR(16),
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    execution_detail JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_execution_status (execution_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_review_strategy (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    name VARCHAR(128) NOT NULL,
    strategy_type VARCHAR(32),
    config JSON,
    enabled TINYINT DEFAULT 1,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_compliance_rule (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    rule_name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(32),
    pattern TEXT,
    action VARCHAR(32),
    severity VARCHAR(16),
    enabled TINYINT DEFAULT 1,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_quality_rule (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    rule_name VARCHAR(128) NOT NULL,
    metric VARCHAR(32),
    threshold DECIMAL(10,2),
    weight DECIMAL(5,2),
    enabled TINYINT DEFAULT 1,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_review_template (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    description TEXT,
    flow_config JSON,
    is_builtin TINYINT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_review_node (
    id VARCHAR(32) PRIMARY KEY,
    template_id VARCHAR(32),
    node_name VARCHAR(128) NOT NULL,
    node_type VARCHAR(32),
    approver_role VARCHAR(64),
    order_num INT DEFAULT 0,
    config JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_listener (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    name VARCHAR(128) NOT NULL,
    listen_type VARCHAR(32),
    target VARCHAR(256),
    config JSON,
    status VARCHAR(16) DEFAULT 'enabled',
    last_run_at DATETIME,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_listener_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listener_id VARCHAR(32) NOT NULL,
    event_type VARCHAR(32),
    message TEXT,
    status VARCHAR(16),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_listener_id (listener_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_listener_alert (
    id VARCHAR(32) PRIMARY KEY,
    listener_id VARCHAR(32) NOT NULL,
    alert_type VARCHAR(32),
    config JSON,
    enabled TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_listener_id (listener_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_reset_config (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    role_key VARCHAR(64),
    can_reset TINYINT DEFAULT 0,
    max_reset_count INT DEFAULT 3,
    config JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M16 应用配置 ====================
CREATE TABLE IF NOT EXISTS app_basic_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    memory_rounds INT DEFAULT 5,
    output_format VARCHAR(16) DEFAULT 'markdown',
    response_language VARCHAR(16) DEFAULT 'zh-CN',
    greeting TEXT,
    goodbye_message TEXT,
    timeout_seconds INT DEFAULT 30,
    max_input_length INT DEFAULT 2000,
    advanced_options JSON,
    updated_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_dialog_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    background_color VARCHAR(16),
    background_image VARCHAR(512),
    bubble_style VARCHAR(16),
    show_avatar TINYINT DEFAULT 1,
    show_feedback TINYINT DEFAULT 1,
    show_suggestions TINYINT DEFAULT 1,
    updated_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_trigger (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    trigger_type VARCHAR(32) DEFAULT 'keyword',
    match_content TEXT,
    action_type VARCHAR(32),
    action_config JSON,
    enabled TINYINT DEFAULT 1,
    priority INT DEFAULT 0,
    hit_count INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_global_policy (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    safety_enabled TINYINT DEFAULT 1,
    sensitive_word_mode VARCHAR(16) DEFAULT 'reject',
    fallback_text TEXT,
    unmatched_enabled TINYINT DEFAULT 1,
    unmatched_action VARCHAR(32) DEFAULT 'fallback',
    unmatched_config JSON,
    updated_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_variable (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    var_key VARCHAR(64) NOT NULL,
    var_type VARCHAR(16) DEFAULT 'string',
    default_value TEXT,
    description TEXT,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_kb_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    kb_id VARCHAR(32) NOT NULL,
    priority INT DEFAULT 0,
    filter_tags JSON,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_app_kb (app_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_db_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    db_id VARCHAR(32) NOT NULL,
    alias VARCHAR(128),
    allowed_tables JSON,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_publish_record (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    version INT,
    status VARCHAR(16) DEFAULT 'draft',
    scope_type VARCHAR(32),
    scope_value JSON,
    config_snapshot LONGTEXT,
    published_at DATETIME,
    operator VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_monitor_data (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    metric_value DECIMAL(18,4),
    dimension VARCHAR(32),
    period_start DATETIME,
    period_end DATETIME,
    details JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_metric (app_id, metric_type, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_dialog_test (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    name VARCHAR(256),
    query TEXT,
    expected_answer TEXT,
    actual_answer TEXT,
    matched TINYINT,
    similarity DECIMAL(5,2),
    tags JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_debug_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    session_id VARCHAR(64),
    level VARCHAR(16) DEFAULT 'debug',
    module VARCHAR(32),
    message TEXT,
    context JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_level (app_id, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_kb_auto_update_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    enabled TINYINT DEFAULT 0,
    cron_expr VARCHAR(64),
    auto_publish TINYINT DEFAULT 0,
    notify_channels JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_optimization (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    suggestion_type VARCHAR(32),
    title VARCHAR(256),
    description TEXT,
    impact_score DECIMAL(5,2),
    status VARCHAR(16) DEFAULT 'pending',
    before_metric JSON,
    after_metric JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M17 业务流管理 ====================
CREATE TABLE IF NOT EXISTS wf_node (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32) NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    name VARCHAR(128),
    config JSON,
    position_x INT DEFAULT 0,
    position_y INT DEFAULT 0,
    next_nodes JSON,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_wf_key (workflow_id, node_key),
    INDEX idx_workflow_id (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wf_template (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(32),
    description TEXT,
    canvas_data LONGTEXT,
    is_builtin TINYINT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wf_test_case (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32) NOT NULL,
    name VARCHAR(256),
    query TEXT,
    inputs JSON,
    expected_output TEXT,
    actual_output TEXT,
    matched TINYINT,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wf_debug_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(32) NOT NULL,
    node_key VARCHAR(64),
    level VARCHAR(16) DEFAULT 'debug',
    message TEXT,
    context JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wf_monitor_data (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32) NOT NULL,
    metric_type VARCHAR(32),
    metric_value DECIMAL(18,4),
    period_start DATETIME,
    period_end DATETIME,
    details JSON
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wf_optimization (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32) NOT NULL,
    suggestion_type VARCHAR(32),
    title VARCHAR(256),
    description TEXT,
    impact_score DECIMAL(5,2),
    status VARCHAR(16) DEFAULT 'pending',
    before_metric JSON,
    after_metric JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wf_migration (
    id VARCHAR(32) PRIMARY KEY,
    source_workflow_id VARCHAR(32),
    target_workflow_id VARCHAR(32),
    target_env VARCHAR(32),
    strategy VARCHAR(32) DEFAULT 'overwrite',
    status VARCHAR(16) DEFAULT 'running',
    progress INT DEFAULT 0,
    validate_result JSON,
    operator VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M19 数据库管理 ====================
CREATE TABLE IF NOT EXISTS db_instance (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    db_type VARCHAR(16) NOT NULL,
    host VARCHAR(128),
    port INT,
    username VARCHAR(64),
    password VARCHAR(256),
    db_name VARCHAR(128),
    jdbc_url VARCHAR(512),
    pool_config JSON,
    read_only TINYINT DEFAULT 1,
    status VARCHAR(16) DEFAULT 'connected',
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS db_table (
    id VARCHAR(32) PRIMARY KEY,
    db_id VARCHAR(32) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    table_comment VARCHAR(256),
    columns JSON,
    row_count BIGINT,
    enabled TINYINT DEFAULT 1,
    synced_at DATETIME,
    INDEX idx_db_id (db_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 清理临时存储过程
DROP PROCEDURE IF EXISTS sp_add_column_if_not_exists;

-- kb_version.version 默认值（原 schema.sql 无 DEFAULT）
ALTER TABLE kb_version MODIFY COLUMN version INT DEFAULT 1;

-- ==================== 知识库分类表 ====================
CREATE TABLE IF NOT EXISTS kb_category (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    color VARCHAR(16),
    icon VARCHAR(64),
    sort INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO kb_category (id,name,description,color,icon,sort) VALUES
('cat_default','默认分类','未分类的知识库','#909399','Folder',0),
('cat_tech','技术文档','技术类知识库','#1890ff','Document',1),
('cat_product','产品手册','产品相关文档','#52c41a','Box',2),
('cat_faq','常见问题','FAQ知识库','#faad14','QuestionFilled',3);

-- ==================== 统一通知/日志表 ====================
CREATE TABLE IF NOT EXISTS sys_notification (
    id VARCHAR(32) PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    content TEXT,
    notify_type VARCHAR(32) NOT NULL COMMENT 'review_remind|publish_done|review_timeout|knowledge_update|system',
    source_type VARCHAR(32) COMMENT 'kb|app|workflow|system',
    source_id VARCHAR(32),
    target_user VARCHAR(32),
    status VARCHAR(16) DEFAULT 'unread' COMMENT 'unread|read|dismissed',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target_status (target_user, status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    module VARCHAR(32) NOT NULL COMMENT 'knowledge|retrieval|publish|app|workflow|system',
    operation VARCHAR(64) NOT NULL COMMENT 'create|update|delete|import|export|publish|review_approve|review_reject',
    resource_type VARCHAR(32),
    resource_id VARCHAR(32),
    operator VARCHAR(32),
    detail TEXT,
    result VARCHAR(16) DEFAULT 'success' COMMENT 'success|fail',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_module_op (module, operation),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 初始化示范数据（幂等） ====================
-- 标签类型
INSERT IGNORE INTO kb_tag_type (id,kb_id,name,color,sort,is_system) VALUES
('T1','kb_sample','文档分类','#1890ff',1,1),('T2','kb_sample','优先级','#faad14',2,1),('T3','kb_sample','状态','#52c41a',3,1);
-- 标签
INSERT IGNORE INTO kb_tag (id,kb_id,tag_type_id,name,color) VALUES
('t1','kb_sample','T1','技术文档','#1890ff'),('t2','kb_sample','T1','产品手册','#36cfc9'),
('t3','kb_sample','T2','高优先级','#faad14'),('t4','kb_sample','T2','普通','#d9d9d9'),
('t5','kb_sample','T3','已完成','#52c41a'),('t6','kb_sample','T3','进行中','#1890ff');

-- 合规规则
INSERT IGNORE INTO kb_compliance_rule (id,kb_id,rule_name,rule_type,pattern,enabled) VALUES
('c1','kb_sample','个人信息保护','keyword','身份证|手机号|银行卡',1),
('c2','kb_sample','长度限制','length','>2000字符',1),
('c3','kb_sample','敏感内容过滤','keyword','敏感词|违规|违法',1);

-- 质量规则
INSERT IGNORE INTO kb_quality_rule (id,kb_id,rule_name,metric,weight,threshold,enabled) VALUES
('q1','kb_sample','完整性','completeness',0.25,0.8,1),
('q2','kb_sample','准确性','accuracy',0.30,0.9,1),
('q3','kb_sample','时效性','freshness',0.20,0.7,1),
('q4','kb_sample','相关性','relevance',0.25,0.8,1);

-- 搜索关联（智能联想）
INSERT IGNORE INTO kb_search_association (id,kb_id,dimension,name,pattern,enabled) VALUES
('s1','kb_sample','keyword','关键词联想','退款|退货|换货',1),
('s2','kb_sample','topic','主题词联想','订单|物流|支付',1),
('s3','kb_sample','attachment','附件联想','.*发票.*|.*附件.*',1);

-- 自动纠错
INSERT IGNORE INTO kb_auto_correction (id,kb_id,wrong_text,correct_text,match_type,enabled) VALUES
('a1','kb_sample','退款','退款','exact',1),
('a2','kb_sample','收货地扯','收货地址','exact',1),
('a3','kb_sample','联糸','联系','exact',1);

-- 多轮问答
INSERT IGNORE INTO kb_multi_turn_qa (id,kb_id,title,description,category,turns,status) VALUES
('mt1','kb_sample','退货流程咨询','用户咨询退货流程的完整对话','售后','[{"q":"我想退货","a":"请问您的订单编号是多少？"},{"q":"DD2024001","a":"已查到您的订单，退货原因是什么呢？"},{"q":"商品有质量问题","a":"好的，已为您提交退货申请，请将商品寄回"}]','active');

-- 多模态问答
INSERT IGNORE INTO kb_multimodal_qa (id,kb_id,title,question,answer,modal_type,category,status) VALUES
('mm1','kb_sample','产品故障排查','怎么排查设备故障？','请按以下步骤操作：1.检查电源连接 2.重启设备 3.更新固件','text','技术','active');

-- 文档导读
INSERT IGNORE INTO kb_doc_guide (id,kb_id,title,summary,index_status) VALUES
('dg1','kb_sample','产品用户手册','本手册涵盖产品安装、配置、使用和故障排除','completed');
