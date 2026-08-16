-- ============================================================
-- Migration: kb_chunk 父子分片支持
-- 新增 parent_id 字段：子分片指向其所属父分片（父分片 chunk_type='parent'）
-- 父分片不向量化（vector_stored=0，不进 Milvus），仅作检索命中后的上下文返回
-- ============================================================

-- 1. 新增 parent_id 列（幂等：存在则跳过）
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_chunk'
                     AND column_name = 'parent_id');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_chunk ADD COLUMN parent_id VARCHAR(64) DEFAULT NULL COMMENT ''父分片ID（子分片指向其所属父分片，父分片本身为 NULL）'' AFTER file_name',
    'SELECT "Column kb_chunk.parent_id already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 新增 parent_id 索引（按父分片查子分片）
SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_chunk'
                     AND index_name = 'idx_parent_id');

SET @sql2 = IF(@idx_exists = 0,
    'ALTER TABLE kb_chunk ADD INDEX idx_parent_id (parent_id)',
    'SELECT "Index kb_chunk.idx_parent_id already exists" AS message');

PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
