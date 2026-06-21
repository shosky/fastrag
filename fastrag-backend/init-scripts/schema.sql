-- FastRAG Database Schema
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
