-- 知识加工与采编功能补齐
-- 适用日期：2026-09-13
-- 内容：1) kb_knowledge 增加软删除字段（知识回收站）
--       2) 新增知识工单表 kb_knowledge_ticket
--       3) 新增事项知识关联表 kb_matter_knowledge_rel
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，此处用 information_schema 判断实现幂等
USE fastrag2;

-- 1) kb_knowledge.deleted_at
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge' AND COLUMN_NAME = 'deleted_at');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE kb_knowledge ADD COLUMN deleted_at DATETIME DEFAULT NULL',
    'SELECT ''kb_knowledge.deleted_at 已存在，跳过'' AS result');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 知识工单表
CREATE TABLE IF NOT EXISTS kb_knowledge_ticket (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    ticket_type VARCHAR(16) DEFAULT 'other',
    priority VARCHAR(16) DEFAULT 'medium',
    knowledge_id VARCHAR(32),
    assignee VARCHAR(32),
    reporter VARCHAR(32),
    status VARCHAR(16) DEFAULT 'open',
    remark TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at DATETIME,
    INDEX idx_kb_id (kb_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 事项知识关联表
CREATE TABLE IF NOT EXISTS kb_matter_knowledge_rel (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    matter_name VARCHAR(128) NOT NULL,
    matter_code VARCHAR(64),
    knowledge_id VARCHAR(32),
    knowledge_title VARCHAR(256),
    relation_type VARCHAR(16) DEFAULT 'reference',
    remark TEXT,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id),
    INDEX idx_matter (matter_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
