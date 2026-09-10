-- ============================================================
-- 迁移：图谱存储收敛 Neo4j，移除 MySQL 图表（ADR-0002）
-- 日期：2026-09-05
-- 关联：docs/adr/0002-graph-store-consolidate-on-neo4j.md
--       docs/design/graph-store-neo4j-consolidation-plan.md
-- ============================================================
-- 前置条件：
--   1. 已部署 Neo4j（docker-compose 自带 fastrag-neo4j 服务）；
--   2. 应用已升级到本版本（启动时校验 Neo4j 连通性）；
--   3. 对需要保留图谱的知识库已执行重放迁移：
--      POST /api/kb/{kbId}/graph/index/replay   （fast 档，零 LLM 成本，重放 kb_chunk.extraction_result）
--      POST /api/kb/{kbId}/graph/index/retry    （full 档，重新调 LLM 全量抽取，质量更优）
--      对账：GET  /api/kb/{kbId}/graph/index     （entityCount/relationCount 应非零）
-- 本脚本可重复执行。

-- 1. 备份并移除 MySQL 图表（先 RENAME 保留观察期，确认无回滚需求后执行第 2 段 DROP）
-- 1. 备份并移除 MySQL 图表（先 RENAME 保留观察期，确认无回滚需求后执行第 2 段 DROP）。
--    MySQL 8.0 的 RENAME TABLE 不支持 IF EXISTS，先删同名备份保证幂等：
DROP TABLE IF EXISTS kb_graph_entity_bak_20260905;
DROP TABLE IF EXISTS kb_graph_relation_bak_20260905;
DROP TABLE IF EXISTS kb_graph_entity_mention_bak_20260905;
DROP TABLE IF EXISTS kb_graph_triple_mention_bak_20260905;
RENAME TABLE kb_graph_entity         TO kb_graph_entity_bak_20260905;
RENAME TABLE kb_graph_relation       TO kb_graph_relation_bak_20260905;
RENAME TABLE kb_graph_entity_mention TO kb_graph_entity_mention_bak_20260905;
RENAME TABLE kb_graph_triple_mention TO kb_graph_triple_mention_bak_20260905;

-- 2. 观察期结束后手动执行（或下一版本默认执行）：
-- DROP TABLE IF EXISTS kb_graph_entity_bak_20260905;
-- DROP TABLE IF EXISTS kb_graph_relation_bak_20260905;
-- DROP TABLE IF EXISTS kb_graph_entity_mention_bak_20260905;
-- DROP TABLE IF EXISTS kb_graph_triple_mention_bak_20260905;

-- 3. 新建知识库默认开启图谱构建（ADR-0002 G4：原默认 0 导致多数库从未建图）。
--    仅影响新建库；既有库保持现状，如需批量开启可执行（注意会带来 LLM 抽取成本）：
-- UPDATE kb SET graph_auto_build = 1 WHERE graph_auto_build = 0;
ALTER TABLE kb MODIFY COLUMN graph_auto_build TINYINT DEFAULT 1 COMMENT '是否自动构建知识图谱（ADR-0002：默认开启）';
