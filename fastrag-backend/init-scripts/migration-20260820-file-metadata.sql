-- ============================================================
-- Migration: 文档元数据管理（分册四 rag-file-metadata-management.md）
-- 1. kb_file —— 业务元数据主体列（region/publish_date/doc_level/issuer/doc_number）
--    + 管理侧扩展（metadata_status/custom_attrs/metadata_source）
-- 2. kb —— KB 级自定义属性 schema（customAttrs 复活落库）
-- 3. kb_chunk —— 冗余列（region/publish_date/doc_level，检索过滤锚定 chunk，避免 JOIN）
-- 说明：存量文件的规则抽取由代码侧 extract 端点完成（POST /api/kb/{kbId}/files/metadata/extract），
--       迁移脚本只负责加列，不猜测数据状态。
-- ============================================================

-- ---------- 1. kb_file 增列 ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'region');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN region VARCHAR(64) DEFAULT NULL COMMENT ''地域（省/市，可多值 JSON 数组，如 ["湖南省","长沙"]）''',
    'SELECT "Column kb_file.region already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'publish_date');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN publish_date DATE DEFAULT NULL COMMENT ''发文日期''',
    'SELECT "Column kb_file.publish_date already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'doc_level');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN doc_level VARCHAR(16) DEFAULT NULL COMMENT ''发文层级: national/provincial/municipal/county/unknown''',
    'SELECT "Column kb_file.doc_level already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'issuer');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN issuer VARCHAR(128) DEFAULT NULL COMMENT ''发文机关''',
    'SELECT "Column kb_file.issuer already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'doc_number');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN doc_number VARCHAR(64) DEFAULT NULL COMMENT ''文号（如 发改价格〔2024〕123号）''',
    'SELECT "Column kb_file.doc_number already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'metadata_status');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN metadata_status VARCHAR(16) DEFAULT ''none'' COMMENT ''元数据状态: none/partial/full/revised''',
    'SELECT "Column kb_file.metadata_status already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'custom_attrs');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN custom_attrs JSON DEFAULT NULL COMMENT ''自定义属性取值（KV, schema 见 kb 表）''',
    'SELECT "Column kb_file.custom_attrs already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND column_name = 'metadata_source');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_file ADD COLUMN metadata_source VARCHAR(16) DEFAULT NULL COMMENT ''填充来源: manual/auto/mixed''',
    'SELECT "Column kb_file.metadata_source already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- kb_file 元数据索引（检索过滤/排序消费；P0 量小可容忍，P1 数据量大时启用）
SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND index_name = 'idx_kb_file_region');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE kb_file ADD INDEX idx_kb_file_region (region)',
    'SELECT "Index kb_file.idx_kb_file_region already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND index_name = 'idx_kb_file_doc_level');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE kb_file ADD INDEX idx_kb_file_doc_level (doc_level)',
    'SELECT "Index kb_file.idx_kb_file_doc_level already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = 'kb_file' AND index_name = 'idx_kb_file_publish_date');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE kb_file ADD INDEX idx_kb_file_publish_date (publish_date)',
    'SELECT "Index kb_file.idx_kb_file_publish_date already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. kb 增列：custom_attr_schema ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb' AND column_name = 'custom_attr_schema');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb ADD COLUMN custom_attr_schema JSON DEFAULT NULL COMMENT ''KB 级自定义属性定义（customAttrs 复活落库）''',
    'SELECT "Column kb.custom_attr_schema already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. kb_chunk 冗余列（与分册二定义保持一致） ----------
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND column_name = 'region');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_chunk ADD COLUMN region VARCHAR(64) DEFAULT NULL COMMENT ''地域（冗余自 kb_file.region，入库时回填）''',
    'SELECT "Column kb_chunk.region already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND column_name = 'publish_date');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_chunk ADD COLUMN publish_date DATE DEFAULT NULL COMMENT ''发文日期（冗余自 kb_file.publish_date）''',
    'SELECT "Column kb_chunk.publish_date already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND column_name = 'doc_level');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE kb_chunk ADD COLUMN doc_level VARCHAR(16) DEFAULT NULL COMMENT ''发文层级（冗余自 kb_file.doc_level）''',
    'SELECT "Column kb_chunk.doc_level already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_chunk_region');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE kb_chunk ADD INDEX idx_kb_chunk_region (region)',
    'SELECT "Index kb_chunk.idx_kb_chunk_region already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_chunk_doc_level');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE kb_chunk ADD INDEX idx_kb_chunk_doc_level (doc_level)',
    'SELECT "Index kb_chunk.idx_kb_chunk_doc_level already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_chunk_publish_date');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE kb_chunk ADD INDEX idx_kb_chunk_publish_date (publish_date)',
    'SELECT "Index kb_chunk.idx_kb_chunk_publish_date already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 4. 标签类型字典（T1 文档分类/T2 优先级/T3 状态; 软维度, kb_tag.tagTypeId 直接引用字符串） ----------
-- kb_tag 表结构已含 tag_type_id 字段注释，此处仅落字典数据供前端下拉使用（幂等插入）
SET @dict = (SELECT COUNT(1) FROM sys_dictionary WHERE dict_type = 'TAG_TYPE' AND dict_key = 'T1');
SET @sql = IF(@dict = 0,
    'INSERT INTO sys_dictionary (dict_type, dict_key, dict_value) VALUES (''TAG_TYPE'', ''T1'', ''文档分类''), (''TAG_TYPE'', ''T2'', ''优先级''), (''TAG_TYPE'', ''T3'', ''状态'')',
    'SELECT "TAG_TYPE dictionary already exists" AS message');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;