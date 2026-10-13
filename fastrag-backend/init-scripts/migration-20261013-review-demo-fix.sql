-- FastRAG 知识审核管理演示数据修复
-- 适用日期：2026-10-13
-- 修复审核流程模板的三类数据缺陷，保证「审核流程设计-添加监听器/优化流程节点配置」演示开箱可用：
--   1) rt_001 存在 "undefined（副本）" 节点名
--   2) rt_002 的 steps 是字符串数组，流程设计器解析失败
--   3) 主演示模板步骤未预置监听器引用，「添加监听器」演示无初始勾选
-- 顺带补全一条 listen_type 为空的监听器
USE fastrag2;

-- 1) rt_001 标准审核流程：规范节点
UPDATE kb_review_template SET flow_config='{"steps":[{"id":"step-1","name":"编辑初审","order":1,"reviewerRole":"知识编辑","timeoutHours":12,"listeners":[]},{"id":"step-2","name":"专家复审","order":2,"reviewerRole":"领域专家","timeoutHours":24,"listeners":[]},{"id":"step-3","name":"终审发布","order":3,"reviewerRole":"知识管理员","timeoutHours":24,"listeners":["li_002"]}]}' WHERE id='rt_001';

-- 2) rt_002 快速审核流程：字符串 steps 转标准对象
UPDATE kb_review_template SET flow_config='{"steps":[{"id":"step-1","name":"提交审核","order":1,"reviewerRole":"知识编辑","timeoutHours":8,"listeners":[]},{"id":"step-2","name":"复核发布","order":2,"reviewerRole":"知识管理员","timeoutHours":16,"listeners":["li_001"]}]}' WHERE id='rt_002';

-- 3) 电信业务知识三级审核(2100445810652942337)：步骤预置监听器引用（真实监听器 id）
UPDATE kb_review_template SET flow_config='{"steps":[{"id":"step-1","name":"编辑初审","order":1,"reviewerRole":"知识编辑","timeoutHours":12,"listeners":["2100445810912989185"]},{"id":"step-2","name":"专家复审","order":2,"reviewerRole":"领域专家","timeoutHours":24,"listeners":[]},{"id":"step-3","name":"终审发布","order":3,"reviewerRole":"知识管理员","timeoutHours":24,"listeners":["2100445810850074627"]}]}' WHERE id='2100445810652942337';

-- 4) 补全监听器缺失的类型/目标
UPDATE kb_listener SET listen_type='http', target='http://127.0.0.1:8080/api/home' WHERE id='2100484762357215234' AND (listen_type IS NULL OR listen_type='');
