-- FastRAG 应答模板真实化
-- 适用日期：2026-09-14
-- 为 app_template 补充标准回复所需字段
USE fastrag2;

SET @has_content := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_template' AND COLUMN_NAME = 'content');
SET @ddl1 := IF(@has_content = 0,
    'ALTER TABLE app_template ADD COLUMN content TEXT COMMENT ''模板正文'' AFTER name',
    'SELECT ''content 已存在'' AS result');
PREPARE stmt1 FROM @ddl1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

SET @has_category := (SELECT COUNT(*) FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_template' AND COLUMN_NAME = 'category');
SET @ddl2 := IF(@has_category = 0,
    'ALTER TABLE app_template ADD COLUMN category VARCHAR(64) DEFAULT ''通用'' COMMENT ''模板分类'' AFTER description',
    'SELECT ''category 已存在'' AS result');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

SET @has_creator := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_template' AND COLUMN_NAME = 'creator');
SET @ddl3 := IF(@has_creator = 0,
    'ALTER TABLE app_template ADD COLUMN creator VARCHAR(32) DEFAULT ''admin'' COMMENT ''创建人'' AFTER category',
    'SELECT ''creator 已存在'' AS result');
PREPARE stmt3 FROM @ddl3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

SET @has_created_at := (SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_template' AND COLUMN_NAME = 'created_at');
SET @ddl4 := IF(@has_created_at = 0,
    'ALTER TABLE app_template ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'' AFTER creator',
    'SELECT ''created_at 已存在'' AS result');
PREPARE stmt4 FROM @ddl4; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

SET @has_updated_at := (SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_template' AND COLUMN_NAME = 'updated_at');
SET @ddl5 := IF(@has_updated_at = 0,
    'ALTER TABLE app_template ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER created_at',
    'SELECT ''updated_at 已存在'' AS result');
PREPARE stmt5 FROM @ddl5; EXECUTE stmt5; DEALLOCATE PREPARE stmt5;
