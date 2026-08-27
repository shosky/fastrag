-- ============================================================
-- Migration: 分片切断召回增强（P0/P1/P2）
-- 1. kb_benchmark_question.cross_boundary —— 跨界切断测试题标志
--    答案横跨两个相邻 chunk（分片边界切断场景），用于评测召回完整度
-- 2. kb_evaluation_result.context_completeness[,extended]
--    上下文完整度打分（≈ Ragas context_recall）：
--    - context_completeness：原始命中上下文的要点支撑比例
--    - context_completeness_extended：父块扩展上下文的要点支撑比例
--============================================================

-- ---------- 1. kb_benchmark_question.cross_boundary ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_benchmark_question'
                     AND column_name = 'cross_boundary');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_benchmark_question ADD COLUMN cross_boundary TINYINT NOT NULL DEFAULT 0 COMMENT ''跨界切断标志：1=答案跨多个相邻 chunk（评测召回完整度专用）'' AFTER question_index',
    'SELECT "Column kb_benchmark_question.cross_boundary already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2. kb_evaluation_result.context_completeness ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_evaluation_result'
                     AND column_name = 'context_completeness');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_evaluation_result ADD COLUMN context_completeness DECIMAL(5,4) DEFAULT NULL COMMENT ''上下文完整度（原始命中，0~1）'' AFTER recall_at_10',
    'SELECT "Column kb_evaluation_result.context_completeness already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. kb_evaluation_result.context_completeness_extended ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'kb_evaluation_result'
                     AND column_name = 'context_completeness_extended');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_evaluation_result ADD COLUMN context_completeness_extended DECIMAL(5,4) DEFAULT NULL COMMENT ''上下文完整度（父块扩展，跨界题，0~1）'' AFTER context_completeness',
    'SELECT "Column kb_evaluation_result.context_completeness_extended already exists" AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;