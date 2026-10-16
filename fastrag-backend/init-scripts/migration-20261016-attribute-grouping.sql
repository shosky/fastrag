-- ============================================================
-- 「知识库分组 - 依据知识属性分组」DDL 与演示数据（2026-10-16）
-- 说明：应用启动时 SchemaInitializer 也会幂等执行同样变更（旧库自愈）；本文件供手工执行/留档。
--       全部语句可重复执行。
-- ============================================================
SET NAMES utf8mb4;

-- 1) 属性定义补：select 选项集 + 排序（录入表单动态渲染与分组统计依赖）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_attribute_def ADD COLUMN options TEXT NULL COMMENT ''select 候选选项，JSON 数组''',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_attribute_def' AND COLUMN_NAME = 'options');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE kb_attribute_def ADD COLUMN sort INT DEFAULT 0 COMMENT ''录入/展示顺序''',
    'DO 0') FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_attribute_def' AND COLUMN_NAME = 'sort');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 演示数据：给主演示库（知识条目最多、且已定义「适用客户群/生效省份」的库）补属性值，
--    使「按知识属性分组」打开即有分组可看。仅对 attributes 为空的条目生效，不覆盖已有值。
SET @demo_kb = (SELECT kb_id FROM kb_knowledge WHERE deleted_at IS NULL GROUP BY kb_id
                HAVING SUM(CASE WHEN attributes IS NULL OR attributes = '' THEN 1 ELSE 0 END) > 0
                ORDER BY COUNT(*) DESC LIMIT 1);

-- 2.1) 前 5 条：家庭客户 / 江苏
UPDATE kb_knowledge k SET k.attributes = JSON_OBJECT(
        (SELECT d.id FROM kb_attribute_def d WHERE d.kb_id = k.kb_id AND d.name = '适用客户群' LIMIT 1), '家庭客户',
        (SELECT d.id FROM kb_attribute_def d WHERE d.kb_id = k.kb_id AND d.name = '生效省份' LIMIT 1), '江苏')
WHERE k.kb_id = @demo_kb AND (k.attributes IS NULL OR k.attributes = '')
  AND EXISTS (SELECT 1 FROM kb_attribute_def d WHERE d.kb_id = k.kb_id AND d.name IN ('适用客户群','生效省份') HAVING COUNT(*) = 2)
ORDER BY k.created_at LIMIT 5;

-- 2.2) 其余仍为空的条目：政企客户 / 浙江
UPDATE kb_knowledge k SET k.attributes = JSON_OBJECT(
        (SELECT d.id FROM kb_attribute_def d WHERE d.kb_id = k.kb_id AND d.name = '适用客户群' LIMIT 1), '政企客户',
        (SELECT d.id FROM kb_attribute_def d WHERE d.kb_id = k.kb_id AND d.name = '生效省份' LIMIT 1), '浙江')
WHERE k.kb_id = @demo_kb AND (k.attributes IS NULL OR k.attributes = '')
  AND EXISTS (SELECT 1 FROM kb_attribute_def d WHERE d.kb_id = k.kb_id AND d.name IN ('适用客户群','生效省份') HAVING COUNT(*) = 2)
ORDER BY k.created_at;

-- 2.3) 给主演示库的「适用客户群」补一个 select 型演示属性（带选项集），用于演示"选项类型分组"
INSERT IGNORE INTO kb_attribute_def (id, kb_id, name, attr_type, required, description, options, sort)
SELECT 'demo_attr_level', @demo_kb, '服务等级', 'select', 0, '演示：选项类型属性（金牌/银牌/标准）', '["金牌","银牌","标准"]', 10
WHERE @demo_kb IS NOT NULL;
