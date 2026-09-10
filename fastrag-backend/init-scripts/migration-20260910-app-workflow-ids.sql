-- FastRAG 应用工作流配置扩展
-- 适用日期：2026-09-10
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，此处用 information_schema 判断实现幂等
USE fastrag2;

-- app_config 增加应用绑定的工作流ID列表字段（JSON数组，如 ["1","2"]）
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_config' AND COLUMN_NAME = 'workflow_ids');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE app_config ADD COLUMN workflow_ids TEXT COMMENT ''应用绑定的工作流ID列表JSON'' AFTER debug_settings',
    'SELECT ''workflow_ids 已存在，跳过'' AS result');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
