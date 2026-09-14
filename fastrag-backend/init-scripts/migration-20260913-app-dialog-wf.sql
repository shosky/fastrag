-- 应用管理功能补齐：对话配置建议内容 + 业务流调试级别
-- 适用日期：2026-09-13
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，此处用 information_schema 判断实现幂等
USE fastrag2;

-- 1) app_dialog_config 增加答复后建议内容列
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_dialog_config' AND COLUMN_NAME = 'suggestions');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE app_dialog_config ADD COLUMN suggestions TEXT COMMENT ''答复后建议内容JSON数组''',
    'SELECT ''app_dialog_config.suggestions 已存在，跳过'' AS result');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) workflow 增加业务流级调试级别
SET @has_col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workflow' AND COLUMN_NAME = 'debug_level');
SET @ddl2 := IF(@has_col2 = 0,
    'ALTER TABLE workflow ADD COLUMN debug_level VARCHAR(16) DEFAULT ''info'' COMMENT ''业务流调试级别''',
    'SELECT ''workflow.debug_level 已存在，跳过'' AS result');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
