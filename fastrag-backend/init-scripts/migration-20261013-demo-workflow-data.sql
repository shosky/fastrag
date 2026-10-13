-- FastRAG 业务流管理演示数据：对话调试 / 对话优化 / 对话测试
-- 适用日期：2026-10-13
-- 为真实业务流「测试流程」铺调试日志、优化建议与测试案例，使业务流管理页四区开箱可演示
-- 幂等：主键/id 固定，重复执行自动跳过
USE fastrag2;
SET @wf := '2095741004533280769'; -- 测试流程

-- 调试级别设置行（node_key=__settings__，不作为日志展示）
INSERT IGNORE INTO wf_debug_log (id, workflow_id, node_key, level, message) VALUES
(9001, @wf, '__settings__', 'info', '调试级别设置');

-- 调试日志 4 条（debug/info/warn/error 各级别）
INSERT IGNORE INTO wf_debug_log (id, workflow_id, node_key, level, message, created_at) VALUES
(9002, @wf, 'start',       'debug', '流程开始节点已触发，输入参数解析完成', NOW() - INTERVAL 3 HOUR),
(9003, @wf, 'llm-node',    'info',  'LLM 节点调用完成，耗时 412ms', NOW() - INTERVAL 2 HOUR),
(9004, @wf, 'kb-retrieve', 'warn',  '知识检索命中数偏低（1 条），建议扩充知识库', NOW() - INTERVAL 1 HOUR),
(9005, @wf, 'end',         'error', '结束节点校验失败：缺少必填字段 orderNo', NOW() - INTERVAL 30 MINUTE);

-- 优化建议 2 条（1 条待应用 / 1 条已应用）
INSERT IGNORE INTO wf_optimization (id, workflow_id, suggestion_type, title, description, impact_score, status) VALUES
('demo_wfo_01', @wf, 'performance', '压缩检索节点提示词', '检索节点提示词过长导致响应慢，建议压缩至 500 字内并固定输出格式', 85.00, 'pending'),
('demo_wfo_02', @wf, 'fallback',     '增加意图识别兜底逻辑', '低分意图增加兜底分支，避免空回复直接结束流程', 70.00, 'applied');

-- 测试案例 3 条（2 通过 / 1 未通过），供「对话优化-分析对话数据」出真实统计
INSERT IGNORE INTO bpm_flow_test_case (id, flow_def_id, name, inputs, expected_output, actual_output, match_result, created_by) VALUES
('demo_wtc_01', @wf, '退款流程-正常退款', '{"query":"我要退款"}', '返回退款办理指引', '返回退款办理指引', 1, 'admin'),
('demo_wtc_02', @wf, '退款流程-查询进度', '{"query":"退款到哪了"}', '返回工单进度', '返回工单进度', 1, 'admin'),
('demo_wtc_03', @wf, '退款流程-异常输入', '{"query":"asdfgh"}', '返回兜底话术', '空回复', 0, 'admin');
