-- H2-compatible schema for testing
SET MODE MySQL;

-- 工作流
CREATE TABLE IF NOT EXISTS workflow (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128),
    description VARCHAR(512),
    category VARCHAR(64),
    status VARCHAR(16) DEFAULT 'draft',
    nodes TEXT,
    edges TEXT,
    version VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_node (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32),
    node_key VARCHAR(64),
    node_type VARCHAR(32),
    name VARCHAR(128),
    position_x INT DEFAULT 0,
    position_y INT DEFAULT 0,
    enabled INT DEFAULT 1,
    config TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_test_case (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32),
    name VARCHAR(128),
    query TEXT,
    expected_output TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_template (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128),
    category VARCHAR(64),
    description VARCHAR(512),
    config TEXT,
    is_builtin INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_migration (
    id VARCHAR(32) PRIMARY KEY,
    source_workflow_id VARCHAR(32),
    target_env VARCHAR(32),
    status VARCHAR(16) DEFAULT 'running',
    progress INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_optimization (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32),
    title VARCHAR(256),
    description TEXT,
    impact VARCHAR(16),
    status VARCHAR(16) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==================== 知识库 ====================
CREATE TABLE IF NOT EXISTS kb (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    category VARCHAR(64),
    permission VARCHAR(16) DEFAULT 'public',
    creator VARCHAR(32),
    embedding_model VARCHAR(64),
    dimension INT DEFAULT 1024,
    type VARCHAR(16) DEFAULT 'team',
    tags CLOB,
    used_size BIGINT DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    retrieval_config CLOB,
    file_type_config CLOB,
    parse_mode VARCHAR(32),
    split_mode VARCHAR(32),
    graph_auto_build INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_category (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    color VARCHAR(16),
    icon VARCHAR(32),
    sort INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_acl (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32),
    kb_role VARCHAR(16),
    granted_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识库内容
CREATE TABLE IF NOT EXISTS kb_knowledge (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    title VARCHAR(256),
    content TEXT,
    category VARCHAR(64),
    status VARCHAR(16) DEFAULT 'draft',
    source VARCHAR(32),
    version VARCHAR(32),
    tags VARCHAR(256),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_knowledge_test (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    knowledge_id VARCHAR(32),
    test_query TEXT,
    expected_answer TEXT,
    actual_answer TEXT,
    test_model VARCHAR(64),
    is_passed INT DEFAULT 1,
    relevance_score DOUBLE DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_knowledge_dialog (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    knowledge_id VARCHAR(32),
    dialog_type VARCHAR(32),
    messages TEXT,
    result TEXT,
    confidence DOUBLE,
    created_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_knowledge_edit (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    knowledge_id VARCHAR(32),
    title VARCHAR(256),
    content TEXT,
    edit_type VARCHAR(16),
    status VARCHAR(16) DEFAULT 'draft',
    editor VARCHAR(64),
    reviewer VARCHAR(64),
    review_comment TEXT,
    tags VARCHAR(256),
    version INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 发布管理
CREATE TABLE IF NOT EXISTS kb_publish_history (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    knowledge_id VARCHAR(32),
    version VARCHAR(32),
    publish_type VARCHAR(16),
    status VARCHAR(16),
    operator VARCHAR(64),
    published_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_publish_plan (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    name VARCHAR(128),
    strategy VARCHAR(32),
    execution_status VARCHAR(16) DEFAULT 'idle',
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 监听器
CREATE TABLE IF NOT EXISTS kb_listener (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32),
    name VARCHAR(128),
    url VARCHAR(512),
    events TEXT,
    enabled INT DEFAULT 1,
    status VARCHAR(16) DEFAULT 'enabled',
    trigger_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 标签管理
CREATE TABLE IF NOT EXISTS kb_tag (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    color VARCHAR(16),
    description VARCHAR(256),
    usage_count INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS kb_tag_relation (
    id VARCHAR(32) PRIMARY KEY,
    tag_id VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL DEFAULT 'kb',
    target_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tag_id, target_type, target_id)
);

-- ==================== M16 应用配置 ====================
CREATE TABLE IF NOT EXISTS app (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description CLOB,
    type VARCHAR(32) DEFAULT 'ChatBot',
    icon VARCHAR(512),
    tags CLOB,
    status VARCHAR(16) DEFAULT 'draft',
    owner VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    model VARCHAR(128),
    prompt CLOB,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    knowledge_ids CLOB,
    tool_ids CLOB,
    max_turns INT DEFAULT 10
    , summary_threshold INT DEFAULT 8000
    , summary_prompt CLOB
    , max_steps INT DEFAULT 15
    , retry_times INT DEFAULT 2
    , max_tokens INT DEFAULT 2048
);

CREATE TABLE IF NOT EXISTS app_template (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description CLOB,
    type VARCHAR(32),
    config_snapshot CLOB,
    usage_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS app_basic_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    memory_rounds INT DEFAULT 5,
    output_format VARCHAR(16) DEFAULT 'markdown',
    response_language VARCHAR(16) DEFAULT 'zh-CN',
    greeting CLOB,
    goodbye_message CLOB,
    timeout_seconds INT DEFAULT 30,
    max_input_length INT DEFAULT 2000,
    advanced_options CLOB,
    updated_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_dialog_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    background_color VARCHAR(16),
    background_image VARCHAR(512),
    bubble_style VARCHAR(16),
    show_avatar INT DEFAULT 1,
    show_feedback INT DEFAULT 1,
    show_suggestions INT DEFAULT 1,
    updated_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_trigger (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    trigger_type VARCHAR(32) DEFAULT 'keyword',
    match_content CLOB,
    action_type VARCHAR(32),
    action_config CLOB,
    enabled INT DEFAULT 1,
    priority INT DEFAULT 0,
    hit_count INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_global_policy (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    safety_enabled INT DEFAULT 1,
    sensitive_word_mode VARCHAR(16) DEFAULT 'reject',
    fallback_text CLOB,
    unmatched_enabled INT DEFAULT 1,
    unmatched_action VARCHAR(32) DEFAULT 'fallback',
    unmatched_config CLOB,
    updated_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_variable (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    var_key VARCHAR(64) NOT NULL,
    var_type VARCHAR(16) DEFAULT 'string',
    default_value CLOB,
    description CLOB,
    created_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_kb_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    kb_id VARCHAR(32) NOT NULL,
    priority INT DEFAULT 0,
    filter_tags CLOB,
    enabled INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (app_id, kb_id)
);

CREATE TABLE IF NOT EXISTS app_db_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    db_id VARCHAR(32) NOT NULL,
    alias VARCHAR(128),
    allowed_tables CLOB,
    enabled INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_publish_record (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    version INT,
    status VARCHAR(16) DEFAULT 'draft',
    scope_type VARCHAR(32),
    scope_value CLOB,
    config_snapshot CLOB,
    published_at TIMESTAMP,
    operator VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_dialog_test (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    name VARCHAR(256),
    query CLOB,
    expected_answer CLOB,
    actual_answer CLOB,
    matched INT,
    similarity DOUBLE,
    tags CLOB,
    created_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_optimization (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    suggestion_type VARCHAR(32),
    title VARCHAR(256),
    description CLOB,
    impact_score DOUBLE,
    status VARCHAR(16) DEFAULT 'pending',
    before_metric CLOB,
    after_metric CLOB,
    created_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_kb_auto_update_config (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL UNIQUE,
    enabled INT DEFAULT 0,
    cron_expr VARCHAR(64),
    auto_publish INT DEFAULT 0,
    notify_channels CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
	);
	
-- 对话记录
CREATE TABLE IF NOT EXISTS app_conversation (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    session_id VARCHAR(64),
    user_id VARCHAR(32),
    user_name VARCHAR(128),
    title VARCHAR(256),
    firstQuestion CLOB,
    answerSummary CLOB,
    message_count INT DEFAULT 0,
    token_count INT DEFAULT 0,
    rating INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conv_app ON app_conversation(app_id);
CREATE INDEX IF NOT EXISTS idx_conv_created ON app_conversation(created_at);

CREATE TABLE IF NOT EXISTS app_conversation_message (
    id VARCHAR(32) PRIMARY KEY,
    conversation_id VARCHAR(32) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content CLOB,
    tokens INT DEFAULT 0,
    latency_ms INT,
    feedback VARCHAR(16) DEFAULT NULL,
    deleted_at TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conv_msg_conv ON app_conversation_message(conversation_id);

-- 技能/工具/MCP 绑定
CREATE TABLE IF NOT EXISTS app_skill_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    skill_id VARCHAR(32) NOT NULL,
    skill_name VARCHAR(128),
    params CLOB,
    enabled INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_tool_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    tool_id VARCHAR(32) NOT NULL,
    tool_name VARCHAR(128),
    config CLOB,
    enabled INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_mcp_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    mcp_service_id VARCHAR(32) NOT NULL,
    mcp_service_name VARCHAR(128),
    enabled INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- M17 工作流补充
CREATE TABLE IF NOT EXISTS wf_debug_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(32) NOT NULL,
    node_key VARCHAR(64),
    level VARCHAR(16) DEFAULT 'debug',
    message CLOB,
    context CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_monitor_data (
    id VARCHAR(32) PRIMARY KEY,
    workflow_id VARCHAR(32) NOT NULL,
    metric_type VARCHAR(32),
    metric_value DECIMAL(18,4),
    dimension VARCHAR(32),
    period_start TIMESTAMP,
    period_end TIMESTAMP,
    details CLOB
);

-- 配置
CREATE TABLE IF NOT EXISTS sys_config (
    id VARCHAR(32) PRIMARY KEY,
    config_key VARCHAR(128) UNIQUE,
    config_value TEXT,
    config_type VARCHAR(32),
    description VARCHAR(256),
    is_default INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_config_history (
    id VARCHAR(32) PRIMARY KEY,
    config_key VARCHAR(128),
    old_value TEXT,
    new_value TEXT,
    operator VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 模型
CREATE TABLE IF NOT EXISTS model (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(128),
    purpose VARCHAR(32),
    brand VARCHAR(64),
    api_url VARCHAR(512),
    api_key_ref VARCHAR(256),
    status VARCHAR(16) DEFAULT 'offline'
);

-- 工具
CREATE TABLE IF NOT EXISTS tool (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    identifier VARCHAR(128),
    description TEXT,
    type VARCHAR(16) DEFAULT 'http',
    tags CLOB,
    icon VARCHAR(512),
    enabled INT DEFAULT 1,
    inputs CLOB,
    outputs CLOB,
    output_mapping VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tool_http_config (
    tool_id VARCHAR(32) PRIMARY KEY,
    method VARCHAR(8) DEFAULT 'GET',
    url VARCHAR(512),
    auth_type VARCHAR(16) DEFAULT 'none',
    params CLOB,
    headers CLOB,
    body_type VARCHAR(32) DEFAULT 'none',
    body TEXT,
    auth_value VARCHAR(512)
);

-- MCP 服务
CREATE TABLE IF NOT EXISTS mcp_service (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(64),
    transport VARCHAR(16) DEFAULT 'sse',
    mcp_url VARCHAR(512),
    command VARCHAR(256),
    args CLOB,
    env CLOB,
    auth_type VARCHAR(16) DEFAULT 'none',
    auth_value VARCHAR(256),
    status VARCHAR(16) DEFAULT 'offline',
    enabled INT DEFAULT 1,
    is_builtin INT DEFAULT 0,
    config_hash VARCHAR(64),
    metadata CLOB,
    last_used TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mcp_tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    tool_id VARCHAR(128),
    description TEXT,
    params CLOB,
    enabled INT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS mcp_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id VARCHAR(32) NOT NULL,
    caller VARCHAR(64),
    tool VARCHAR(128),
    status VARCHAR(16),
    duration INT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 技能
CREATE TABLE IF NOT EXISTS skill (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    slug VARCHAR(64),
    identifier VARCHAR(128),
    description TEXT,
    icon VARCHAR(512),
    source_type VARCHAR(16) DEFAULT 'custom',
    source VARCHAR(16) DEFAULT 'custom',
    category VARCHAR(64),
    "trigger" VARCHAR(256),
    content TEXT,
    code_type VARCHAR(16) DEFAULT 'python',
    code TEXT,
    inputs CLOB,
    outputs CLOB,
    dir_path VARCHAR(512),
    dependencies CLOB,
    content_hash VARCHAR(64),
    is_builtin INT DEFAULT 0,
    metadata CLOB,
    `share_config` CLOB DEFAULT NULL COMMENT '分享配置 JSON',
    enabled INT DEFAULT 1,
    recommended INT DEFAULT 0,
    usage_count INT DEFAULT 0,
    author VARCHAR(64),
    version VARCHAR(16),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS skill_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id VARCHAR(32) NOT NULL,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(128) NOT NULL,
    required INT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS skill_scope (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id VARCHAR(32) NOT NULL,
    scope_id VARCHAR(32),
    scope_name VARCHAR(128),
    enabled INT DEFAULT 1
);

-- 数据库实例
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
    pool_config CLOB,
    read_only INT DEFAULT 1,
    status VARCHAR(16) DEFAULT 'connected',
    created_by VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS db_table (
    id VARCHAR(32) PRIMARY KEY,
    db_id VARCHAR(32) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    table_comment VARCHAR(256),
    columns CLOB,
    row_count BIGINT,
    enabled INT DEFAULT 1,
    synced_at TIMESTAMP
);
