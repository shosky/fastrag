-- FastRAG 合规性检查增强迁移脚本
-- 为 kb_compliance_rule 表增加命中次数字段，用于追踪合规检查执行效果
-- 适用日期：2026-06-30

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
USE fastrag;

-- 1. kb_compliance_rule 增加 hit_count 和 last_checked_at 字段
ALTER TABLE kb_compliance_rule
    ADD COLUMN IF NOT EXISTS hit_count INT DEFAULT 0 COMMENT '命中次数',
    ADD COLUMN IF NOT EXISTS last_checked_at DATETIME DEFAULT NULL COMMENT '最近检查时间';

-- 2. kb_compliance_rule 增加 last_checked_by 字段（可选，记录最近检查人）
ALTER TABLE kb_compliance_rule
    ADD COLUMN IF NOT EXISTS last_checked_by VARCHAR(32) DEFAULT NULL COMMENT '最近检查人';

-- 3. 为常见查询创建索引
CREATE INDEX IF NOT EXISTS idx_compliance_rule_type ON kb_compliance_rule(rule_type);
CREATE INDEX IF NOT EXISTS idx_compliance_enabled ON kb_compliance_rule(enabled);
