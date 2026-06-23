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
