-- ============================================================
-- Migration: 为 kb_chunk.content 添加 MySQL FULLTEXT 索引
-- 用于全文检索模式下的 MATCH...AGAINST 查询
-- ============================================================

-- 检查索引是否已存在，避免重复创建
SET @index_exists = (SELECT COUNT(1) FROM information_schema.statistics
                     WHERE table_schema = DATABASE()
                       AND table_name = 'kb_chunk'
                       AND index_name = 'idx_content_fulltext');

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE kb_chunk ADD FULLTEXT INDEX idx_content_fulltext (content) WITH PARSER ngram',
    'SELECT "Index idx_content_fulltext already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
