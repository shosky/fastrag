-- H2-compatible test data for Hunan Telecom organization
SET MODE MySQL;

-- ==================== 组织架构 ====================
MERGE INTO sys_org (id, name, alias, parent_id, level, sort) KEY (id) VALUES
-- L1 省公司
('org_han_hq', '中国电信湖南分公司', '省公司', 'root', 1, 0),
-- L2 省公司职能部门
('org_han_zonghe',    '综合部',      '综合部',     'org_han_hq', 2, 1),
('org_han_hr',        '人力资源部',    '人资部',     'org_han_hq', 2, 2),
('org_han_finance',   '财务部',       '财务部',     'org_han_hq', 2, 3),
('org_han_gov',       '政企客户部',    '政企部',     'org_han_hq', 2, 4),
('org_han_consumer',  '个人客户部',    '个客部',     'org_han_hq', 2, 5),
('org_han_network',   '网络部',       '网络部',     'org_han_hq', 2, 6),
('org_han_tech',      '技术部',       '技术部',     'org_han_hq', 2, 7),
('org_han_data',      '数据智能部',    '数据部',     'org_han_hq', 2, 8),
('org_han_cloud',     '云网运营部',    '云网部',     'org_han_hq', 2, 9),
('org_han_security',  '安全部',       '安全部',     'org_han_hq', 2, 10),
-- L2 地市分公司
('org_han_cs',  '长沙分公司',   '长沙',   'org_han_hq', 2, 20),
('org_han_zz',  '株洲分公司',   '株洲',   'org_han_hq', 2, 21),
('org_han_xt',  '湘潭分公司',   '湘潭',   'org_han_hq', 2, 22),
('org_han_hy',  '衡阳分公司',   '衡阳',   'org_han_hq', 2, 23),
('org_han_sy',  '邵阳分公司',   '邵阳',   'org_han_hq', 2, 24),
('org_han_yy',  '岳阳分公司',   '岳阳',   'org_han_hq', 2, 25),
('org_han_cd',  '常德分公司',   '常德',   'org_han_hq', 2, 26),
('org_han_zjj', '张家界分公司', '张家界', 'org_han_hq', 2, 27),
('org_han_yiy', '益阳分公司',   '益阳',   'org_han_hq', 2, 28),
('org_han_ld',  '娄底分公司',   '娄底',   'org_han_hq', 2, 29),
('org_han_cz',  '郴州分公司',   '郴州',   'org_han_hq', 2, 30),
('org_han_yz',  '永州分公司',   '永州',   'org_han_hq', 2, 31),
('org_han_hh',  '怀化分公司',   '怀化',   'org_han_hq', 2, 32),
('org_han_xx',  '湘西分公司',   '湘西',   'org_han_hq', 2, 33),
-- L3 科室
('org_han_office',     '办公室',     '办公室',   'org_han_zonghe', 3, 1),
('org_han_logistics',  '后勤保障部',  '后勤部',   'org_han_zonghe', 3, 2),
('org_han_ops',        '运行维护部',  '运维部',   'org_han_network', 3, 1),
('org_han_resource',   '网络资源部',  '资源部',   'org_han_network', 3, 2),
('org_han_rd',         '研发中心',   '研发中心', 'org_han_tech', 3, 1),
('org_han_innovation', '创新业务部',  '创新部',   'org_han_tech', 3, 2),
-- L3 区县经营部（示例）
('org_han_cs_furong',   '芙蓉区经营部', '芙蓉区', 'org_han_cs', 3, 1),
('org_han_cs_yuelu',    '岳麓区经营部', '岳麓区', 'org_han_cs', 3, 2),
('org_han_cs_tianxin',  '天心区经营部', '天心区', 'org_han_cs', 3, 3),
('org_han_hy_zhongshan','石鼓区经营部', '石鼓区', 'org_han_hy', 3, 1),
('org_han_hy_yanfeng',  '雁峰区经营部', '雁峰区', 'org_han_hy', 3, 2),
('org_han_yy_yueyang',  '岳阳楼区经营部', '岳阳楼', 'org_han_yy', 3, 1),
('org_han_yy_junshan',  '君山区经营部',   '君山',   'org_han_yy', 3, 2);

-- ==================== 角色 ====================
MERGE INTO sys_role (id, role_key, name, description, is_default, is_system) KEY (id) VALUES
('role_super_admin', 'super_admin', '超级管理员', '系统超级管理员，拥有所有权限', 0, 1),
('role_kb_admin',    'kb_admin',    '知识库管理员', '管理知识库的创建、编辑、删除等', 0, 1),
('role_kb_user',     'kb_user',     '知识库用户', '普通用户，可查看和搜索知识库', 1, 1),
('role_readonly',    'readonly',    '只读用户', '只能查看，不能编辑', 0, 1),
('role_dept_mgr',    'dept_mgr',    '部门经理', '部门负责人，可管理本部门人员和知识库', 0, 0),
('role_staff',       'staff',       '普通员工', '普通员工，可使用知识库和应用', 0, 0);

-- ==================== 用户 ====================
-- 密码 123456 的 bcrypt 哈希: $2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u
-- 密码 admin123 的 bcrypt 哈希: $2b$10$kwtu6144DqB4Q4IqDbbsROvyxm.gYLLizL0Qbvm0otat.MBfaoJiG

MERGE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) KEY (id) VALUES
-- 超级管理员
('user_admin',    'admin',       '超级管理员', '',              'admin@fastrag.com', '$2b$10$kwtu6144DqB4Q4IqDbbsROvyxm.gYLLizL0Qbvm0otat.MBfaoJiG', 'role_super_admin', 'enabled', 'org_han_hq', 10737418240, 0),
-- 省公司领导
('user_zhang_jg', 'zhangjianguo','张建国',    '0731-85100001', 'zhangjianguo@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_super_admin', 'enabled', 'org_han_hq', 10737418240, 0),
('user_li_mh',    'liminghui',   '李明辉',    '0731-85100002', 'liminghui@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_super_admin', 'enabled', 'org_han_hq', 10737418240, 0),
-- 综合部
('user_wang_xy',  'wangxiuying','王秀英',    '0731-85101001', 'wangxiuying@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_zonghe', 10737418240, 0),
('user_wang_lin', 'wanglin',    '王琳',      '0731-85101002', 'wanglin@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_office', 10737418240, 0),
-- 人力资源部
('user_chen_zq',  'chenzhiqiang','陈志强',   '0731-85102001', 'chenzhiqiang@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_hr', 10737418240, 0),
-- 财务部
('user_liu_fn',   'liufang',    '刘芳',      '0731-85103001', 'liufang@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_finance', 10737418240, 0),
-- 政企客户部
('user_zhao_dp',  'zhaodapeng', '赵大鹏',    '0731-85104001', 'zhaodapeng@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_gov', 10737418240, 0),
('user_peng_l',   'pengli',     '彭丽',      '0731-85104002', 'pengli@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_gov', 10737418240, 0),
-- 个人客户部
('user_sun_lh',   'sunlihua',   '孙丽华',    '0731-85105001', 'sunlihua@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_consumer', 10737418240, 0),
-- 网络部
('user_zhou_w',   'zhouwei',    '周伟',      '0731-85106001', 'zhouwei@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_network', 10737418240, 0),
-- 技术部
('user_wu_xf',    'wuxiaofeng', '吴晓峰',    '0731-85107001', 'wuxiaofeng@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_tech', 10737418240, 0),
('user_cao_l',    'caolei',     '曹磊',      '0731-85107002', 'caolei@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_rd', 10737418240, 0),
-- 数据智能部
('user_zheng_yw', 'zhengyawen', '郑雅文',    '0731-85108001', 'zhengyawen@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_data', 10737418240, 0),
('user_feng_xh',  'fengxiaohong','冯晓红',   '0731-85108002', 'fengxiaohong@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_kb_admin', 'enabled', 'org_han_data', 10737418240, 0),
-- 云网运营部
('user_ma_c',     'machao',     '马超',      '0731-85109001', 'machao@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_cloud', 10737418240, 0),
-- 安全部
('user_lin_j',    'linjing',    '林静',      '0731-85110001', 'linjing@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_security', 10737418240, 0),
-- 地市分公司（部分示例）
('user_huang_zy', 'huangzhiyuan','黄志远',   '0731-85120001', 'huangzhiyuan@hn.chinatelecom.com', '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_cs', 10737418240, 0);

-- ==================== 用户-角色关联 ====================
MERGE INTO sys_user_role (id, user_id, role_id) KEY (id) VALUES
('sur_001', 'user_admin',    'role_super_admin'),
('sur_002', 'user_zhang_jg', 'role_super_admin'),
('sur_003', 'user_li_mh',    'role_super_admin'),
('sur_004', 'user_wang_xy',  'role_dept_mgr'),
('sur_005', 'user_wang_lin', 'role_staff'),
('sur_006', 'user_chen_zq',  'role_dept_mgr'),
('sur_007', 'user_liu_fn',   'role_dept_mgr'),
('sur_008', 'user_zhao_dp',  'role_dept_mgr'),
('sur_009', 'user_peng_l',   'role_staff'),
('sur_010', 'user_sun_lh',   'role_dept_mgr'),
('sur_011', 'user_zhou_w',   'role_dept_mgr'),
('sur_012', 'user_wu_xf',    'role_dept_mgr'),
('sur_013', 'user_cao_l',    'role_staff'),
('sur_014', 'user_zheng_yw', 'role_dept_mgr'),
('sur_015', 'user_feng_xh',  'role_kb_admin'),
('sur_016', 'user_ma_c',     'role_dept_mgr'),
('sur_017', 'user_lin_j',    'role_dept_mgr'),
('sur_018', 'user_huang_zy', 'role_dept_mgr');

-- ==================== 系统配置 ====================
MERGE INTO sys_config (id, config_key, config_value, config_type, description, is_default, is_system) KEY (id) VALUES
('brand_01', 'system_name', 'AIS 智能知识服务平台', 'brand', '系统名称', 1, 1),
('brand_02', 'system_slogan', '让知识触手可及', 'brand', '宣传语', 1, 1),
('brand_03', 'copyright', '', 'brand', '版权信息', 1, 1),
('brand_04', 'logo_url', '', 'brand', '系统 Logo URL', 1, 1),
('brand_05', 'org_name', '{"value":"中国电信湖南分公司"}', 'brand', '组织名称', 1, 1),
('general_01', 'general_settings', '{"defaultLanguage":"zh-CN","timezone":"Asia/Shanghai"}', 'general', '通用设置', 1, 1),
('general_02', 'general_chunk_size', '512', 'general', '默认分片大小', 1, 1),
('general_03', 'general_chunk_overlap', '50', 'general', '默认分片重叠', 1, 1),
('general_04', 'general_search_top_k', '10', 'general', '默认搜索返回条数', 1, 1),
('general_05', 'general_retrieval_mode', 'hybrid', 'general', '默认检索模式', 1, 1),
('general_06', 'general_enable_rerank', 'false', 'general', '是否启用重排序', 1, 1),
('general_07', 'general_max_tokens', '2048', 'general', '默认最大生成Token数', 1, 1);

-- ==================== 工作流模板 ====================
MERGE INTO wf_template (id, name, category, description, config, is_builtin) KEY (id) VALUES
('t1', '智能问答工作流', '问答', '意图识别→知识库检索→LLM回答', '{}', 1),
('t2', '多轮对话工作流', '对话', '上下文管理→意图识别→知识检索→回答生成', '{}', 1);
