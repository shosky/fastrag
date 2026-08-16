-- ============================================================
-- Migration: 知识图谱 mention 表回填 file_id
-- 背景：MysqlGraphStore.createEntityMention/createTripleMention
--       此前未写入 file_id（且实体 mention 写入的是实体文本而非哈希 ID），
--       导致 deleteFileGraph 无法按文件删除 mention，删除文档后图谱数据残留。
-- 作用：把仍存在 chunk 的遗留 mention 记录回填正确 file_id，
--       使后续删除文件/清空回收站时能按 file_id 精确回收。
-- 说明：chunk 已随文件删除的遗留 mention 无法回填，由
--       MysqlGraphStore.deleteFileGraph 中的"孤儿 chunk mention 清理"兜底删除。
-- 运行方式: mysql -h 127.0.0.1 -u root -p fastrag < 此文件
-- ============================================================

SET NAMES utf8mb4;

-- 实体 mention 回填 file_id（chunk 仍存在时）
UPDATE kb_graph_entity_mention m
JOIN kb_chunk c ON c.id = m.chunk_id AND c.kb_id = m.kb_id
SET m.file_id = c.file_id
WHERE m.file_id = '' OR m.file_id IS NULL;

-- 三元组 mention 回填 file_id（chunk 仍存在时）
UPDATE kb_graph_triple_mention m
JOIN kb_chunk c ON c.id = m.chunk_id AND c.kb_id = m.kb_id
SET m.file_id = c.file_id
WHERE m.file_id = '' OR m.file_id IS NULL;
