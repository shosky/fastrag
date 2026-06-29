-- FastRAG Seed Data
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
USE fastrag;

-- Default roles
INSERT INTO sys_role (id, role_key, name, description, is_default, is_system) VALUES
('role_super_admin', 'super_admin', '超级管理员', '系统超级管理员，拥有所有权限', 0, 1),
('role_kb_admin', 'kb_admin', '知识库管理员', '管理知识库的创建、编辑、删除等', 0, 1),
('role_kb_user', 'kb_user', '知识库用户', '普通用户，可查看和搜索知识库', 1, 1),
('role_readonly', 'readonly', '只读用户', '只能查看，不能编辑', 0, 1);

-- Permissions
INSERT INTO sys_permission (perm_key, name, type, `group`) VALUES
('menu:home', '首页', 'menu', 'menu'),
('menu:knowledge', '知识库', 'menu', 'menu'),
('menu:application', '应用中心', 'menu', 'menu'),
('menu:workflow', '工作流', 'menu', 'menu'),
('menu:admin', '管理中心', 'menu', 'menu'),
('menu:admin:system', '系统管理', 'menu', 'menu'),
('menu:admin:account', '账号权限', 'menu', 'menu'),
('menu:admin:audit', '安全审计', 'menu', 'menu'),
('menu:admin:content', '内容工具', 'menu', 'menu'),
('menu:admin:platform', '开放平台', 'menu', 'menu'),
('kb:create', '创建知识库', 'action', 'kb'),
('kb:edit', '编辑知识库', 'action', 'kb'),
('kb:delete', '删除知识库', 'action', 'kb'),
('kb:upload', '上传文件', 'action', 'kb'),
('kb:manage_chunks', '管理分片', 'action', 'kb'),
('kb:manage_graph', '管理图谱', 'action', 'kb'),
('kb:manage_eval', '管理评估', 'action', 'kb'),
('kb:manage_strategy', '管理策略', 'action', 'kb'),
('kb:view', '查看知识库', 'action', 'kb'),
('kb:search', '搜索知识库', 'action', 'kb'),
('kb:acl_manage', '权限管理', 'action', 'kb'),
('admin:access', '管理中心访问', 'action', 'admin'),
('admin:user', '用户管理', 'action', 'admin'),
('admin:role', '角色管理', 'action', 'admin'),
('admin:org', '组织管理', 'action', 'admin'),
('admin:system', '系统配置', 'action', 'admin'),
('admin:audit', '审计管理', 'action', 'admin'),
('app:create', '创建应用', 'action', 'app'),
('app:edit', '编辑应用', 'action', 'app'),
('app:use', '使用应用', 'action', 'app'),
('workflow:create', '创建工作流', 'action', 'workflow'),
('workflow:edit', '编辑工作流', 'action', 'workflow'),
('workflow:delete', '删除工作流', 'action', 'workflow'),
('workflow:publish', '发布工作流', 'action', 'workflow'),
('review:approve', '审核通过', 'action', 'review'),
('review:reject', '审核拒绝', 'action', 'review'),
('qa:manage', '问答管理', 'action', 'kb'),
('testcase:manage', '测试用例管理', 'action', 'kb');

-- Super admin has all permissions
INSERT INTO sys_role_permission (role_id, permission_key)
SELECT 'role_super_admin', perm_key FROM sys_permission;

-- KB admin permissions
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
('role_kb_admin', 'menu:home'), ('role_kb_admin', 'menu:knowledge'),
('role_kb_admin', 'kb:create'), ('role_kb_admin', 'kb:edit'), ('role_kb_admin', 'kb:delete'),
('role_kb_admin', 'kb:upload'), ('role_kb_admin', 'kb:manage_chunks'), ('role_kb_admin', 'kb:manage_graph'),
('role_kb_admin', 'kb:manage_eval'), ('role_kb_admin', 'kb:manage_strategy'), ('role_kb_admin', 'kb:view'),
('role_kb_admin', 'kb:search'), ('role_kb_admin', 'kb:acl_manage');

-- KB user permissions
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
('role_kb_user', 'menu:home'), ('role_kb_user', 'menu:knowledge'), ('role_kb_user', 'menu:application'),
('role_kb_user', 'kb:view'), ('role_kb_user', 'kb:search'), ('role_kb_user', 'kb:upload'),
('role_kb_user', 'app:use');

-- Readonly permissions
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
('role_readonly', 'menu:home'), ('role_readonly', 'kb:view');

-- Default admin user (password: admin123)
INSERT INTO sys_user (id, username, real_name, email, password_hash, role_id, status) VALUES
('user_admin', 'admin', '超级管理员', 'admin@fastrag.com', '$2b$10$kwtu6144DqB4Q4IqDbbsROvyxm.gYLLizL0Qbvm0otat.MBfaoJiG', 'role_super_admin', 'enabled');

-- Default org
INSERT INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
('org_root', 'FastRAG', '总部', 'root', 1, 0);

-- Sample knowledge base
INSERT INTO kb (id, name, description, category, permission, creator, embedding_model, dimension, type) VALUES
('kb_sample', '示例知识库', 'FastRAG示例知识库，用于快速体验', '技术文档', 'public', 'user_admin', 'bge-m3', 1024, 'team');

-- Grant admin as owner of sample KB
INSERT INTO kb_acl (kb_id, user_id, kb_role, granted_by) VALUES
('kb_sample', 'user_admin', 'owner', 'user_admin');

-- Sample model
INSERT INTO model (id, name, code, purpose, brand, status) VALUES
('model_qwen3', 'Qwen3-72B', 'qwen3-72b', 'LLM', '阿里云', 'online'),
('model_bge_m3', 'BGE-M3', 'bge-m3', 'Embedding', 'BAAI', 'online'),
('model_bge_reranker', 'BGE-Reranker-v2-M3', 'bge-reranker-v2-m3', 'Rerank', 'BAAI', 'online');

-- Login security defaults
INSERT INTO login_security_config (id) VALUES (1);

-- Dictionary entries
INSERT INTO sys_dictionary (dict_type, dict_key, dict_value) VALUES
('kb_category', '技术文档', '技术文档'),
('kb_category', '产品说明', '产品说明'),
('kb_category', '常见问题', '常见问题'),
('kb_category', '政策法规', '政策法规'),
('app_type', 'ChatBot', '聊天机器人'),
('app_type', 'Editor', '编辑器'),
('app_type', 'LiteAgent', '轻量Agent');

-- ==================== M1 知识库用户反馈 ====================
INSERT INTO user_feedback (kb_id, user_id, query, answer, feedback, comment, score, status, reply, processed_by, category) VALUES
('kb_sample', 'admin', '如何申请退款？', '您可以在订单详情页点击申请退款...', 'like', '回答很清晰', 5, 'resolved', '感谢您的认可！', 'admin', '产品功能'),
('kb_sample', 'admin', '订单状态查询', '您可以在我的订单中查看...', 'dislike', '查不到物流进度', 2, 'resolved', '已补充物流查询功能说明。', 'admin', '功能缺陷'),
('kb_sample', 'admin', '保修期是多久？', '', 'report', '答案中存在错别字', 1, 'pending', NULL, NULL, '内容错误'),
('kb_sample', 'admin', '发票怎么申请？', '请在订单页申请电子发票...', 'like', '很方便', 5, 'ignored', NULL, NULL, '产品功能'),
('kb_sample', 'admin', '退货流程', '', 'dislike', '退货政策不完整', 2, 'pending', NULL, NULL, '内容缺失');

-- ==================== M2 知识检索增强 ====================
INSERT INTO kb_retrieval_log (kb_id, query, user_id, hit_count, top_score, latency_ms, has_result) VALUES
('kb_sample', '退款流程', 'admin', 8, 0.95, 120, 1),
('kb_sample', '订单查询', 'admin', 5, 0.89, 95, 1),
('kb_sample', '发票申请', 'admin', 3, 0.82, 110, 1),
('kb_sample', '保修政策', 'admin', 0, 0.00, 80, 0),
('kb_sample', '退货', 'admin', 6, 0.91, 105, 1),
('kb_sample', '物流跟踪', 'admin', 0, 0.00, 75, 0),
('kb_sample', '密码重置', 'admin', 2, 0.45, 90, 1),
('kb_sample', '产品对比', 'admin', 4, 0.78, 130, 1);

INSERT INTO kb_update_remind (id, kb_id, enabled, cron_expr, channels) VALUES
('remind_001', 'kb_sample', 1, '0 9 * * *', '["mail","inapp"]');

-- ==================== M3 实体库 ====================
INSERT INTO kb_entity (id, kb_id, name, entity_type, description, values_json, creator) VALUES
('entity_city', 'kb_sample', '城市', 'enum', '常用城市枚举', '["北京","上海","广州","深圳","杭州","成都","武汉","南京","西安","重庆"]', 'admin'),
('entity_product', 'kb_sample', '产品类别', 'enum', '产品分类枚举', '["手机","笔记本","家电","配件","穿戴设备"]', 'admin'),
('entity_order_status', 'kb_sample', '订单状态', 'enum', '订单流转状态', '["待付款","已付款","已发货","已签收","已退款","已取消"]', 'admin');

-- ==================== M6 数据挖掘 ====================
INSERT INTO data_mining_task (id, name, kb_id, rule_type, rule_config, status, result_summary, creator) VALUES
('dm_001', '高频退款问题挖掘', 'kb_sample', 'frequency', '{"threshold": 10, "days": 30}', 'enabled', '{"matchedCount": 28, "topKeywords": ["退款","退货","物流"], "runStatus": "success"}', 'admin');

-- ==================== M7 多媒体存储 + 问答抽取 ====================
INSERT INTO kb_media_storage (id, kb_id, media_type, name, original_name, extension, size, description, tags, status, created_by) VALUES
('media_img_001', 'kb_sample', 'image', '产品架构图.png', '产品架构图.png', 'png', 245678, '2026年产品架构总览', '["架构","产品"]', 'completed', 'admin'),
('media_img_002', 'kb_sample', 'image', '退换货流程图.png', '退换货流程图.png', 'png', 156789, '退换货业务流程', '["流程","售后"]', 'completed', 'admin'),
('media_aud_001', 'kb_sample', 'audio', '客服话术-退款.mp3', '客服话术-退款.mp3', 'mp3', 1024000, '退款场景标准话术录音', '["话术","退款"]', 'completed', 'admin'),
('media_vid_001', 'kb_sample', 'video', '产品使用教程.mp4', '产品使用教程.mp4', 'mp4', 5120000, '产品操作演示视频', '["教程"]', 'completed', 'admin'),
('media_doc_001', 'kb_sample', 'document', '产品手册.docx', '产品手册.docx', 'docx', 356000, '完整产品说明文档', '["手册","说明"]', 'completed', 'admin');

INSERT INTO kb_qa_extract_task (id, kb_id, name, source_type, llm_model, total_count, completed_count, status, created_by) VALUES
('qat_001', 'kb_sample', '产品文档问答抽取', 'document', 'qwen3-72b', 50, 50, 'completed', 'admin'),
('qat_002', 'kb_sample', 'FAQ知识抽取', 'document', 'qwen3-72b', 30, 12, 'running', 'admin');

-- ==================== M8 知识加工与采编 ====================
INSERT INTO kb_knowledge_edit (id, kb_id, title, content, edit_type, status, editor, version) VALUES
('ke_001', 'kb_sample', '退款流程说明-修订', '更新退款申请步骤，新增7天无理由退款说明...', 'update', 'approved', 'admin', 2),
('ke_002', 'kb_sample', '物流查询指南', '新增物流实时查询功能的使用说明...', 'create', 'submitted', 'admin', 1),
('ke_003', 'kb_sample', '保修政策合并', '将分散的保修条款合并为统一文档...', 'merge', 'draft', 'admin', 1);

INSERT INTO kb_knowledge_validate (id, kb_id, validate_type, target_scope, total_count, passed_count, warning_count, failed_count, result, status, created_by) VALUES
('kv_001', 'kb_sample', 'duplicate', 'all', 120, 108, 4, 8, '{"duplicateGroups": 8}', 'completed', 'admin'),
('kv_002', 'kb_sample', 'expired', 'all', 120, 110, 5, 5, '{"expiredItems": 5}', 'completed', 'admin');

-- ==================== M9 标签类型 + 标签 + 笔记 ====================
INSERT INTO kb_tag_type (id, kb_id, name, description, color, icon, sort, is_system) VALUES
('tt_001', 'kb_sample', '业务域', '按业务领域分类', '#1890ff', 'folder', 1, 1),
('tt_002', 'kb_sample', '内容类型', '按内容形式分类', '#52c41a', 'file', 2, 0),
('tt_003', 'kb_sample', '优先级', '重要程度', '#fa8c16', 'star', 3, 0);

INSERT INTO kb_tag (id, kb_id, tag_type_id, name, color, usage_count, created_by) VALUES
('tag_001', 'kb_sample', 'tt_001', '退款', '#1890ff', 25, 'admin'),
('tag_002', 'kb_sample', 'tt_001', '物流', '#1890ff', 18, 'admin'),
('tag_003', 'kb_sample', 'tt_001', '售后', '#1890ff', 15, 'admin'),
('tag_004', 'kb_sample', 'tt_002', 'FAQ', '#52c41a', 30, 'admin'),
('tag_005', 'kb_sample', 'tt_002', '手册', '#52c41a', 12, 'admin'),
('tag_006', 'kb_sample', 'tt_003', '重要', '#fa8c16', 8, 'admin');

INSERT INTO kb_note (id, kb_id, title, content, target_type, target_id, created_by) VALUES
('note_001', 'kb_sample', '产品架构要点', '## 核心模块\n1. 知识库管理\n2. 检索服务\n3. 应用管理', 'document', 'media_doc_001', 'admin'),
('note_002', 'kb_sample', '退款流程关键点', '注意7天无理由与质量保修的区别...', 'document', 'media_doc_001', 'admin');

-- ==================== M10 知识管理 + M11 知识更新 ====================
INSERT INTO kb_knowledge (id, kb_id, title, content, summary, category, tags, source, version, status, created_by) VALUES
('kn_001', 'kb_sample', '退款流程说明', '## 退款流程\n1. 用户提交退款申请\n2. 客服审核\n3. 财务处理\n4. 退款到账', '详细说明了用户退款申请的完整流程', '业务流程', '["退款","流程"]', 'manual', 1, 'published', 'admin'),
('kn_002', 'kb_sample', '物流查询指南', '## 物流查询\n支持实时物流查询功能...', '物流查询功能的详细使用说明', 'FAQ', '["物流","查询"]', 'manual', 1, 'published', 'admin'),
('kn_003', 'kb_sample', '保修政策说明', '## 保修政策\n1. 7天无理由退货\n2. 15天质量问题换货\n3. 1年免费保修', '产品保修政策完整说明', '政策法规', '["保修","售后"]', 'manual', 2, 'draft', 'admin');

INSERT INTO kb_knowledge_test (id, kb_id, knowledge_id, test_query, expected_answer, relevance_score, is_passed, test_model, created_by) VALUES
('kt_001', 'kb_sample', 'kn_001', '如何申请退款？', '用户可在订单详情页提交退款申请...', 0.92, 1, 'qwen3-72b', 'admin');

INSERT INTO kb_knowledge_dialog (id, kb_id, knowledge_id, dialog_type, result, confidence, created_by) VALUES
('kd_001', 'kb_sample', 'kn_001', 'verify', 'matched', 0.92, 'admin');

INSERT INTO kb_knowledge_update (id, kb_id, knowledge_id, update_type, title, change_summary, status, operator) VALUES
('ku_001', 'kb_sample', 'kn_001', 'update', '退款流程-增加7天无理由', '增加7天无理由退款条款说明和流程图', 'applied', 'admin'),
('ku_002', 'kb_sample', 'kn_003', 'update', '保修政策修订', '更新包修期限和范围', 'pending', 'admin');

-- ==================== M12 智能搜索 ====================
INSERT INTO kb_search_association (id, kb_id, dimension, name, pattern, suggestions, priority, enabled, created_by) VALUES
('sa_001', 'kb_sample', 'content', '退款联想', '退款|退货', '["退款流程","退款时效"]', 10, 1, 'admin'),
('sa_002', 'kb_sample', 'keyword', '物流联想', '物流|快递|配送', '["物流查询","配送进度"]', 8, 1, 'admin');

INSERT INTO kb_auto_correction (id, kb_id, wrong_text, correct_text, match_type, priority, enabled, created_by) VALUES
('ac_001', 'kb_sample', '退宽', '退款', 'exact', 10, 1, 'admin'),
('ac_002', 'kb_sample', '流物', '物流', 'fuzzy', 8, 1, 'admin'),
('ac_003', 'kb_sample', '保修其', '保修期', 'regex', 7, 1, 'admin');

-- ==================== M13 知识问答 ====================
INSERT INTO kb_multi_turn_qa (id, kb_id, title, turns, category, status, created_by) VALUES
('mt_001', 'kb_sample', '退款咨询多轮对话', '[{"turnIndex":1,"question":"如何申请退款？","answer":"在订单详情页点击 申请退款..."},{"turnIndex":2,"question":"退款多久到账？","answer":"1-3个工作日原路退回"}]', '售后服务', 'active', 'admin');

INSERT INTO kb_multimodal_qa (id, kb_id, title, question, answer, modal_type, category, status, created_by) VALUES
('mm_001', 'kb_sample', '产品架构图解读', '请解释产品架构图的核心模块', '如图所示，核心模块包括知识库管理、检索服务和应用管理', 'image', '产品', 'active', 'admin');

INSERT INTO kb_doc_guide (id, kb_id, file_id, title, summary, outline, key_points, index_status, created_by) VALUES
('dg_001', 'kb_sample', 'media_doc_001', '产品手册导读', '本文档描述了FastRAG平台的完整产品功能', '[{"level":1,"title":"概述"},{"level":2,"title":"核心功能"},{"level":2,"title":"使用说明"}]', '["核心概念","操作流程","注意事项"]', 'completed', 'admin');

-- ==================== M14 系统设置管理 ====================
INSERT INTO sys_config (config_key, config_value, config_type, description, is_default, is_system) VALUES
('publish_switch', '{"enabled":true,"requireReview":true,"autoPublish":false}', 'publish', '发布开关配置', 1, 1),
('review_switch', '{"enabled":true,"singleReviewer":false,"minReviewers":2}', 'review', '审核开关配置', 1, 1),
('review_flow', '{"flowId":"default","flowName":"标准审核流程"}', 'review', '审核流程选择', 1, 1),
('doc_guide', '{"autoGenerate":true,"llmModel":"qwen3-72b","maxOutlineLevel":3}', 'doc_guide', '文档导读设置', 1, 1),
('publish_settings', '{"publishWindow":{"start":"09:00","end":"18:00"},"maxDailyPublish":10}', 'publish', '发布设置', 1, 1),
('review_settings', '{"timeoutHours":48,"autoApproveOnTimeout":false}', 'review', '审核设置', 1, 1),
('general_settings', '{"defaultLanguage":"zh-CN","timezone":"Asia/Shanghai"}', 'general', '通用设置', 1, 1);

-- ==================== M15 知识审核管理 ====================
INSERT INTO kb_publish_history (id, kb_id, knowledge_id, version, publish_type, status, operator, published_at) VALUES
('ph_001', 'kb_sample', 'kn_001', 1, 'publish', 'published', 'admin', NOW()),
('ph_002', 'kb_sample', 'kn_002', 1, 'publish', 'published', 'admin', NOW());

INSERT INTO kb_publish_plan (id, kb_id, name, knowledge_ids, strategy, execution_status, created_by) VALUES
('pp_001', 'kb_sample', '周度发布计划', '["kn_001","kn_002"]', 'incremental', 'pending', 'admin');

INSERT INTO kb_review_strategy (id, kb_id, name, config, enabled, created_by) VALUES
('rs_001', 'kb_sample', '标准审核策略', '{"autoApprove":false,"minReviewers":2}', 1, 'admin'),
('rs_002', 'kb_sample', '快速审核策略', '{"autoApprove":true,"minReviewers":1}', 1, 'admin');

INSERT INTO kb_compliance_rule (id, kb_id, rule_name, rule_type, pattern, action, severity, enabled, created_by) VALUES
('cr_001', 'kb_sample', '敏感信息检查', 'sensitive', '(手机|身份证|银行卡)\\d+', 'block', 'high', 1, 'admin'),
('cr_002', 'kb_sample', '广告用语检查', 'advertising', '(加微信|扫码|免费领取)', 'block', 'high', 1, 'admin');

INSERT INTO kb_quality_rule (id, kb_id, rule_name, metric, threshold, weight, enabled, created_by) VALUES
('qr_001', 'kb_sample', '内容完整性', 'content_length', 100.00, 0.30, 1, 'admin'),
('qr_002', 'kb_sample', '标题规范性', 'title_valid', 0.80, 0.20, 1, 'admin');

INSERT INTO kb_review_template (id, name, category, description, flow_config, is_builtin) VALUES
('rt_001', '标准审核流程', '通用', '包含初审和终审的标准审核流程', '{"steps":["提交","初审","终审","发布"]}', 1),
('rt_002', '快速审核流程', '简单', '单一节点快速通过', '{"steps":["提交","审核"]}', 1);

INSERT INTO kb_review_node (id, template_id, node_name, node_type, order_num) VALUES
('rn_001', 'rt_001', '提交', 'start', 0),
('rn_002', 'rt_001', '初审', 'review', 1),
('rn_003', 'rt_001', '终审', 'review', 2),
('rn_004', 'rt_001', '发布', 'end', 3);

INSERT INTO kb_listener (id, kb_id, name, listen_type, target, status, created_by) VALUES
('li_001', 'kb_sample', '知识库文件变更监听器', 'file_change', 'kb_sample', 'enabled', 'admin'),
('li_002', 'kb_sample', '定时知识更新检查', 'schedule', '0 0 2 * * *', 'enabled', 'admin');

INSERT INTO kb_listener_log (listener_id, level, message, status, created_at) VALUES
('li_001', 'info', '文件变更检测到新文档', 'success', NOW()),
('li_001', 'info', '索引更新完成', 'success', NOW()),
('li_002', 'warn', '定时检查超时，已自动重试', 'retry', NOW());

-- ==================== M16 应用配置种子数据 ====================
INSERT INTO app (id, name, description, type, icon, tags, status, owner) VALUES
('app_001', '智能客服助手', '基于大模型的智能客服机器人，支持知识库检索和多轮对话', 'ChatBot', 'robot', '["客服","助手","智能"]', 'published', 'user_admin');

INSERT INTO app_config (id, app_id, model, prompt, temperature, knowledge_ids, tool_ids, max_turns) VALUES
('acfg_001', 'app_001', 'qwen3-72b', '你是一个专业的客服助手，请基于知识库内容回答用户问题。', 0.70, '["kb_sample"]', '[]', 10);

INSERT INTO app_basic_config (app_id, memory_rounds, output_format, greeting, timeout_seconds) VALUES
('app_001', 5, 'markdown', '您好，我是智能客服助手，请问有什么可以帮您？', 30);

INSERT INTO app_global_policy (app_id, safety_enabled, sensitive_word_mode, fallback_text) VALUES
('app_001', 1, 'reject', '抱歉，我没有理解您的问题，您可以换一种方式描述。');

INSERT INTO app_kb_binding (app_id, kb_id, priority) VALUES
('app_001', 'kb_sample', 10);

INSERT INTO app_trigger (app_id, name, trigger_type, match_content, action_type) VALUES
('app_001', '退款咨询', 'keyword', '退款|退货|退单', 'workflow'),
('app_001', '转人工', 'keyword', '人工|客服', 'reply');

-- ==================== M17 业务流种子数据 ====================
INSERT INTO workflow (id, name, description, status, nodes, edges, created_by) VALUES
('wf_refund_demo', '退款处理工作流', '处理用户退款咨询的自动化工作流', 'published',
 '[{"key":"node_start","type":"start","name":"开始","x":50,"y":200},{"key":"node_intent","type":"intent","name":"意图识别","x":200,"y":200},{"key":"node_kb","type":"kb_retrieval","name":"知识库检索","x":350,"y":200},{"key":"node_llm","type":"llm","name":"大模型回复","x":500,"y":200},{"key":"node_end","type":"end","name":"结束","x":650,"y":200}]',
 '[{"key":"edge_1","source":"node_start","target":"node_intent"},{"key":"edge_2","source":"node_intent","target":"node_kb"},{"key":"edge_3","source":"node_kb","target":"node_llm"},{"key":"edge_4","source":"node_llm","target":"node_end"}]',
 'user_admin');

INSERT INTO wf_node (workflow_id, node_key, node_type, name, position_x, position_y) VALUES
('wf_refund_demo', 'node_start', 'start', '开始', 50, 200),
('wf_refund_demo', 'node_intent', 'intent', '意图识别', 200, 200),
('wf_refund_demo', 'node_kb', 'kb_retrieval', '知识库检索', 350, 200),
('wf_refund_demo', 'node_llm', 'llm', '大模型回复', 500, 200),
('wf_refund_demo', 'node_end', 'end', '结束', 650, 200);

INSERT INTO wf_test_case (workflow_id, name, query, expected_output) VALUES
('wf_refund_demo', '退款流程测试', '如何申请退款？', '您可以在订单详情页点击申请退款...');

INSERT INTO wf_template (name, category, description, canvas_data, is_builtin) VALUES
('退款处理模板', 'refund', '退款意图识别→知识检索→大模型回复', '{}', 1),
('FAQ问答模板', 'qa', '知识检索→大模型回复', '{}', 1);

INSERT INTO wf_optimization (id, workflow_id, name, suggestion, status, created_by) VALUES
('wf_opt_001', 'wf_refund_demo', '优化退款节点响应速度', '将知识库检索节点的缓存策略调整为LRU，提升重复查询响应速度', 'pending', 'admin'),
('wf_opt_002', 'wf_refund_demo', '增加意图识别兜底逻辑', '当意图识别置信度低于0.6时，自动转接人工客服', 'applied', 'admin');

-- ==================== M19 数据库实例种子数据 ====================
INSERT INTO db_instance (id, name, db_type, host, port, db_name, status, created_by) VALUES
('db_inst_001', '订单业务库', 'mysql', '10.0.0.10', 3306, 'orders', 'connected', 'admin');
