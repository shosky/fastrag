-- ============================================================
-- Migration: kb_chunk.origin —— 分片来源标记（手动分片保留语义）
-- 1. 新增 kb_chunk.origin 列：auto=管线自动分片 / manual=用户手动创建
--    重分片 / AI 分片应用时只删 auto，保留 manual（用户手工精心分片不被一键清空）
-- 2. 存量行默认 'auto'（原有分片均为管线产物）
-- ============================================================

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_chunk'
                     AND column_name = 'origin');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_chunk ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT ''auto'' COMMENT ''分片来源(auto=管线自动分片/manual=用户手动创建，重分片时保留 manual)'' AFTER chunk_type',
    'SELECT "Column kb_chunk.origin already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
