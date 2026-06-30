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

-- 知识库
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
