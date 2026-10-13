-- FastRAG 修复：模型密钥真实保存
-- 适用日期：2026-10-13
-- model 表新增 api_key 列：界面「模型密钥」此前从未落库（只存了 api_key_ref 引用），
-- 对话测试/模型调用把引用当 Bearer 传给上游导致 401。密钥列不回显（@JsonIgnore）。
USE fastrag2;

SET @has_api_key := (SELECT COUNT(*) FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'model' AND COLUMN_NAME = 'api_key');
SET @ddl := IF(@has_api_key = 0,
    'ALTER TABLE model ADD COLUMN api_key TEXT NULL COMMENT ''模型访问密钥（明文保存，接口不回显）'' AFTER api_key_ref',
    'SELECT ''api_key 已存在'' AS result');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
