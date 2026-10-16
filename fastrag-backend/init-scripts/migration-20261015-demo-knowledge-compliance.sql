-- ============================================================
-- 5.4.6.2.4 知识采编 演示数据（2026-10-15）
-- 全部语句可重复执行（INSERT IGNORE / 依主键去重），不覆盖已有业务数据
-- 包含：事项热点配置项、知识工单三态演示数据 + 流程环节日志、事项知识命中埋点
-- 注意：中文常量与 utf8mb4 列做 CONCAT 时必须先声明连接字符集，否则 GBK 默认连接会报 1267
-- ============================================================
SET NAMES utf8mb4;

-- 1) 事项热点两类可配置项（统计时间窗口天数 / 事项热点返回条数）
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_type, description, is_default, is_system)
VALUES ('cfg_matter_window_days', 'matter_hot_window_days', '30', 'matter', '事项热点统计时间窗口（天），座席端按事项推荐热点知识时生效', 1, 0);
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_type, description, is_default, is_system)
VALUES ('cfg_matter_hot_top_n', 'matter_hot_top_n', '5', 'matter', '事项热点返回知识条数（前 N 个），座席端按事项推荐热点知识时生效', 1, 0);

-- 2) 知识工单三态演示数据（待审批/已通过/已驳回），关联主演示库（知识条目最多的库）的真实知识条目
--    待审批：修改流程（修改知识状态流转至待审核时自动生成）
INSERT IGNORE INTO kb_knowledge_ticket
    (id, kb_id, title, description, ticket_type, priority, knowledge_id, reporter, status,
     flow_name, current_step, submitted_at, created_at)
SELECT 'demo_kt_pending', k.kb_id, CONCAT('知识修改待审：', k.title),
       '演示数据：知识修改流程待审批', 'update', 'medium', k.id, 'admin', 'pending_approval',
       '知识审核流程', '初审', NOW(), NOW()
FROM kb_knowledge k
WHERE k.deleted_at IS NULL
  AND k.kb_id = (SELECT kb_id FROM kb_knowledge WHERE deleted_at IS NULL GROUP BY kb_id ORDER BY COUNT(*) DESC LIMIT 1)
ORDER BY k.created_at ASC LIMIT 1;

--    已通过：新增流程（审批通过后知识点已上线）
INSERT IGNORE INTO kb_knowledge_ticket
    (id, kb_id, title, description, ticket_type, priority, knowledge_id, reporter, status, approver,
     approval_comment, flow_name, current_step, submitted_at, approved_at, resolved_at, created_at)
SELECT 'demo_kt_approved', k.kb_id, CONCAT('知识新增待审：', k.title),
       '演示数据：新增流程审批通过', 'create', 'medium', k.id, 'admin', 'approved', 'admin',
       '初审通过，流程结束', '知识审核流程', '初审', NOW(), NOW(), NOW(), NOW()
FROM kb_knowledge k
WHERE k.deleted_at IS NULL
  AND k.kb_id = (SELECT kb_id FROM kb_knowledge WHERE deleted_at IS NULL GROUP BY kb_id ORDER BY COUNT(*) DESC LIMIT 1)
ORDER BY k.created_at ASC LIMIT 1 OFFSET 1;

--    已驳回：取消发布流程驳回（知识点已恢复取消前状态）
INSERT IGNORE INTO kb_knowledge_ticket
    (id, kb_id, title, description, ticket_type, priority, knowledge_id, reporter, status, approver,
     approval_comment, flow_name, current_step, submitted_at, approved_at, resolved_at, created_at)
SELECT 'demo_kt_rejected', k.kb_id, CONCAT('知识取消发布待审：', k.title),
       '演示数据：取消发布流程被驳回，已恢复取消前状态', 'offline', 'medium', k.id, 'admin', 'rejected', 'admin',
       '初审驳回：该知识点仍在使用中，暂不取消发布', '知识审核流程', '初审', NOW(), NOW(), NOW(), NOW()
FROM kb_knowledge k
WHERE k.deleted_at IS NULL
  AND k.kb_id = (SELECT kb_id FROM kb_knowledge WHERE deleted_at IS NULL GROUP BY kb_id ORDER BY COUNT(*) DESC LIMIT 1)
ORDER BY k.created_at ASC LIMIT 1 OFFSET 2;

-- 3) 工单流程环节日志（提交/审批/驳回步骤，供「流程环节」时间轴展示）
INSERT IGNORE INTO kb_knowledge_ticket_log (id, ticket_id, step_name, action, operator, comment, created_at)
VALUES ('demo_ktl_p1', 'demo_kt_pending', '初审', 'submit', 'admin', '发起修改流程，等待初审审批', NOW());
INSERT IGNORE INTO kb_knowledge_ticket_log (id, ticket_id, step_name, action, operator, comment, created_at)
VALUES ('demo_ktl_a1', 'demo_kt_approved', '初审', 'submit', 'admin', '发起新增流程，等待初审审批', NOW());
INSERT IGNORE INTO kb_knowledge_ticket_log (id, ticket_id, step_name, action, operator, comment, created_at)
VALUES ('demo_ktl_a2', 'demo_kt_approved', '初审', 'approve', 'admin', '初审通过，流程结束；意见：内容准确，准予发布', NOW());
INSERT IGNORE INTO kb_knowledge_ticket_log (id, ticket_id, step_name, action, operator, comment, created_at)
VALUES ('demo_ktl_r1', 'demo_kt_rejected', '初审', 'submit', 'admin', '发起取消发布流程，等待初审审批', NOW());
INSERT IGNORE INTO kb_knowledge_ticket_log (id, ticket_id, step_name, action, operator, comment, created_at)
VALUES ('demo_ktl_r2', 'demo_kt_rejected', '初审', 'reject', 'admin', '初审驳回；意见：该知识点仍在使用中，暂不取消发布', NOW());

-- 4) 事项知识命中埋点：给已有事项关联造近 7 天热度，使座席端选中事项即有热点推荐
INSERT IGNORE INTO kb_matter_knowledge_hit (id, kb_id, matter_code, matter_name, knowledge_id, hit_type, user_id, created_at)
SELECT CONCAT('demo_hit_a_', r.id), r.kb_id, COALESCE(NULLIF(r.matter_code, ''), r.matter_name), r.matter_name,
       r.knowledge_id, 'ticket', 'admin', DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM kb_matter_knowledge_rel r
WHERE r.knowledge_id IS NOT NULL AND r.knowledge_id <> '';

INSERT IGNORE INTO kb_matter_knowledge_hit (id, kb_id, matter_code, matter_name, knowledge_id, hit_type, user_id, created_at)
SELECT CONCAT('demo_hit_b_', r.id), r.kb_id, COALESCE(NULLIF(r.matter_code, ''), r.matter_name), r.matter_name,
       r.knowledge_id, 'click', 'admin', DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM kb_matter_knowledge_rel r
WHERE r.knowledge_id IS NOT NULL AND r.knowledge_id <> '';
