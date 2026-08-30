-- FastRAG 合规性检查增强迁移脚本
-- 为 kb_compliance_rule 表增加命中次数字段，用于追踪合规检查执行效果
-- 适用日期：2026-06-30
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS（MariaDB 语法），
--       改用 information_schema 探测 + PREPARE 的幂等写法。

SET NAMES utf8mb4;

-- ---------- 1. kb_compliance_rule.hit_count ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_compliance_rule'
                     AND column_name = 'hit_count');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_compliance_rule ADD COLUMN hit_count INT DEFAULT 0 COMMENT ''命中次数''',
    'SELECT "Column kb_compliance_rule.hit_count already exists" AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. kb_compliance_rule.last_checked_at ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_compliance_rule'
                     AND column_name = 'last_checked_at');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_compliance_rule ADD COLUMN last_checked_at DATETIME DEFAULT NULL COMMENT ''最近检查时间''',
    'SELECT "Column kb_compliance_rule.last_checked_at already exists" AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. kb_compliance_rule.last_checked_by ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_compliance_rule'
                     AND column_name = 'last_checked_by');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_compliance_rule ADD COLUMN last_checked_by VARCHAR(32) DEFAULT NULL COMMENT ''最近检查人''',
    'SELECT "Column kb_compliance_rule.last_checked_by already exists" AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 4. 索引：idx_compliance_rule_type ----------
SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_compliance_rule'
                     AND index_name = 'idx_compliance_rule_type');
SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_compliance_rule_type ON kb_compliance_rule(rule_type)',
    'SELECT "Index idx_compliance_rule_type already exists" AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 5. 索引：idx_compliance_enabled ----------
SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_compliance_rule'
                     AND index_name = 'idx_compliance_enabled');
SET @sql = IF(@idx_exists = 0,
    'CREATE INDEX idx_compliance_enabled ON kb_compliance_rule(enabled)',
    'SELECT "Index idx_compliance_enabled already exists" AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
