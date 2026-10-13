-- FastRAG 演示数据：机器人配置管理-插件配置
-- 适用日期：2026-10-13
-- tool 表此前仅 1 条测试数据，为「插件配置-配置插件参数 / 查看插件详情」补充可演示的 http 插件，
-- 其中 2 条预置 API 配置（tool_http_config），供「配置插件参数」回显编辑。
-- 幂等：id 主键 / tool_id 主键，重复执行自动跳过
USE fastrag2;

INSERT IGNORE INTO tool (id, name, identifier, description, type, tags, enabled, inputs) VALUES
('demo_plugin_01', '话费余额查询',   'query_balance',   '查询指定手机号的实时话费余额与套餐使用情况', 'http', '["查询", "话费"]', 1, '[{"name":"phone","type":"string","required":true,"desc":"手机号"}]'),
('demo_plugin_02', '宽带故障自助检测','broadband_check', '对指定宽带账号执行线路质量与光猫状态自助检测', 'http', '["宽带", "故障"]', 1, '[{"name":"account","type":"string","required":true,"desc":"宽带账号"}]'),
('demo_plugin_03', '套餐办理通道',   'plan_subscribe',  '为用户办理指定档位的套餐变更',               'http', '["套餐", "办理"]', 1, '[{"name":"userId","type":"string","required":true,"desc":"用户ID"},{"name":"planCode","type":"string","required":true,"desc":"套餐编码"}]'),
('demo_plugin_04', '工单进度查询',   'ticket_status',   '按工单号查询当前处理进度与预计完成时间',       'http', '["工单", "查询"]', 1, '[{"name":"ticketNo","type":"string","required":true,"desc":"工单号"}]'),
('demo_plugin_05', '营业厅网点查询', 'query_outlets',   '按城市/关键字查询附近营业厅网点与营业时间',    'http', '["营业厅", "LBS"]', 1, '[{"name":"city","type":"string","required":true,"desc":"城市"}]');

INSERT IGNORE INTO tool_http_config (tool_id, method, url, auth_type, params, headers, body_type, body) VALUES
('demo_plugin_01', 'GET',  'https://api.example-telecom.cn/v1/balance',      'api_key', '{"phone":"{phone}"}', '{"X-Channel":"fastrag-bot"}', 'none', NULL),
('demo_plugin_02', 'POST', 'https://api.example-telecom.cn/v1/broadband/diagnose', 'none', '{"account":"{account}"}', '{"X-Channel":"fastrag-bot"}', 'json', '{"account":"{account}","checkItems":["line","modem"]}'),
('demo_plugin_03', 'POST', 'https://api.example-telecom.cn/v1/plans/subscribe', 'api_key', NULL, '{"X-Channel":"fastrag-bot"}', 'json', '{"userId":"{userId}","planCode":"{planCode}"}');
