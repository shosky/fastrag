-- ============================================================================
-- 数据迁移脚本：sys_dictionary → sys_config
-- 
-- 将字典表中 type='系统信息' 的品牌配置数据迁移到 sys_config 表。
-- 使用 INSERT IGNORE 保证幂等性，多次执行安全。
--
-- 执行方式：
--   mysql -u<user> -p<pass> <database> < migrate_dict_to_config.sql
--
-- 迁移完成后，可安全删除字典表中的冗余数据：
--   DELETE FROM sys_dictionary WHERE type = '系统信息';
-- ============================================================================

-- ===== 品牌信息迁移 =====
INSERT IGNORE INTO sys_config (config_key, config_value, config_type, description, is_default, is_system)
SELECT
    d.key,
    COALESCE(d.value, ''),
    'brand',
    CASE d.key
        WHEN 'system_name' THEN '系统名称'
        WHEN 'system_slogan' THEN '宣传语'
        WHEN 'copyright' THEN '版权信息'
        WHEN 'logo_url' THEN '系统 Logo URL'
        ELSE '品牌信息'
    END,
    1,
    1
FROM sys_dictionary d
WHERE d.type = '系统信息'
  AND d.key IN ('system_name', 'system_slogan', 'copyright', 'logo_url')
  -- 跳过已存在的配置
  AND d.key NOT IN (SELECT config_key FROM sys_config WHERE config_type = 'brand');

-- 输出迁移统计
SELECT CONCAT('Migrated ', ROW_COUNT(), ' records from sys_dictionary to sys_config') AS migration_result;
