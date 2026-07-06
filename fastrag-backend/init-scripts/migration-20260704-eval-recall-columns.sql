-- ============================================================================
-- Migration: Add structured recall columns to kb_evaluation_result
-- Date: 2026-07-04
-- Description: 将 retrieval_metrics 从不可查询的字符串拆分为结构化 DECIMAL 列，
--              支持 SQL 级别的 Recall@K 指标分析。
-- ============================================================================

ALTER TABLE kb_evaluation_result
    ADD COLUMN recall_at_1 DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@1',
    ADD COLUMN recall_at_3 DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@3',
    ADD COLUMN recall_at_5 DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@5',
    ADD COLUMN recall_at_10 DECIMAL(5,4) DEFAULT NULL COMMENT 'Recall@10';
