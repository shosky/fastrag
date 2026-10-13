-- FastRAG 演示数据：知识加工与采编 / 知识更新 / 知识生产与获取
-- 适用日期：2026-10-13
-- 为「知识检索-演示库」初始化：多媒体处理 / 知识回收 / 知识更新处理 / 问答抽取 四组演示数据
-- 幂等：id 为主键，重复执行自动跳过
USE fastrag2;

SET @kb := '2100443257059983362'; -- 知识检索-演示库

-- ============ 1. 多媒体处理（kb_media_storage，6 条，覆盖 4 类媒体 × 4 种状态） ============
INSERT IGNORE INTO kb_media_storage (id, kb_id, media_type, name, original_name, extension, size, object_key, duration, resolution, description, tags, status, created_by, source) VALUES
('demo_media_01', @kb, 'image',    '网络架构图.png',        '网络架构图.png',        'png',  245678, 'demo/network-arch.png',  NULL, 1920, '宽带接入网整体网络架构示意，含核心路由、OLT、分光器与ONU部署位置', '["宽带", "架构图", "网络"]',  'completed',  'admin', 'manual'),
('demo_media_02', @kb, 'image',    '营业厅现场照片.jpg',    '营业厅现场照片.jpg',    'jpg',  184320, 'demo/shop-photo.jpg',    NULL, NULL, '营业厅宽带受理台席现场照片，用于办理流程说明配图',               '["营业厅", "办理"]',        'uploaded',   'admin', 'manual'),
('demo_media_03', @kb, 'audio',    '客服语音示例.mp3',      '客服语音示例.mp3',      'mp3',   96256, 'demo/service-call.mp3',  85,   NULL, '宽带报障标准应答语音示例，时长约1分25秒',                       '["客服", "语音", "报障"]',  'completed',  'admin', 'manual'),
('demo_media_04', @kb, 'video',    '5G基站巡检演示.mp4',    '5G基站巡检演示.mp4',    'mp4', 1823456, 'demo/5g-inspect.mp4',    305,  '1920x1080', '5G基站例行巡检作业演示视频，含告警检查与防雷接地巡检要点',     '["5G", "基站", "巡检"]',    'processing', 'admin', 'manual'),
('demo_media_05', @kb, 'video',    '光猫安装教学.mp4',      '光猫安装教学.mp4',      'mp4', 1425000, 'demo/modem-install.mp4', 420,  '1280x720',  '家庭光猫安装与激活全流程教学视频',                             '["宽带", "光猫", "安装"]',  'completed',  'admin', 'manual'),
('demo_media_06', @kb, 'document', '宽带办理流程说明.pdf',  '宽带办理流程说明.pdf',  'pdf',  158720, 'demo/broadband-flow.pdf',NULL, NULL, '宽带新装/移机/拆机办理流程说明文档',                           '["宽带", "流程"]',          'failed',     'admin', 'manual');

-- ============ 2. 知识回收（kb_knowledge 软删 3 条，deleted_at 非空即入回收站） ============
INSERT IGNORE INTO kb_knowledge (id, kb_id, title, content, summary, category, tags, status, source, version, deleted_at) VALUES
('demo_recycle_01', @kb, '过期的5G资费说明(2025版)', '2025版5G套餐资费说明：129元档/169元档/199元档。该版本已过期，仅作历史参考。', '已过期的2025版5G资费说明',        '5G',   '["5G", "资费", "过期"]', 'published', 'manual', 1, NOW()),
('demo_recycle_02', @kb, '重复录入的专线办理说明',     'MPLS-VPN专线办理流程说明（重复录入条目，与正式条目内容一致，保留一份即可）。', '重复录入的专线办理说明条目',      '专线', '["专线", "重复"]',       'published', 'manual', 1, NOW()),
('demo_recycle_03', @kb, '对话知识-宽带提速办理说明(旧)', '宽带提速办理旧版说明：仅支持营业厅渠道办理提速。新版已支持APP自助办理，此条作废。', '旧版宽带提速办理说明，已被新版替代', '宽带', '["宽带", "提速", "旧版"]', 'draft',   'manual', 1, NOW());

-- ============ 3. 知识更新处理（kb_knowledge_update，4 条，覆盖 3 种状态） ============
INSERT IGNORE INTO kb_knowledge_update (id, kb_id, knowledge_id, update_type, title, old_value, new_value, change_summary, status, operator) VALUES
('demo_update_01', @kb, '2100443286671769601', 'update',   '宽带资费调整通知',           '100M企业宽带月费299元/月',      '100M企业宽带月费调至279元/月，年付享8.5折', '2026年10月资费调整，更新知识条目中的价格信息', 'pending',     'admin'),
('demo_update_02', @kb, '2100443286671769601', 'archive',  '下线旧版5G套餐说明',         '5G畅享套餐(2024版)在售',        '5G畅享套餐(2024版)已下线，不再受理新装',    '旧版套餐下线，对应知识条目待归档',             'pending',     'admin'),
('demo_update_03', @kb, '2100443286671769601', 'create',   '新增企业宽带年付优惠条目',   NULL,                            '企业宽带年付一次性缴纳享9折优惠，含免费移机一次', '新增年付优惠政策知识条目，已审核通过并应用', 'applied',     'admin'),
('demo_update_04', @kb, '2100443286671769601', 'update',   '副卡规则变更(已回滚)',       '副卡10元/月，最多2张',          '副卡8元/月，最多3张',                        '该变更未通过业务复核，已回滚至原内容',         'rolled_back', 'admin');

-- ============ 4. 问答抽取（kb_qa_extract_task，3 条，含 1 条 running 供「停止」演示） ============
INSERT IGNORE INTO kb_qa_extract_task (id, kb_id, name, source_type, source_ids, llm_model, total_count, completed_count, status, result, params, created_by) VALUES
('demo_extract_01', @kb, '宽带FAQ抽取任务',     'knowledge', '[\"2100443286671769601\"]', 'qwen-max', 20, 8,  'running',   NULL, '{\"chunkSize\": 800}', 'admin'),
('demo_extract_02', @kb, '专线问答抽取任务',     'knowledge', '[\"2100443286671769601\"]', 'qwen-max', 12, 12, 'completed', '[{\"question\":\"MPLS-VPN专线如何开通？\",\"answer\":\"联系客户经理提交需求单，3个工作日内开通\"}]', '{\"chunkSize\": 800}', 'admin'),
('demo_extract_03', @kb, '5G问答抽取任务(已停止)', 'knowledge', '[\"2100443286671769601\"]', 'deepseek-chat', 10, 4, 'stopped', NULL, '{\"chunkSize\": 800}', 'admin');
