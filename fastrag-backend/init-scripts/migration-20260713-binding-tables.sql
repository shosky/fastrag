-- ============================================================
-- Migration: 新增技能/工具/MCP 绑定表 + 对话记录表
-- 针对已有 MySQL 数据库执行
-- 运行方式: mysql -h 127.0.0.1 -u root -p fastrag < 此文件
-- ============================================================

SET NAMES utf8mb4;

-- 技能绑定表（替代 app_config.toolIds 逗号字符串方案）
CREATE TABLE IF NOT EXISTS app_skill_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    skill_id VARCHAR(32) NOT NULL,
    skill_name VARCHAR(128),
    params JSON,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 工具绑定表
CREATE TABLE IF NOT EXISTS app_tool_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    tool_id VARCHAR(32) NOT NULL,
    tool_name VARCHAR(128),
    config JSON,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MCP 绑定表
CREATE TABLE IF NOT EXISTS app_mcp_binding (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    mcp_service_id VARCHAR(32) NOT NULL,
    mcp_service_name VARCHAR(128),
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mcp_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对话记录表
CREATE TABLE IF NOT EXISTS app_conversation (
    id VARCHAR(32) PRIMARY KEY,
    app_id VARCHAR(32) NOT NULL,
    session_id VARCHAR(64),
    user_id VARCHAR(32),
    user_name VARCHAR(128),
    title VARCHAR(256),
    first_question TEXT,
    answer_summary TEXT,
    message_count INT DEFAULT 0,
    token_count INT DEFAULT 0,
    rating TINYINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conv_app (app_id),
    INDEX idx_conv_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对话消息表
CREATE TABLE IF NOT EXISTS app_conversation_message (
    id VARCHAR(32) PRIMARY KEY,
    conversation_id VARCHAR(32) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT,
    tokens INT DEFAULT 0,
    latency_ms INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_msg_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SELECT 'Migration completed successfully' AS status;
SHOW TABLES LIKE 'app_%';
