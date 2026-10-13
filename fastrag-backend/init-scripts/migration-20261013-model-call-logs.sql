-- FastRAG 演示数据：模型调用日志
-- 适用日期：2026-10-13
-- 为「系统设置管理-模型管理-查看模型日志」补充调用日志（重点补主力对话模型 GLM-4-Flash）
-- 幂等：id 为主键，重复执行自动跳过
USE fastrag2;

INSERT IGNORE INTO model_call_log (id, model_id, caller, status, duration, tokens, timestamp) VALUES
(1001, '2109581448929116162', 'model_test',      'success', 412,  356, '2026-10-12 09:15:00'),
(1002, '2109581448929116162', 'dialog_knowledge', 'success', 388,  298, '2026-10-12 10:02:00'),
(1003, '2109581448929116162', 'model_invoke',    'success', 298,  186, '2026-10-12 11:30:00'),
(1004, '2109581448929116162', 'agent_console',   'success', 455,  412, '2026-10-12 14:05:00'),
(1005, '2109581448929116162', 'model_invoke',    'failed',  1500, 0,   '2026-10-12 15:40:00'),
(1006, 'model_qwen3',         'model_test',      'success', 520,  447, '2026-10-12 16:12:00'),
(1007, 'model_qwen3',         'model_invoke',    'success', 489,  401, '2026-10-12 17:26:00'),
(1008, 'model_bge_m3',        'retrieval',       'success', 35,   64,  '2026-10-12 18:00:00'),
(1009, 'model_bge_reranker',  'retrieval',       'success', 41,   58,  '2026-10-12 18:05:00');
