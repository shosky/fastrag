-- 2026-10-26 知识更新-比较更新内容：补 2 条带新旧内容快照的演示日志
-- 用途：业务流管理→知识更新→「比较」弹窗演示（后端已支持：编辑知识条目/重命名文件会自动产生可比较记录）
-- 执行方式：mysql -u<user> -p fastrag2 < migration-20261026-update-compare-demo.sql
-- 幂等：重复执行不产生重复数据

INSERT INTO kb_update_log (id, kb_id, update_type, target, detail, old_value, new_value, operator, timestamp)
SELECT 'ul_demo_cmp_1', k.id, 'knowledge_updated', '宽带办理条件与资费说明', '编辑知识条目',
       '宽带办理需携带本人身份证。目前支持100M和200M两档，月费分别为99元和139元。',
       '宽带办理需携带本人身份证原件（需在有效期内）。目前支持100M/200M/500M三档，月费分别为99元、139元和199元；500M档赠送千兆光猫一台。',
       'admin', NOW()
FROM kb k WHERE k.name LIKE '演示-电信业务知识库%'
  AND NOT EXISTS (SELECT 1 FROM kb_update_log t WHERE t.id = 'ul_demo_cmp_1')
LIMIT 1;

INSERT INTO kb_update_log (id, kb_id, update_type, target, detail, old_value, new_value, operator, timestamp)
SELECT 'ul_demo_cmp_2', k.id, 'file_updated', '故障排查手册_v2.txt', '更新文件信息',
       '故障排查手册.txt',
       '故障排查手册_v2.txt（更新内容：补充光猫LOS红灯、千兆不达标两类故障的处理步骤）',
       'admin', NOW()
FROM kb k WHERE k.name LIKE '演示-电信业务知识库%'
  AND NOT EXISTS (SELECT 1 FROM kb_update_log t WHERE t.id = 'ul_demo_cmp_2')
LIMIT 1;
