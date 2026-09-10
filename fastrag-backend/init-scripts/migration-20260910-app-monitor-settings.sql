-- FastRAG 应用监控管理配置扩展
-- 适用日期：2026-09-10
-- 注意：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，此处用 information_schema 判断实现幂等
USE fastrag2;

-- app_config 增加监控管理配置字段（告警监控 alert / 配置监控 optimize）
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_config' AND COLUMN_NAME = 'monitor_settings');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE app_config ADD COLUMN monitor_settings TEXT COMMENT ''应用监控配置JSON(告警监控/性能优化配置)'' AFTER kb_settings',
    'SELECT ''monitor_settings 已存在，跳过'' AS result');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
