-- FastRAG Database Schema
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS fastrag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fastrag;

-- ==================== IAM ====================
CREATE TABLE IF NOT EXISTS sys_user (
    id VARCHAR(32) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    real_name VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    password_hash VARCHAR(256) NOT NULL,
    role_id VARCHAR(32),
    status VARCHAR(16) DEFAULT 'enabled',
    org_id VARCHAR(32),
    storage_quota BIGINT DEFAULT 10737418240,
    storage_used BIGINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id VARCHAR(32) PRIMARY KEY,
    role_key VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    is_default TINYINT DEFAULT 0,
    is_system TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    `group` VARCHAR(32),
    parent_key VARCHAR(64),
    description VARCHAR(256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id VARCHAR(32) NOT NULL,
    permission_key VARCHAR(64) NOT NULL,
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_org (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    alias VARCHAR(64),
    parent_id VARCHAR(32) DEFAULT 'root',
    level INT DEFAULT 1,
    sort INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_team (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_team_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    INDEX idx_team_id (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_acl (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    kb_role VARCHAR(16) NOT NULL,
    granted_by VARCHAR(32),
    granted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Knowledge Base ====================
CREATE TABLE IF NOT EXISTS kb (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    category VARCHAR(64),
    tags JSON,
    embedding_model VARCHAR(128),
    dimension INT,
    creator VARCHAR(32),
    type VARCHAR(16) DEFAULT 'personal',
    permission VARCHAR(16) DEFAULT 'private',
    used_size BIGINT DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    retrieval_config JSON,
    file_type_config JSON,
    parse_mode VARCHAR(16) DEFAULT 'auto',
    split_mode VARCHAR(16) DEFAULT 'auto',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_file (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(256) NOT NULL,
    category VARCHAR(16) DEFAULT 'document',
    extension VARCHAR(16),
    size BIGINT DEFAULT 0,
    object_key VARCHAR(512),
    status VARCHAR(16) DEFAULT 'pending',
    progress INT DEFAULT 0,
    stage VARCHAR(64),
    duration INT,
    pages INT,
    parse_strategy_id VARCHAR(32),
    parse_strategy_name VARCHAR(128),
    chunk_count INT DEFAULT 0,
    folder_id VARCHAR(32),
    view_count BIGINT DEFAULT 0,
    deleted_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_folder (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    parent_id VARCHAR(32) DEFAULT 'root',
    sort INT DEFAULT 0,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_chunk (
    id VARCHAR(64) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    file_id VARCHAR(32) NOT NULL,
    file_name VARCHAR(256),
    chunk_index INT NOT NULL,
    content MEDIUMTEXT,
    embedding_id VARCHAR(64),
    vector_stored TINYINT DEFAULT 0,
    start_time DOUBLE DEFAULT NULL COMMENT '开始时间(秒)',
    end_time DOUBLE DEFAULT NULL COMMENT '结束时间(秒)',
    page_number INT DEFAULT NULL COMMENT 'PDF 页码',
    page_range VARCHAR(16) DEFAULT NULL COMMENT '页码范围',
    image_keys JSON DEFAULT NULL COMMENT '关联图片 key',
    chunk_type VARCHAR(16) DEFAULT 'text' COMMENT '分片类型(text/image)',
    INDEX idx_kb_id (kb_id),
    INDEX idx_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_parse_strategy (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    extensions JSON,
    parse_method VARCHAR(16) DEFAULT 'default',
    is_default TINYINT DEFAULT 0,
    advanced JSON,
    llm_model VARCHAR(128),
    vlm_model VARCHAR(128),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_qa_pair (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    file_id VARCHAR(32),
    file_name VARCHAR(256),
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    source VARCHAR(16) DEFAULT 'manual',
    status VARCHAR(16) DEFAULT 'draft',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Graph & Evaluation ====================
CREATE TABLE IF NOT EXISTS kb_graph_index (
    kb_id VARCHAR(32) PRIMARY KEY,
    status VARCHAR(16) DEFAULT 'idle',
    build_progress INT DEFAULT 0,
    entity_extract_progress INT DEFAULT 0,
    relation_extract_progress INT DEFAULT 0,
    total_chunks INT DEFAULT 0,
    built_chunks INT DEFAULT 0,
    entity_count INT DEFAULT 0,
    relation_count INT DEFAULT 0,
    index_version INT DEFAULT 0,
    last_built_at DATETIME,
    build_error TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_benchmark (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    has_gold_chunks TINYINT DEFAULT 0,
    has_gold_answer TINYINT DEFAULT 0,
    is_auto_generated TINYINT DEFAULT 0,
    question_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_benchmark_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    benchmark_id VARCHAR(32) NOT NULL,
    question_index INT,
    question TEXT NOT NULL,
    gold_chunks VARCHAR(512),
    gold_answer TEXT,
    INDEX idx_benchmark_id (benchmark_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_evaluation (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    name VARCHAR(128),
    benchmark VARCHAR(128),
    benchmark_count INT,
    data_count INT,
    completed_count INT,
    duration BIGINT,
    recall_at_1 DECIMAL(5,4),
    recall_at_3 DECIMAL(5,4),
    recall_at_5 DECIMAL(5,4),
    recall_at_10 DECIMAL(5,4),
    answer_accuracy DECIMAL(5,4),
    overall_score DECIMAL(5,4),
    status VARCHAR(16) DEFAULT 'pending',
    run_id VARCHAR(64),
    answer_model VARCHAR(128),
    judge_model VARCHAR(128),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_evaluation_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evaluation_id VARCHAR(32) NOT NULL,
    question TEXT,
    generated_answer TEXT,
    retrieval_metrics JSON,
    is_correct TINYINT,
    judge_reason TEXT,
    INDEX idx_evaluation_id (evaluation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Publish & Logs ====================
CREATE TABLE IF NOT EXISTS kb_version (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(128),
    description TEXT,
    tags JSON,
    file_count INT DEFAULT 0,
    chunk_count INT DEFAULT 0,
    publish_status VARCHAR(16) DEFAULT 'draft',
    change_summary TEXT,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_review_task (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    kb_name VARCHAR(128),
    version_id VARCHAR(32),
    version INT,
    applicant VARCHAR(64),
    reviewer VARCHAR(64),
    status VARCHAR(16) DEFAULT 'pending',
    comment TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_log (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    category VARCHAR(16) NOT NULL,
    action VARCHAR(64),
    target VARCHAR(256),
    detail TEXT,
    operator VARCHAR(64),
    status VARCHAR(16),
    extra JSON,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_update_log (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    update_type VARCHAR(32) NOT NULL,
    target VARCHAR(256),
    detail TEXT,
    old_value TEXT,
    new_value TEXT,
    operator VARCHAR(64),
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Application ====================
CREATE TABLE IF NOT EXISTS app (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    type VARCHAR(32) DEFAULT 'ChatBot',
    icon VARCHAR(512),
    tags JSON,
    status VARCHAR(16) DEFAULT 'draft',
    owner VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    model VARCHAR(128),
    prompt TEXT,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    knowledge_ids JSON,
    tool_ids JSON,
    max_turns INT DEFAULT 10
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_template (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    type VARCHAR(32),
    usage_count INT DEFAULT 0,
    config_snapshot JSON
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workflow (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(16) DEFAULT 'draft',
    nodes JSON,
    edges JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Tools / Skills / MCP ====================
CREATE TABLE IF NOT EXISTS tool (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    identifier VARCHAR(128),
    description TEXT,
    type VARCHAR(16) DEFAULT 'http',
    tags JSON,
    icon VARCHAR(512),
    enabled TINYINT DEFAULT 1,
    inputs JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tool_http_config (
    tool_id VARCHAR(32) PRIMARY KEY,
    method VARCHAR(8) DEFAULT 'GET',
    url VARCHAR(512),
    auth_type VARCHAR(16) DEFAULT 'none',
    params JSON,
    headers JSON,
    body_type VARCHAR(32) DEFAULT 'none',
    body TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS skill (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    identifier VARCHAR(128),
    description TEXT,
    icon VARCHAR(512),
    source VARCHAR(16) DEFAULT 'custom',
    category VARCHAR(64),
    `trigger` VARCHAR(256),
    content TEXT,
    code_type VARCHAR(16) DEFAULT 'python',
    code TEXT,
    inputs JSON,
    outputs JSON,
    enabled TINYINT DEFAULT 1,
    recommended TINYINT DEFAULT 0,
    usage_count INT DEFAULT 0,
    author VARCHAR(64),
    version VARCHAR(16),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS skill_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id VARCHAR(32) NOT NULL,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(128) NOT NULL,
    required TINYINT DEFAULT 1,
    INDEX idx_skill_id (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS skill_scope (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id VARCHAR(32) NOT NULL,
    scope_id VARCHAR(32),
    scope_name VARCHAR(128),
    enabled TINYINT DEFAULT 1,
    INDEX idx_skill_id (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mcp_service (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    mcp_url VARCHAR(512),
    auth_type VARCHAR(16) DEFAULT 'none',
    auth_value VARCHAR(256),
    status VARCHAR(16) DEFAULT 'offline',
    enabled TINYINT DEFAULT 1,
    last_used DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mcp_tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    params JSON,
    INDEX idx_service_id (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mcp_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id VARCHAR(32) NOT NULL,
    caller VARCHAR(64),
    tool VARCHAR(128),
    status VARCHAR(16),
    duration INT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_service_id (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Platform Config ====================
CREATE TABLE IF NOT EXISTS model (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(128),
    purpose VARCHAR(32),
    brand VARCHAR(64),
    api_url VARCHAR(512),
    api_key_ref VARCHAR(256),
    status VARCHAR(16) DEFAULT 'offline'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_training (
    id VARCHAR(32) PRIMARY KEY,
    model_id VARCHAR(32) NOT NULL,
    model_name VARCHAR(128),
    dataset VARCHAR(128),
    epochs INT,
    status VARCHAR(16) DEFAULT 'running',
    duration BIGINT,
    metrics JSON,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_test_report (
    id VARCHAR(32) PRIMARY KEY,
    model_id VARCHAR(32) NOT NULL,
    model_name VARCHAR(128),
    test_dataset VARCHAR(128),
    test_count INT,
    score VARCHAR(32),
    metrics JSON,
    status VARCHAR(16),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_id VARCHAR(32) NOT NULL,
    caller VARCHAR(64),
    status VARCHAR(16),
    duration INT,
    tokens INT,
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS term_library (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    term_count INT DEFAULT 0,
    owner VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS term_record (
    id VARCHAR(32) PRIMARY KEY,
    library_id VARCHAR(32) NOT NULL,
    term VARCHAR(128) NOT NULL,
    alias VARCHAR(256),
    definition TEXT,
    category VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_library_id (library_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS query_rule (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    rule_type VARCHAR(16) NOT NULL,
    pattern VARCHAR(256),
    action VARCHAR(256),
    enabled TINYINT DEFAULT 1,
    priority INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sensitive_word (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    word VARCHAR(128) NOT NULL,
    category VARCHAR(64),
    level VARCHAR(16),
    replacement VARCHAR(128) DEFAULT '***',
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_type VARCHAR(64) NOT NULL,
    dict_key VARCHAR(128) NOT NULL,
    dict_value TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Operations & Audit ====================
CREATE TABLE IF NOT EXISTS user_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(32),
    user_id VARCHAR(32),
    kb_id VARCHAR(32),
    app_id VARCHAR(32),
    query TEXT,
    answer TEXT,
    feedback VARCHAR(16) DEFAULT 'none',
    score INT,
    comment TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(32) PRIMARY KEY,
    user_id VARCHAR(32),
    kb_id VARCHAR(32),
    app_id VARCHAR(32),
    query TEXT,
    answer TEXT,
    retrieved_chunks JSON,
    model VARCHAR(128),
    duration BIGINT,
    tokens INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32),
    username VARCHAR(64),
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    ip VARCHAR(64),
    device VARCHAR(128),
    os VARCHAR(64),
    browser VARCHAR(64),
    location VARCHAR(128),
    status VARCHAR(16) DEFAULT 'success',
    fail_reason VARCHAR(256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32),
    username VARCHAR(64),
    module VARCHAR(32),
    action VARCHAR(64),
    target VARCHAR(256),
    detail TEXT,
    ip VARCHAR(64),
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS login_security_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    min_password_length INT DEFAULT 8,
    require_special_char TINYINT DEFAULT 0,
    max_fail_attempts INT DEFAULT 5,
    lock_duration INT DEFAULT 30,
    session_timeout INT DEFAULT 1440,
    enable_2fa TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Workspace ====================
CREATE TABLE IF NOT EXISTS user_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id VARCHAR(32) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id VARCHAR(32) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_recent_visit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id VARCHAR(32) NOT NULL,
    kb_name VARCHAR(128),
    visit_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    modify_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_workspace_layout (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL UNIQUE,
    layout_json JSON
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M1 用户反馈扩展字段 ====================
ALTER TABLE user_feedback ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'pending';
ALTER TABLE user_feedback ADD COLUMN IF NOT EXISTS reply TEXT;
ALTER TABLE user_feedback ADD COLUMN IF NOT EXISTS processed_by VARCHAR(32);
ALTER TABLE user_feedback ADD COLUMN IF NOT EXISTS processed_at DATETIME;
ALTER TABLE user_feedback ADD COLUMN IF NOT EXISTS category VARCHAR(32);

-- ==================== M2 知识检索增强 ====================
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

-- ==================== M7 多媒体存储 + 问答抽取 ====================
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

-- ==================== M9 知识存储管理（标签/笔记） ====================
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

-- ==================== M10 知识管理 + M11 知识更新 ====================
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

CREATE TABLE IF NOT EXISTS kb_reset_config (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    role_key VARCHAR(64),
    can_reset TINYINT DEFAULT 0,
    max_reset_count INT DEFAULT 3,
    config JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== M16 应用配置（机器人配置） ====================
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

-- ==================== M20 知识库分类 & 通知 ====================
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

CREATE TABLE IF NOT EXISTS sys_notification (
    id VARCHAR(32) PRIMARY KEY,
    title VARCHAR(256),
    content TEXT,
    notify_type VARCHAR(32),
    source_type VARCHAR(32),
    source_id VARCHAR(32),
    target_user VARCHAR(32),
    status VARCHAR(16) DEFAULT 'unread',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_target_user (target_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Global Security & Publish Strategy ====================
CREATE TABLE IF NOT EXISTS sys_security_policy (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    policy_type VARCHAR(32) NOT NULL,
    pattern VARCHAR(512),
    action VARCHAR(16) DEFAULT 'block',
    priority INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_policy_type (policy_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_publish_strategy (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    strategy_type VARCHAR(32) NOT NULL,
    config JSON,
    priority INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_strategy_type (strategy_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
