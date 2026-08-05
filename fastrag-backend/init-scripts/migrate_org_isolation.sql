-- ============================================================================
-- 组织数据隔离迁移脚本（一次性，可安全重复执行）
-- 目标：
--   1. kb 表增加 org_id（知识库归属组织，同组织成员默认可见）
--   2. kb_category 表增加 org_id（分类完全按组织隔离；NULL=未分配，仅管理员可见）
-- 存量数据处理：
--   - 已有知识库按创建者所属组织回填 org_id（创建者无组织则保留 NULL）
--   - 已有分类置为 NULL（未分配），由 kb_admin 在管理界面逐个分配到组织
-- ============================================================================

-- 为表增加列（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程保证幂等）
DROP PROCEDURE IF EXISTS fastrag_add_column;
DELIMITER $$
CREATE PROCEDURE fastrag_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(255))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL fastrag_add_column('kb', 'org_id', 'org_id VARCHAR(32) NULL COMMENT ''归属组织（同组织成员默认可见）''');

-- 存量知识库按创建者所属组织回填
UPDATE kb k
LEFT JOIN sys_user u ON u.id = k.creator
SET k.org_id = u.org_id
WHERE k.org_id IS NULL;

CALL fastrag_add_column('kb_category', 'org_id', 'org_id VARCHAR(32) NULL COMMENT ''归属组织（NULL=未分配，仅管理员可见）''');

DROP PROCEDURE IF EXISTS fastrag_add_column;
