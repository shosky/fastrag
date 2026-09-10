-- FastRAG 应用调试配置扩展
-- 适用日期：2026-09-10
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，此处用 information_schema 判断实现幂等
USE fastrag2;

-- app_config 增加调试配置字段（调试级别 debug/info/warn/error 等）
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_config' AND COLUMN_NAME = 'debug_settings');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE app_config ADD COLUMN debug_settings TEXT COMMENT ''应用调试配置JSON(调试级别等)'' AFTER monitor_settings',
    'SELECT ''debug_settings 已存在，跳过'' AS result');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
