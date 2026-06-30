-- ============================================================================
-- 迁移脚本：初始化标签与知识的关联数据（小微 ICT 业务场景）
-- 日期：2026-06-30
-- 安全：INSERT IGNORE 保证幂等
-- ============================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
USE fastrag;

-- ==================== 1. 补充真实 ICT 业务标签 ====================
-- 文档分类 (tag_type_id=T1)
INSERT IGNORE INTO kb_tag (id, kb_id, tag_type_id, name, color, description, usage_count, created_by) VALUES
('tag_faq',      'kb_sample', 'T1', 'FAQ',         '#ff85c0', '常见问答类文档',       0, 'admin'),
('tag_guide',    'kb_sample', 'T1', '操作指南',     '#36cfc9', '操作步骤与使用指导',     0, 'admin'),
('tag_standard', 'kb_sample', 'T1', '规范制度',     '#722ed1', '业务规范与管理制度',     0, 'admin'),
('tag_case',     'kb_sample', 'T1', '案例',         '#fa8c16', '典型案例与最佳实践',     0, 'admin'),
('tag_product',  'kb_sample', 'T1', '产品资料',     '#1890ff', '产品介绍与规格参数',     0, 'admin');

-- 优先级 (tag_type_id=T2)
INSERT IGNORE INTO kb_tag (id, kb_id, tag_type_id, name, color, description, usage_count, created_by) VALUES
('tag_urgent',  'kb_sample', 'T2', '紧急', '#ff4d4f', '需要优先处理的知识', 0, 'admin'),
('tag_normal',  'kb_sample', 'T2', '一般', '#52c41a', '常规知识',           0, 'admin');

-- 状态 (tag_type_id=T3)
INSERT IGNORE INTO kb_tag (id, kb_id, tag_type_id, name, color, description, usage_count, created_by) VALUES
('tag_draft',     'kb_sample', 'T3', '草稿',   '#d9d9d9', '尚未发布',       0, 'admin'),
('tag_published', 'kb_sample', 'T3', '已发布', '#52c41a', '已对外发布',     0, 'admin'),
('tag_reviewing', 'kb_sample', 'T3', '审核中', '#faad14', '正在审核流程中', 0, 'admin');

-- 业务主题标签（无标签类型）
INSERT IGNORE INTO kb_tag (id, kb_id, tag_type_id, name, color, description, usage_count, created_by) VALUES
('tag_broadband',  'kb_sample', NULL, '宽带',     '#1890ff', '宽带业务相关知识',   0, 'admin'),
('tag专线',        'kb_sample', NULL, '专线',     '#722ed1', '专线业务相关知识',   0, 'admin'),
('tag_cloud_pc',   'kb_sample', NULL, '云电脑',   '#36cfc9', '云电脑产品相关知识', 0, 'admin'),
('tag_5g',         'kb_sample', NULL, '5G',       '#ff85c0', '5G业务相关知识',     0, 'admin'),
('tag_fault',      'kb_sample', NULL, '故障排查', '#ff4d4f', '故障诊断与排除',     0, 'admin'),
('tag_billing',    'kb_sample', NULL, '费用账单', '#fa8c16', '资费与账单相关',     0, 'admin');

-- ==================== 2. 补充真实 ICT 业务知识 ====================
INSERT IGNORE INTO kb_knowledge (id, kb_id, title, content, category, status) VALUES
('know_001', 'kb_sample', '企业宽带新装办理流程', '详细介绍企业宽带新装的业务受理、现场勘测、方案设计、施工安装、开通验收等全流程。', '宽带', 'published'),
('know_002', 'kb_sample', '宽带常见问题FAQ', '汇总宽带使用中最常见的问题及解答，包括网速慢、无法上网、WiFi连接等。', 'FAQ', 'published'),
('know_003', 'kb_sample', '宽带故障自助排查指南', '指导用户通过简单步骤自行排查宽带故障，包括检查光猫、路由器、网线等。', '故障', 'published'),
('know_004', 'kb_sample', '企业宽带资费标准(2026版)', '2026年企业宽带各档次资费一览，包含带宽、价格、合约期等信息。', '资费', 'published'),
('know_005', 'kb_sample', '互联网专线接入方案', '介绍MSTP专线、PTN专线、裸光纤等不同专线接入方式的技术方案和适用场景。', '专线', 'published'),
('know_006', 'kb_sample', '专线业务办理条件与流程', '说明专线业务申请条件、所需材料、办理流程及工期预估。', '专线', 'published'),
('know_007', 'kb_sample', '云电脑产品介绍', '详细介绍云电脑产品功能、配置规格、适用场景及与传统PC的对比优势。', '云电脑', 'published'),
('know_008', 'kb_sample', '云电脑用户操作手册', '从登录到日常使用的完整操作指南，包含文件管理、外设连接、截图等。', '云电脑', 'published'),
('know_009', 'kb_sample', '5G套餐一览', '汇总当前可办理的5G套餐信息，包含流量、资费、权益等内容。', '5G', 'published'),
('know_010', 'kb_sample', '5G智慧园区应用案例', '某制造企业5G+工业互联网智慧园区建设典型案例。', '案例', 'published'),
('know_011', 'kb_sample', '装维服务操作规范', '装维人员上门服务的标准化操作流程，包括仪容仪表、服务用语、操作步骤。', '规范', 'published'),
('know_012', 'kb_sample', 'ICT项目交付流程规范', 'ICT项目从立项、设计、实施到验收的完整交付流程管理制度。', '规范', 'published'),
('know_013', 'kb_sample', '小微企业ICT解决方案模板', '面向小微企业的一站式ICT解决方案标准模板，含网络、通信、云服务。', '方案', 'published'),
('know_014', 'kb_sample', '知识库内容更新规范', '知识库内容的更新频率要求、审核流程、版本管理规范。', '规范', 'reviewing'),
('know_015', 'kb_sample', '客户投诉处理管理办法', '客户投诉的分级标准、处理时限、升级机制及考核办法。', '规范', 'published');

-- ==================== 3. 建立标签与知识的关联 ====================
-- 宽带标签 → 宽带相关知识
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_broadband', 'knowledge', 'know_001'),
('tag_broadband', 'knowledge', 'know_002'),
('tag_broadband', 'knowledge', 'know_003'),
('tag_broadband', 'knowledge', 'know_004');

-- 专线标签 → 专线相关知识
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_专线', 'knowledge', 'know_005'),
('tag_专线', 'knowledge', 'know_006');

-- 云电脑标签 → 云电脑相关知识
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_cloud_pc', 'knowledge', 'know_007'),
('tag_cloud_pc', 'knowledge', 'know_008');

-- 5G标签 → 5G相关知识
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_5g', 'knowledge', 'know_009'),
('tag_5g', 'knowledge', 'know_010');

-- 故障排查标签
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_fault', 'knowledge', 'know_003');

-- 费用账单标签
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_billing', 'knowledge', 'know_004');

-- FAQ标签 → FAQ类文档
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_faq', 'knowledge', 'know_002');

-- 操作指南标签 → 操作指南类文档
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_guide', 'knowledge', 'know_003'),
('tag_guide', 'knowledge', 'know_008');

-- 规范制度标签 → 规范类文档
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_standard', 'knowledge', 'know_011'),
('tag_standard', 'knowledge', 'know_012'),
('tag_standard', 'knowledge', 'know_014'),
('tag_standard', 'knowledge', 'know_015');

-- 案例标签 → 案例类文档
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_case', 'knowledge', 'know_010'),
('tag_case', 'knowledge', 'know_013');

-- 产品资料标签 → 产品类文档
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_product', 'knowledge', 'know_004'),
('tag_product', 'knowledge', 'know_007'),
('tag_product', 'knowledge', 'know_009');

-- 已发布状态标签 → 已发布知识
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_published', 'knowledge', 'know_001'),
('tag_published', 'knowledge', 'know_004'),
('tag_published', 'knowledge', 'know_007'),
('tag_published', 'knowledge', 'know_011');

-- 审核中状态标签
INSERT IGNORE INTO kb_tag_relation (tag_id, target_type, target_id) VALUES
('tag_reviewing', 'knowledge', 'know_014');

-- ==================== 4. 更新标签使用次数 ====================
UPDATE kb_tag t SET usage_count = (SELECT COUNT(*) FROM kb_tag_relation r WHERE r.tag_id = t.id);
