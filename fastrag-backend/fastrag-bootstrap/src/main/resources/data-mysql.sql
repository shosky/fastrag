-- ============================================================
-- 湖南电信分公司 组织架构初始化数据
-- 模拟中国电信股份有限公司湖南分公司及其下属地市分公司
-- ============================================================
-- 说明：
--   本脚本替换原有的 FastRAG 总部组织，构建湖南电信三级组织架构：
--     L1 省公司   → L2 职能部门/地市分公司 → L3 区县经营部
--   所有新人员工默认密码：123456（bcrypt 已预编码）
--   超级管理员密码：admin123
-- ============================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ==================== 建表（确保 IAM 表存在，避免数据插入失败） ====================
CREATE TABLE IF NOT EXISTS sys_org (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    alias VARCHAR(64),
    parent_id VARCHAR(32) DEFAULT 'root',
    level INT DEFAULT 1,
    sort INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- API Token（平台级程序化访问）
CREATE TABLE IF NOT EXISTS sys_api_token (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    token VARCHAR(256) NOT NULL,
    permission VARCHAR(16) DEFAULT 'read',
    expires_at DATETIME DEFAULT NULL,
    revoked INT DEFAULT 0,
    created_by VARCHAR(32),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_api_token_value (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
    id VARCHAR(32) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    real_name VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    password_hash VARCHAR(256) NOT NULL,
    role_id VARCHAR(32),
    status VARCHAR(16) DEFAULT 'enabled',
    org_id VARCHAR(32),
    storage_quota BIGINT DEFAULT 10737418240,
    storage_used BIGINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role (
    id VARCHAR(32) PRIMARY KEY,
    role_key VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    is_default TINYINT DEFAULT 0,
    is_system TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    perm_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    `group` VARCHAR(32),
    parent_key VARCHAR(64),
    category VARCHAR(16) DEFAULT 'page_action' COMMENT '权限分类: menu=菜单权限, page_action=页面操作, api=API接口',
    description VARCHAR(256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id VARCHAR(32) NOT NULL,
    permission_key VARCHAR(64) NOT NULL,
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    role_id VARCHAR(32) NOT NULL,
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ==================== 初始化数据（使用 INSERT IGNORE 避免主键冲突） ====================
-- 注：所有 INSERT 均已改为 INSERT IGNORE，脚本可安全重复执行
INSERT IGNORE INTO sys_role (id, role_key, name, description, is_default, is_system) VALUES
('role_super_admin', 'super_admin', '超级管理员', '系统超级管理员，拥有所有权限', 0, 1),
('role_kb_admin',    'kb_admin',    '知识库管理员', '管理知识库的创建、编辑、删除等', 0, 1),
('role_kb_user',     'kb_user',     '知识库用户', '普通用户，可查看和搜索知识库', 1, 1),
('role_readonly',    'readonly',    '只读用户', '只能查看，不能编辑', 0, 1),
('role_dept_mgr',    'dept_mgr',    '部门经理', '部门负责人，可管理本部门人员和知识库', 0, 0),
('role_staff',       'staff',       '普通员工', '普通员工，可使用知识库和应用', 0, 0);

-- ==================== 权限分配（保留原有，新增角色权限） ====================
-- 超级管理员拥有所有权限（已通过 SELECT * FROM sys_permission 完成）
INSERT IGNORE INTO sys_role_permission (role_id, permission_key)
SELECT 'role_super_admin', perm_key FROM sys_permission;

-- KB 管理员权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_key) VALUES
('role_kb_admin', 'menu:home'), ('role_kb_admin', 'menu:knowledge'),
('role_kb_admin', 'menu:knowledge:list'), ('role_kb_admin', 'menu:knowledge:categories'),
('role_kb_admin', 'menu:operation:kb-analytics'), ('role_kb_admin', 'menu:operation:retrieval-analysis'),
('role_kb_admin', 'menu:operation:feedback'), ('role_kb_admin', 'menu:operation:model-monitor'),
('role_kb_admin', 'menu:admin'),
('role_kb_admin', 'menu:admin:index'),
('role_kb_admin', 'menu:admin:system'), ('role_kb_admin', 'menu:admin:system:kb-config'),
('role_kb_admin', 'menu:admin:system:terminology'),
('role_kb_admin', 'menu:admin:account'),
('role_kb_admin', 'menu:admin:account:org'), ('role_kb_admin', 'menu:admin:account:personnel'),
('role_kb_admin', 'menu:admin:account:permissions'),
('role_kb_admin', 'kb:create'), ('role_kb_admin', 'kb:edit'), ('role_kb_admin', 'kb:delete'),
('role_kb_admin', 'kb:upload'), ('role_kb_admin', 'kb:manage_chunks'), ('role_kb_admin', 'kb:manage_graph'),
('role_kb_admin', 'kb:manage_eval'), ('role_kb_admin', 'kb:manage_strategy'), ('role_kb_admin', 'kb:view'),
('role_kb_admin', 'kb:search'), ('role_kb_admin', 'kb:acl_manage'),
('role_kb_admin', 'admin:access'), ('role_kb_admin', 'admin:org'), ('role_kb_admin', 'admin:user'),
('role_kb_admin', 'admin:user:create'), ('role_kb_admin', 'admin:user:edit'), ('role_kb_admin', 'admin:user:disable'),
('role_kb_admin', 'kb:manage');

-- KB 用户权限（普通用户仅限知识库，无应用功能）
INSERT IGNORE INTO sys_role_permission (role_id, permission_key) VALUES
('role_kb_user', 'menu:home'), ('role_kb_user', 'menu:knowledge'), ('role_kb_user', 'menu:knowledge:list'),
('role_kb_user', 'menu:knowledge:categories'),
('role_kb_user', 'kb:view'), ('role_kb_user', 'kb:search'), ('role_kb_user', 'kb:upload'),
('role_kb_user', 'kb:manage_chunks'), ('role_kb_user', 'kb:manage_graph'),
('role_kb_user', 'kb:manage_eval'), ('role_kb_user', 'kb:manage_strategy'),
('role_kb_user', 'kb:create'), ('role_kb_user', 'kb:edit'),
('role_kb_user', 'qa:manage'), ('role_kb_user', 'testcase:manage');
-- 移除历史版本误授予的应用权限（INSERT IGNORE 不会删除已存在记录，需显式清理）
DELETE FROM sys_role_permission
WHERE role_id='role_kb_user' AND permission_key IN ('menu:application','menu:application:center','app:use');

-- 只读用户权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_key) VALUES
('role_readonly', 'menu:home'), ('role_readonly', 'menu:knowledge'), ('role_readonly', 'menu:knowledge:list'),
('role_readonly', 'menu:application'), ('role_readonly', 'menu:application:center'),
('role_readonly', 'kb:view'), ('role_readonly', 'kb:search'),
('role_readonly', 'app:use');

-- 部门经理权限（可管理本部门人员、查看知识库、使用应用）
INSERT IGNORE INTO sys_role_permission (role_id, permission_key) VALUES
('role_dept_mgr', 'menu:home'), ('role_dept_mgr', 'menu:knowledge'),
('role_dept_mgr', 'menu:knowledge:list'), ('role_dept_mgr', 'menu:application'),
('role_dept_mgr', 'menu:application:center'),
('role_dept_mgr', 'kb:view'), ('role_dept_mgr', 'kb:search'), ('role_dept_mgr', 'kb:upload'),
('role_dept_mgr', 'kb:create'), ('role_dept_mgr', 'kb:edit'), ('role_dept_mgr', 'kb:acl_manage'),
('role_dept_mgr', 'app:use'), ('role_dept_mgr', 'app:member'),
('role_dept_mgr', 'admin:access'), ('role_dept_mgr', 'admin:org'), ('role_dept_mgr', 'admin:user');

-- 普通员工权限
INSERT IGNORE INTO sys_role_permission (role_id, permission_key) VALUES
('role_staff', 'menu:home'), ('role_staff', 'menu:knowledge'),
('role_staff', 'menu:knowledge:list'), ('role_staff', 'menu:application'),
('role_staff', 'menu:application:center'),
('role_staff', 'kb:view'), ('role_staff', 'kb:search'),
('role_staff', 'app:use'), ('role_staff', 'qa:manage');

-- ==================== 组织架构 ====================

-- L1 省公司（根组织）
INSERT IGNORE INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
('org_han_hq', '中国电信湖南分公司', '省公司', 'root', 1, 0);

-- L2 省公司职能部门
INSERT IGNORE INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
('org_han_zonghe',    '综合部',      '综合部',     'org_han_hq',    2, 1),
('org_han_hr',        '人力资源部',    '人资部',     'org_han_hq',    2, 2),
('org_han_finance',   '财务部',       '财务部',     'org_han_hq',    2, 3),
('org_han_gov',       '政企客户部',    '政企部',     'org_han_hq',    2, 4),
('org_han_consumer',  '个人客户部',    '个客部',     'org_han_hq',    2, 5),
('org_han_network',   '网络部',       '网络部',     'org_han_hq',    2, 6),
('org_han_tech',      '技术部',       '技术部',     'org_han_hq',    2, 7),
('org_han_data',      '数据智能部',    '数据部',     'org_han_hq',    2, 8),
('org_han_cloud',     '云网运营部',    '云网部',     'org_han_hq',    2, 9),
('org_han_security',  '安全部',       '安全部',     'org_han_hq',    2, 10);

-- L2 地市分公司（湖南省14个地市州）
INSERT IGNORE INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
('org_han_cs',  '长沙分公司',     '长沙',     'org_han_hq',  2, 20),
('org_han_zz',  '株洲分公司',     '株洲',     'org_han_hq',  2, 21),
('org_han_xt',  '湘潭分公司',     '湘潭',     'org_han_hq',  2, 22),
('org_han_hy',  '衡阳分公司',     '衡阳',     'org_han_hq',  2, 23),
('org_han_sy',  '邵阳分公司',     '邵阳',     'org_han_hq',  2, 24),
('org_han_yy',  '岳阳分公司',     '岳阳',     'org_han_hq',  2, 25),
('org_han_cd',  '常德分公司',     '常德',     'org_han_hq',  2, 26),
('org_han_zjj', '张家界分公司',   '张家界',   'org_han_hq',  2, 27),
('org_han_yiy','益阳分公司',      '益阳',     'org_han_hq',  2, 28),
('org_han_ld',  '娄底分公司',     '娄底',     'org_han_hq',  2, 29),
('org_han_cz',  '郴州分公司',     '郴州',     'org_han_hq',  2, 30),
('org_han_yz',  '永州分公司',     '永州',     'org_han_hq',  2, 31),
('org_han_hh',  '怀化分公司',     '怀化',     'org_han_hq',  2, 32),
('org_han_xx',  '湘西分公司',     '湘西',     'org_han_hq',  2, 33);

-- L3 综合部下属科室
INSERT IGNORE INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
('org_han_office',     '办公室',       '办公室',   'org_han_zonghe', 3, 1),
('org_han_logistics',  '后勤保障部',    '后勤部',   'org_han_zonghe', 3, 2);

-- L3 网络部下属科室
INSERT IGNORE INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
('org_han_ops',     '运行维护部',    '运维部', 'org_han_network', 3, 1),
('org_han_resource','网络资源部',    '资源部', 'org_han_network', 3, 2);

-- L3 技术部下属科室
INSERT IGNORE INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
('org_han_rd',         '研发中心',      '研发中心', 'org_han_tech',  3, 1),
('org_han_innovation', '创新业务部',    '创新部',   'org_han_tech',  3, 2);

-- L3 地市分公司下属区县经营部（示例：长沙、衡阳、岳阳）
INSERT IGNORE INTO sys_org (id, name, alias, parent_id, level, sort) VALUES
-- 长沙分公司区县
('org_han_cs_furong',   '芙蓉区经营部', '芙蓉区', 'org_han_cs', 3, 1),
('org_han_cs_yuelu',    '岳麓区经营部', '岳麓区', 'org_han_cs', 3, 2),
('org_han_cs_tianxin',  '天心区经营部', '天心区', 'org_han_cs', 3, 3),
-- 衡阳分公司区县
('org_han_hy_zhongshan','石鼓区经营部', '石鼓区', 'org_han_hy', 3, 1),
('org_han_hy_yanfeng',  '雁峰区经营部', '雁峰区', 'org_han_hy', 3, 2),
-- 岳阳分公司区县
('org_han_yy_yueyang',  '岳阳楼区经营部', '岳阳楼', 'org_han_yy', 3, 1),
('org_han_yy_junshan',  '君山区经营部',   '君山',   'org_han_yy', 3, 2);

-- ==================== 用户（员工） ====================
-- 密码说明：
--   超级管理员 admin → admin123  → $2b$10$kwtu6144DqB4Q4IqDbbsROvyxm.gYLLizL0Qbvm0otat.MBfaoJiG
--   其余所有用户     → 123456    → $2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u

-- ---- 超级管理员 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_admin',     'admin',       '超级管理员', '',              'admin@fastrag.com',
 '$2b$10$kwtu6144DqB4Q4IqDbbsROvyxm.gYLLizL0Qbvm0otat.MBfaoJiG', 'role_super_admin', 'enabled', 'org_han_hq', 10737418240, 0);

-- ---- 省公司领导层 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_zhang_jg', 'zhangjianguo', '张建国', '0731-85100001', 'zhangjianguo@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_super_admin', 'enabled', 'org_han_zonghe', 10737418240, 0),
('user_li_mh',     'liminghui',    '李明辉', '0731-85100002', 'liminghui@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_super_admin', 'enabled', 'org_han_zonghe', 10737418240, 0);

-- ---- 综合部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_wang_xy',   'wangxiuying', '王秀英', '0731-85101001', 'wangxiuying@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_zonghe', 10737418240, 0),
('user_wang_lin',  'wanglin',     '王琳',   '0731-85101002', 'wanglin@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_office', 10737418240, 0),
('user_liu_yf',    'liuyanfang',  '刘艳芳', '0731-85101003', 'liuyanfang@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_logistics', 10737418240, 0);

-- ---- 人力资源部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_chen_zq',   'chenzhiqiang','陈志强', '0731-85102001', 'chenzhiqiang@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_hr', 10737418240, 0),
('user_jiang_t',   'jiangtao',    '蒋涛',   '0731-85102002', 'jiangtao@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_hr', 10737418240, 0);

-- ---- 财务部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_liu_fn',    'liufang',     '刘芳',   '0731-85103001', 'liufang@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_finance', 10737418240, 0),
('user_yuan_fn',   'yuanfang',    '袁芳',   '0731-85103002', 'yuanfang@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_finance', 10737418240, 0);

-- ---- 政企客户部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_zhao_dp',   'zhaodapeng',  '赵大鹏', '0731-85104001', 'zhaodapeng@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_gov', 10737418240, 0),
('user_peng_l',    'pengli',      '彭丽',   '0731-85104002', 'pengli@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_gov', 10737418240, 0);

-- ---- 个人客户部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_sun_lh',    'sunlihua',    '孙丽华', '0731-85105001', 'sunlihua@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_consumer', 10737418240, 0),
('user_he_xy',     'hexiaoyan',   '何小燕', '0731-85105002', 'hexiaoyan@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_consumer', 10737418240, 0);

-- ---- 网络部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_zhou_w',    'zhouwei',     '周伟',   '0731-85106001', 'zhouwei@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_network', 10737418240, 0),
('user_dong_w',    'dongwei',     '董伟',   '0731-85106002', 'dongwei@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_ops', 10737418240, 0),
('user_xu_hb',     'xuhongbin',   '徐红兵', '0731-85106003', 'xuhongbin@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_resource', 10737418240, 0);

-- ---- 技术部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_wu_xf',     'wuxiaofeng',  '吴晓峰', '0731-85107001', 'wuxiaofeng@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_tech', 10737418240, 0),
('user_cao_l',     'caolei',      '曹磊',   '0731-85107002', 'caolei@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_rd', 10737418240, 0),
('user_tang_y',    'tangyu',      '唐宇',   '0731-85107003', 'tangyu@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_innovation', 10737418240, 0);

-- ---- 数据智能部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_zheng_yw',  'zhengyawen',  '郑雅文', '0731-85108001', 'zhengyawen@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_data', 10737418240, 0),
('user_feng_xh',   'fengxiaohong', '冯晓红', '0731-85108002', 'fengxiaohong@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_kb_admin', 'enabled', 'org_han_data', 10737418240, 0);

-- ---- 云网运营部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_ma_c',      'machao',      '马超',   '0731-85109001', 'machao@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_cloud', 10737418240, 0);

-- ---- 安全部 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_lin_j',     'linjing',     '林静',   '0731-85110001', 'linjing@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_security', 10737418240, 0);

-- ---- 长沙分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_huang_zy',  'huangzhiyuan', '黄志远', '0731-85120001', 'huangzhiyuan@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_cs', 10737418240, 0),
('user_zhang_m',   'zhangming',   '张明',   '0731-85120002', 'zhangming@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_cs_furong', 10737418240, 0);

-- ---- 株洲分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_yang_xl',   'yangxiulan',  '杨秀兰', '0733-28100001', 'yangxiulan@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_zz', 10737418240, 0);

-- ---- 湘潭分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_xu_jh',     'xujianhua',   '许建华', '0731-58200001', 'xujianhua@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_xt', 10737418240, 0);

-- ---- 衡阳分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_he_jp',     'hejianping',  '何建平', '0734-81400001', 'hejianping@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_hy', 10737418240, 0),
('user_liu_hy',    'liuhaiyang',  '刘海阳', '0734-81400002', 'liuhaiyang@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_staff',    'enabled', 'org_han_hy_zhongshan', 10737418240, 0);

-- ---- 邵阳分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_luo_m',     'luomin',      '罗敏',   '0739-5010001', 'luomin@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_sy', 10737418240, 0);

-- ---- 岳阳分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_xie_zg',    'xiezhigang',  '谢志刚', '0730-8840001', 'xiezhigang@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_yy', 10737418240, 0);

-- ---- 常德分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_tang_mh',   'tangminghui', '唐明辉', '0736-7320001', 'tangminghui@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_cd', 10737418240, 0);

-- ---- 张家界分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_hu_wj',     'huwenjuan',   '胡文娟', '0744-8220001', 'huwenjuan@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_zjj', 10737418240, 0);

-- ---- 益阳分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_zhou_cm',   'zhouchenming', '周晨明', '0737-4310001', 'zhouchenming@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_yiyang', 10737418240, 0);

-- ---- 娄底分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_lei_j',     'leijun',      '雷军',   '0738-8920001', 'leijun@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_ld', 10737418240, 0);

-- ---- 郴州分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_chen_dh',   'chen Dahai',   '陈大海', '0735-2220001', 'chendahai@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_cz', 10737418240, 0);

-- ---- 永州分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_ou_yh',     'ouyanghui',   '欧阳辉', '0746-8440001', 'ouyanghui@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_yz', 10737418240, 0);

-- ---- 怀化分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_tan_y',     'tanyong',     '谭勇',   '0745-2710001', 'tanyong@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_hh', 10737418240, 0);

-- ---- 湘西分公司 ----
INSERT IGNORE INTO sys_user (id, username, real_name, phone, email, password_hash, role_id, status, org_id, storage_quota, storage_used) VALUES
('user_bian_wm',   'bianweimin',  '卞伟民', '0743-8750001', 'bianweimin@hn.chinatelecom.com',
 '$2a$10$q98A1ogdFy22WwwMC2suP.SOA.p8HVB0h3s0QDK1Kd4K/9AWnKl1u', 'role_dept_mgr', 'enabled', 'org_han_xx', 10737418240, 0);

-- ==================== 超级管理员（更新组织归属） ====================
-- ==================== 用户-角色关联 ====================
-- 超级管理员
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_admin',    'role_super_admin'),
('user_zhang_jg', 'role_super_admin'),
('user_li_mh',    'role_super_admin');

-- 综合部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_wang_xy',  'role_dept_mgr'),
('user_wang_lin', 'role_staff'),
('user_liu_yf',   'role_staff');

-- 人力资源部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_chen_zq',  'role_dept_mgr'),
('user_jiang_t',  'role_staff');

-- 财务部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_liu_fn',   'role_dept_mgr'),
('user_yuan_fn',  'role_staff');

-- 政企客户部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_zhao_dp',  'role_dept_mgr'),
('user_peng_l',   'role_staff');

-- 个人客户部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_sun_lh',   'role_dept_mgr'),
('user_he_xy',    'role_staff');

-- 网络部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_zhou_w',   'role_dept_mgr'),
('user_dong_w',   'role_staff'),
('user_xu_hb',    'role_staff');

-- 技术部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_wu_xf',    'role_dept_mgr'),
('user_cao_l',    'role_staff'),
('user_tang_y',   'role_staff');

-- 数据智能部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_zheng_yw', 'role_dept_mgr'),
('user_feng_xh',  'role_kb_admin');

-- 云网运营部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_ma_c',     'role_dept_mgr');

-- 安全部
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
('user_lin_j',    'role_dept_mgr');

-- 地市分公司（部门经理 + 普通员工）
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
-- 长沙
('user_huang_zy', 'role_dept_mgr'),
('user_zhang_m',  'role_staff'),
-- 株洲
('user_yang_xl',  'role_dept_mgr'),
-- 湘潭
('user_xu_jh',    'role_dept_mgr'),
-- 衡阳
('user_he_jp',    'role_dept_mgr'),
('user_liu_hy',   'role_staff'),
-- 邵阳
('user_luo_m',    'role_dept_mgr'),
-- 岳阳
('user_xie_zg',   'role_dept_mgr'),
-- 常德
('user_tang_mh',  'role_dept_mgr'),
-- 张家界
('user_hu_wj',    'role_dept_mgr'),
-- 益阳
('user_zhou_cm',  'role_dept_mgr'),
-- 娄底
('user_lei_j',    'role_dept_mgr'),
-- 郴州
('user_chen_dh',  'role_dept_mgr'),
-- 永州
('user_ou_yh',    'role_dept_mgr'),
-- 怀化
('user_tan_y',    'role_dept_mgr'),
-- 湘西
('user_bian_wm',  'role_dept_mgr');

-- ==================== 知识库示例（湖南电信业务知识） ====================

-- 知识库分类
INSERT IGNORE INTO kb_category (id, name, description, color, icon, sort, created_by) VALUES
('cat_policy',   '规章制度', '公司制度、管理办法', '#1890ff', 'file-text', 1, 'user_admin'),
('cat_product',  '产品方案', '业务产品及营销方案', '#52c41a', 'gift',      2, 'user_admin'),
('cat_network',  '网络运维', '网络运行维护知识', '#faad14', 'cloud',     3, 'user_admin'),
('cat_tech',     '技术方案', '技术架构、研发文档', '#722ed1', 'code',      4, 'user_admin'),
('cat_service',  '客户服务', '服务规范、典型案例', '#eb2f96', 'customer-service', 5, 'user_admin');

-- 知识库
INSERT IGNORE INTO kb (id, name, description, category, permission, creator, embedding_model, dimension, type, tags, parse_mode, split_mode) VALUES
('kb_hn_policy',  '湖南电信规章制度库',  '公司内部规章制度、管理办法、流程规范', '规章制度', 'internal', 'user_admin', 'bge-m3', 1024, 'team', '["制度","流程","管理"]', 'standard', 'paragraph'),
('kb_hn_product', '湖南电信产品方案库',  '业务产品介绍、营销方案、资费政策',   '产品方案', 'internal', 'user_admin', 'bge-m3', 1024, 'team', '["产品","方案","资费"]', 'standard', 'paragraph'),
('kb_hn_network', '湖南电信网络运维库',  '网络运行维护手册、故障处理、应急预案', '网络运维', 'internal', 'user_admin', 'bge-m3', 1024, 'team', '["网络","运维","故障"]', 'standard', 'paragraph'),
('kb_hn_tech',    '湖南电信技术方案库',  '技术架构文档、研发规范、平台使用指南', '技术方案', 'internal', 'user_admin', 'bge-m3', 1024, 'team', '["技术","架构","研发"]', 'standard', 'paragraph'),
('kb_hn_service', '湖南电信客户服务库',  '服务规范、典型案例、客户诉求处理',   '客户服务', 'internal', 'user_admin', 'bge-m3', 1024, 'team', '["服务","案例","客户"]', 'standard', 'paragraph');

-- 知识库权限分配
INSERT IGNORE INTO kb_acl (kb_id, user_id, kb_role, granted_by) VALUES
-- 省公司管理层拥有所有知识库权限
('kb_hn_policy',  'user_admin',    'owner', 'user_admin'),
('kb_hn_product', 'user_admin',    'owner', 'user_admin'),
('kb_hn_network', 'user_admin',    'owner', 'user_admin'),
('kb_hn_tech',    'user_admin',    'owner', 'user_admin'),
('kb_hn_service', 'user_admin',    'owner', 'user_admin'),
('kb_hn_policy',  'user_zhang_jg', 'editor', 'user_admin'),
('kb_hn_product', 'user_zhang_jg', 'editor', 'user_admin'),
-- 各部门负责人
('kb_hn_policy',  'user_wang_xy',  'editor', 'user_admin'),
('kb_hn_product', 'user_zhao_dp',  'editor', 'user_admin'),
('kb_hn_network', 'user_zhou_w',   'editor', 'user_admin'),
('kb_hn_tech',    'user_wu_xf',    'editor', 'user_admin'),
('kb_hn_service', 'user_sun_lh',   'editor', 'user_admin'),
('kb_hn_policy',  'user_chen_zq',  'viewer', 'user_admin'),
('kb_hn_product', 'user_chen_zq',  'viewer', 'user_admin'),
-- 知识库管理员
('kb_hn_policy',  'user_feng_xh',  'admin',  'user_admin'),
('kb_hn_product', 'user_feng_xh',  'admin',  'user_admin'),
('kb_hn_network', 'user_feng_xh',  'admin',  'user_admin'),
('kb_hn_tech',    'user_feng_xh',  'admin',  'user_admin'),
('kb_hn_service', 'user_feng_xh',  'admin',  'user_admin');

-- 知识条目（示例）
INSERT IGNORE INTO kb_knowledge (id, kb_id, title, content, category, status, source, tags) VALUES
('kn_hn_001', 'kb_hn_policy', '湖南电信员工考勤管理办法', '第一章 总则... 员工应严格遵守作息时间，实行每日两次打卡制度...', '管理制度', 'published', 'manual', '考勤,制度'),
('kn_hn_002', 'kb_hn_policy', '中国电信湖南分公司采购管理办法', '为规范公司采购行为，提高采购效率，降低采购成本...', '管理制度', 'published', 'manual', '采购,制度'),
('kn_hn_003', 'kb_hn_product', '天翼云电脑产品介绍', '天翼云电脑是中国电信面向企业客户推出的桌面云服务...', '云产品', 'published', 'manual', '天翼云,云电脑'),
('kn_hn_004', 'kb_hn_product', '5G定制网资费方案（2025版）', '5G定制网是中国电信为行业客户提供的专属5G网络服务...', '5G业务', 'published', 'manual', '5G,定制网'),
('kn_hn_005', 'kb_hn_network', '光网络故障排查手册', '一、OLT设备故障... 二、ONU设备故障... 三、光缆线路故障...', '故障处理', 'published', 'manual', '光网络,故障'),
('kn_hn_006', 'kb_hn_tech', '湖南电信大模型平台使用指南', '本指南介绍湖南电信内部大模型平台的使用方法...', 'AI平台', 'published', 'manual', '大模型,AI'),
('kn_hn_007', 'kb_hn_service', '政企客户投诉处理流程', '一、投诉受理... 二、分类派单... 三、处理跟进... 四、回访闭环...', '服务规范', 'published', 'manual', '投诉,政企'),
('kn_hn_008', 'kb_hn_product', '物联网平台IOT连接管理', '中国电信物联网连接管理平台提供设备接入、通信管理...', '物联网', 'published', 'manual', '物联网,IOT');

-- ==================== 系统配置 ====================
INSERT IGNORE INTO sys_config (config_key, config_value, config_type, description, is_default, is_system) VALUES
-- brand：品牌信息
('system_name', 'AIS 智能知识服务平台', 'brand', '系统名称', 1, 1),
('system_slogan', '让知识触手可及', 'brand', '宣传语', 1, 1),
('copyright', '', 'brand', '版权信息', 1, 1),
('logo_url', '', 'brand', '系统 Logo URL', 1, 1),
('org_name', '{"value":"中国电信湖南分公司"}', 'brand', '组织名称', 1, 1),
-- general：通用参数
('general_settings', '{"defaultLanguage":"zh-CN","timezone":"Asia/Shanghai"}', 'general', '通用设置', 1, 1),
('general_chunk_size', '2000', 'general', '默认分片大小', 1, 1),
('general_chunk_overlap', '100', 'general', '默认分片重叠', 1, 1),
('general_search_top_k', '10', 'general', '默认搜索返回条数', 1, 1),
('general_retrieval_mode', 'hybrid', 'general', '默认检索模式', 1, 1),
('general_enable_rerank', 'false', 'general', '是否启用重排序', 1, 1),
('general_max_tokens', '2048', 'general', '默认最大生成Token数', 1, 1);

-- ==================== 应用示例 ====================
INSERT IGNORE INTO app (id, name, description, type, status, owner, tags) VALUES
('app_hn_policy_bot',  '制度问答助手',  '基于湖南电信规章制度库的智能问答应用', 'ChatBot', 'published', 'user_admin', '["制度","FAQ"]'),
('app_hn_product_bot', '产品知识助手',  '基于湖南电信产品方案库的智能问答应用', 'ChatBot', 'published', 'user_admin', '["产品","营销"]'),
('app_hn_network_bot', '运维知识助手',  '基于湖南电信网络运维库的智能问答应用', 'ChatBot', 'published', 'user_admin', '["网络","运维"]');

-- 应用配置
INSERT IGNORE INTO app_config (id, app_id, model, prompt, temperature, knowledge_ids, max_turns) VALUES
('ac_001', 'app_hn_policy_bot',  'qwen3-72b', '你是湖南电信的内部制度助手，请根据规章制度库回答员工问题。', 0.30, '["kb_hn_policy"]', 10),
('ac_002', 'app_hn_product_bot', 'qwen3-72b', '你是湖南电信的产品知识助手，请根据产品方案库回答客户经理问题。', 0.40, '["kb_hn_product"]', 10),
('ac_003', 'app_hn_network_bot', 'qwen3-72b', '你是湖南电信的运维知识助手，请根据运维手册帮助运维人员排查故障。', 0.30, '["kb_hn_network"]', 10);

-- 应用基础配置
INSERT IGNORE INTO app_basic_config (id, app_id, greeting, response_language) VALUES
('abc_001', 'app_hn_policy_bot',  '您好！我是湖南电信制度问答助手，请问有什么可以帮您？', 'zh-CN'),
('abc_002', 'app_hn_product_bot', '您好！我是湖南电信产品知识助手，请告诉我您想了解什么产品？', 'zh-CN'),
('abc_003', 'app_hn_network_bot', '您好！我是湖南电信运维知识助手，请描述您遇到的网络问题。', 'zh-CN');

-- 应用知识库绑定
INSERT IGNORE INTO app_kb_binding (id, app_id, kb_id, priority, enabled) VALUES
('akb_001', 'app_hn_policy_bot',  'kb_hn_policy',  1, 1),
('akb_002', 'app_hn_product_bot', 'kb_hn_product', 1, 1),
('akb_003', 'app_hn_network_bot', 'kb_hn_network', 1, 1);

-- ==================== 字典数据（湖南电信专属） ====================
INSERT IGNORE INTO sys_dictionary (dict_type, dict_key, dict_value) VALUES
('org_type', '省公司', '省公司'),
('org_type', '地市分公司', '地市分公司'),
('org_type', '区县经营部', '区县经营部'),
('org_type', '职能部门', '职能部门'),
('kb_category', '技术文档', '技术文档'),
('kb_category', '产品说明', '产品说明'),
('kb_category', '常见问题', '常见问题'),
('kb_category', '政策法规', '政策法规'),
('app_type', 'ChatBot', '聊天机器人'),
('app_type', 'Editor', '编辑器'),
('app_type', 'LiteAgent', '轻量Agent');

-- ==================== 完成 ====================
-- 初始化完成，组织架构概要：
--
-- 中国电信湖南分公司（L1 省公司，org_han_hq）
-- ├── 综合部（L2）
-- │   ├── 办公室（L3）
-- │   └── 后勤保障部（L3）
-- ├── 人力资源部（L2）
-- ├── 财务部（L2）
-- ├── 政企客户部（L2）
-- ├── 个人客户部（L2）
-- ├── 网络部（L2）
-- │   ├── 运行维护部（L3）
-- │   └── 网络资源部（L3）
-- ├── 技术部（L2）
-- │   ├── 研发中心（L3）
-- │   └── 创新业务部（L3）
-- ├── 数据智能部（L2）
-- ├── 云网运营部（L2）
-- ├── 安全部（L2）
-- ├── 长沙分公司（L2）
-- │   ├── 芙蓉区经营部（L3）
-- │   ├── 岳麓区经营部（L3）
-- │   └── 天心区经营部（L3）
-- ├── 株洲分公司（L2）
-- ├── 湘潭分公司（L2）
-- ├── 衡阳分公司（L2）
-- │   ├── 石鼓区经营部（L3）
-- │   └── 雁峰区经营部（L3）
-- ├── 邵阳分公司（L2）
-- ├── 岳阳分公司（L2）
-- │   ├── 岳阳楼区经营部（L3）
-- │   └── 君山区经营部（L3）
-- ├── 常德分公司（L2）
-- ├── 张家界分公司（L2）
-- ├── 益阳分公司（L2）
-- ├── 娄底分公司（L2）
-- ├── 郴州分公司（L2）
-- ├── 永州分公司（L2）
-- ├── 怀化分公司（L2）
-- └── 湘西分公司（L2）
--
-- 员工总数：42 人
--   - 省公司领导：2 人（总经理 张建国、党委书记 李明辉）
--   - 职能部门员工：23 人
--   - 地市分公司总经理：14 人
--   - 区县经营部员工：3 人
--
-- 默认账号密码：
--   超级管理员：admin / admin123
--   其余员工：   [用户名] / 123456
--     例如：zhangjianguo / 123456, liminghui / 123456, zhaodapeng / 123456
