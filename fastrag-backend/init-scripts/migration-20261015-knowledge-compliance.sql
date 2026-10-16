-- ============================================================
-- 5.4.6.2.4 知识采编 需求补齐 DDL（2026-10-15）
-- 说明：应用启动时 SchemaInitializer 也会幂等执行同样变更（旧库自愈）；
--       本文件供全新环境 / 手工执行 / 变更留档使用。
--       全部语句可重复执行：加列走 information_schema 判断 + 动态 SQL，建表走 IF NOT EXISTS。
-- 涉及需求点：
--   1) 知识回收：取消发布(offline)/失效(expired) 纳入回收站集中管理 → kb_knowledge.valid_until
--   2) 知识工单：自动生成 + 审批 + 流程环节 → 工单审批字段 + kb_knowledge_ticket_log
--   3) 事项知识关联：热点推荐 → kb_matter_knowledge_hit 埋点表
-- ============================================================

-- 1) 知识失效时间（到期后由每日 01:30 扫描置为 expired；也可 POST /api/kb/{kbId}/knowledge/expire-scan 手动触发）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_knowledge ADD COLUMN valid_until DATETIME DEFAULT NULL',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge' AND COLUMN_NAME = 'valid_until');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 知识工单审批字段
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_knowledge_ticket ADD COLUMN approver VARCHAR(32) NULL',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge_ticket' AND COLUMN_NAME = 'approver');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_knowledge_ticket ADD COLUMN submitted_at DATETIME NULL',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge_ticket' AND COLUMN_NAME = 'submitted_at');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_knowledge_ticket ADD COLUMN approved_at DATETIME NULL',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge_ticket' AND COLUMN_NAME = 'approved_at');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_knowledge_ticket ADD COLUMN approval_comment VARCHAR(512) NULL',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge_ticket' AND COLUMN_NAME = 'approval_comment');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_knowledge_ticket ADD COLUMN flow_name VARCHAR(64) NULL',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge_ticket' AND COLUMN_NAME = 'flow_name');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_knowledge_ticket ADD COLUMN current_step VARCHAR(64) NULL',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_knowledge_ticket' AND COLUMN_NAME = 'current_step');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- 2.1) 工单流程环节日志（提交/审批/驳回步骤，供「流程环节」时间轴展示）
CREATE TABLE IF NOT EXISTS kb_knowledge_ticket_log (
    id VARCHAR(32) PRIMARY KEY,
    ticket_id VARCHAR(32) NOT NULL,
    step_name VARCHAR(64),
    action VARCHAR(32),
    operator VARCHAR(32),
    comment VARCHAR(512),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 事项知识命中埋点（热度数据源；默认窗口 30 天、取前 5，可在系统配置 matter_hot_window_days / matter_hot_top_n 调整）
CREATE TABLE IF NOT EXISTS kb_matter_knowledge_hit (
    id VARCHAR(32) PRIMARY KEY,
    kb_id VARCHAR(32) NOT NULL,
    matter_code VARCHAR(64) NOT NULL,
    matter_name VARCHAR(128),
    knowledge_id VARCHAR(32) NOT NULL,
    hit_type VARCHAR(16),
    user_id VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_matter_time (matter_code, created_at),
    INDEX idx_knowledge (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
