-- ============================================================
-- LLM 分片边界审查（structure_aware llmReview.enabled 启用后，
-- structuralChunk 输出经 LLM 审查调整边界后入库）
-- 1) kb_chunk 增加审查标记字段
-- 2) 新建审查明细表 kb_chunk_llm_review（逐片调整说明）
-- ============================================================

ALTER TABLE kb_chunk
    ADD COLUMN llm_reviewed TINYINT DEFAULT 0 COMMENT '0=未经LLM审查 1=LLM审查通过（含keep未调整）',
    ADD COLUMN review_model VARCHAR(128) NULL COMMENT '审查用 LLM 模型',
    ADD COLUMN review_time DATETIME NULL COMMENT '审查时间',
    ADD COLUMN review_note TEXT NULL COMMENT 'LLM 边界调整说明';

CREATE TABLE IF NOT EXISTS kb_chunk_llm_review (
    id VARCHAR(32) PRIMARY KEY,
    chunk_id VARCHAR(64) NOT NULL COMMENT '分片 ID（{fileId}_chunk_{n}）',
    kb_id VARCHAR(32),
    file_id VARCHAR(32),
    model VARCHAR(128) COMMENT '审查用 LLM 模型',
    adjusted TINYINT DEFAULT 0 COMMENT '1=边界被调整 0=审查后保持原样',
    note TEXT COMMENT 'LLM 边界调整说明',
    prompt_version VARCHAR(16) COMMENT '审查 prompt 版本',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
