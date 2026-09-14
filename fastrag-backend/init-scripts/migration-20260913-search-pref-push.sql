-- 知识检索补齐：检索偏好设置 + 知识推送
-- 适用日期：2026-09-13
USE fastrag2;

CREATE TABLE IF NOT EXISTS kb_search_preference (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    search_mode VARCHAR(16) DEFAULT 'hybrid',
    top_k INT DEFAULT 10,
    similarity_threshold DECIMAL(5,2) DEFAULT 0.5,
    prefer_tags JSON,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_user (kb_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_knowledge_push (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    content TEXT,
    knowledge_id VARCHAR(32),
    push_type VARCHAR(16) DEFAULT 'manual',
    target_users JSON,
    status VARCHAR(16) DEFAULT 'draft',
    pushed_at DATETIME,
    created_by VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
