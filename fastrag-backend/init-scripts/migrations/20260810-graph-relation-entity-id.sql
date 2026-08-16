-- ============================================================================
-- 知识图谱改进迁移脚本（KG-01 / KG-03 / KG-06）
-- 日期：2026-08-10
-- 说明：
--   1. kb_graph_relation 增加 source_id / target_id（边按实体 ID 引用，消除悬空/错连边）
--   2. 存量关系按名称回填实体 ID（两轮：原始名匹配 → 规范化名匹配）
--   3. kb_graph_entity 增加 attributes 列（实体属性键值对 JSON）
-- 幂等性：ALTER 语句在 MySQL 8 无 ADD COLUMN IF NOT EXISTS，重复执行会报错；
--         建议先执行 SELECT 检查列是否存在，或由部署脚本按错误码跳过。
-- ============================================================================

-- 1. 关系表新增端点实体 ID 列
ALTER TABLE kb_graph_relation ADD COLUMN source_id VARCHAR(64) NULL COMMENT '源实体确定性ID' AFTER kb_id;
ALTER TABLE kb_graph_relation ADD COLUMN target_id VARCHAR(64) NULL COMMENT '目标实体确定性ID' AFTER source;
ALTER TABLE kb_graph_relation ADD INDEX idx_kb_relation_ids (kb_id, source_id, target_id);

-- 2. 存量数据回填：优先按原始名称精确匹配（多命中取任意一条）
UPDATE kb_graph_relation r
SET r.source_id = (SELECT e.entity_id FROM kb_graph_entity e
                   WHERE e.kb_id = r.kb_id AND e.name = r.source LIMIT 1)
WHERE r.source_id IS NULL;

UPDATE kb_graph_relation r
SET r.target_id = (SELECT e.entity_id FROM kb_graph_entity e
                   WHERE e.kb_id = r.kb_id AND e.name = r.target LIMIT 1)
WHERE r.target_id IS NULL;

-- 3. 兜底回填：按规范化名称匹配（处理大小写/空白差异的存量数据）
UPDATE kb_graph_relation r
SET r.source_id = (SELECT e.entity_id FROM kb_graph_entity e
                   WHERE e.kb_id = r.kb_id AND e.normalized_name = LOWER(TRIM(r.source)) LIMIT 1)
WHERE r.source_id IS NULL;

UPDATE kb_graph_relation r
SET r.target_id = (SELECT e.entity_id FROM kb_graph_entity e
                   WHERE e.kb_id = r.kb_id AND e.normalized_name = LOWER(TRIM(r.target)) LIMIT 1)
WHERE r.target_id IS NULL;

-- 4. 实体表新增属性列（实体属性键值对 JSON 字符串）
ALTER TABLE kb_graph_entity ADD COLUMN attributes TEXT COMMENT '实体属性JSON字符串([{"text":"值","label":"属性名"}])' AFTER description;

-- 5. 校验：回填后仍未匹配到 ID 的边（多为指向不存在实体的历史脏数据，保留名称以兼容展示）
SELECT COUNT(*) AS unmatched_source FROM kb_graph_relation WHERE source_id IS NULL;
SELECT COUNT(*) AS unmatched_target FROM kb_graph_relation WHERE target_id IS NULL;
