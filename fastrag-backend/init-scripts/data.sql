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
INSERT INTO sys_permission (perm_key, name, type, `group`, category) VALUES
-- ===== 菜单权限 (category='menu') =====
('menu:home', '首页', 'menu', 'menu', 'menu'),
('menu:knowledge', '知识库', 'menu', 'menu', 'menu'),
('menu:knowledge:list', '知识库列表', 'menu', 'menu', 'menu'),
('menu:knowledge:categories', '知识库分类', 'menu', 'menu', 'menu'),
('menu:knowledge:analytics', '知识库分析', 'menu', 'menu', 'menu'),
('menu:knowledge:retrieval', '检索日志分析', 'menu', 'menu', 'menu'),
('menu:application', '应用中心', 'menu', 'menu', 'menu'),
('menu:application:center', '应用中心', 'menu', 'menu', 'menu'),
('menu:application:runtime', '应用运行', 'menu', 'menu', 'menu'),
('menu:application:my-tools', '我的工具', 'menu', 'menu', 'menu'),
('menu:application:mcp', 'MCP管理', 'menu', 'menu', 'menu'),
('menu:application:skill', '技能管理', 'menu', 'menu', 'menu'),
('menu:application:database', '数据库管理', 'menu', 'menu', 'menu'),
('menu:application:feedback', '反馈管理', 'menu', 'menu', 'menu'),
('menu:application:monitor', '模型监控', 'menu', 'menu', 'menu'),
('menu:admin', '管理中心', 'menu', 'menu', 'menu'),
('menu:admin:index', '管理中心概览', 'menu', 'menu', 'menu'),
('menu:admin:system', '系统管理', 'menu', 'menu', 'menu'),
('menu:admin:system:general', '通用设置', 'menu', 'menu', 'menu'),
('menu:admin:system:kb-config', '知识库配置', 'menu', 'menu', 'menu'),
('menu:admin:system:sensitive', '敏感词设置', 'menu', 'menu', 'menu'),
('menu:admin:system:dictionary', '字典管理', 'menu', 'menu', 'menu'),
('menu:admin:system:terminology', '术语管理', 'menu', 'menu', 'menu'),
('menu:admin:account', '账号权限', 'menu', 'menu', 'menu'),
('menu:admin:account:roles', '角色管理', 'menu', 'menu', 'menu'),
('menu:admin:account:org', '组织管理', 'menu', 'menu', 'menu'),
('menu:admin:account:personnel', '人员管理', 'menu', 'menu', 'menu'),
('menu:admin:account:permissions', '权限管理', 'menu', 'menu', 'menu'),
('menu:admin:audit', '安全审计', 'menu', 'menu', 'menu'),
('menu:admin:audit:log', '系统日志', 'menu', 'menu', 'menu'),
('menu:admin:platform', '开放平台', 'menu', 'menu', 'menu'),
('menu:admin:platform:model', '模型管理', 'menu', 'menu', 'menu'),
('menu:admin:platform:apikey', '开放密钥', 'menu', 'menu', 'menu'),

-- ===== 页面操作权限 (category='page_action') =====
('home:workspace:manage', '管理工作台', 'action', 'menu', 'page_action'),
('kb:create', '创建知识库', 'action', 'kb', 'page_action'),
('kb:edit', '编辑知识库', 'action', 'kb', 'page_action'),
('kb:delete', '删除知识库', 'action', 'kb', 'page_action'),
('kb:view', '查看知识库', 'action', 'kb', 'page_action'),
('kb:search', '搜索知识库', 'action', 'kb', 'page_action'),
('kb:upload', '上传文件', 'action', 'kb', 'page_action'),
('kb:manage_chunks', '管理分片', 'action', 'kb', 'page_action'),
('kb:manage_graph', '管理知识图谱', 'action', 'kb', 'page_action'),
('kb:manage_eval', '管理评估', 'action', 'kb', 'page_action'),
('kb:manage_strategy', '管理解析策略', 'action', 'kb', 'page_action'),
('kb:acl_manage', '知识库权限管理', 'action', 'kb', 'page_action'),
('kb:export', '导出知识库', 'action', 'kb', 'page_action'),
('kb:folder:create', '新建文件夹', 'action', 'kb', 'page_action'),
('kb:file:rename', '重命名文件', 'action', 'kb', 'page_action'),
('kb:file:move', '移动文件', 'action', 'kb', 'page_action'),
('kb:file:restore', '恢复文件', 'action', 'kb', 'page_action'),
('kb:version:create', '创建版本', 'action', 'kb', 'page_action'),
('kb:version:publish', '发布版本', 'action', 'kb', 'page_action'),
('kb:version:revoke', '撤回版本', 'action', 'kb', 'page_action'),
('kb:category:create', '创建分类', 'action', 'kb', 'page_action'),
('kb:category:edit', '编辑分类', 'action', 'kb', 'page_action'),
('kb:category:delete', '删除分类', 'action', 'kb', 'page_action'),
('qa:manage', '问答管理', 'action', 'kb', 'page_action'),
('testcase:manage', '测试用例管理', 'action', 'kb', 'page_action'),
('app:create', '创建应用', 'action', 'app', 'page_action'),
('app:edit', '编辑应用', 'action', 'app', 'page_action'),
('app:delete', '删除应用', 'action', 'app', 'page_action'),
('app:use', '使用应用', 'action', 'app', 'page_action'),
('app:publish', '发布应用', 'action', 'app', 'page_action'),
('app:config:basic', '编辑基本参数', 'action', 'app', 'page_action'),
('app:config:prompt', '编辑提示词', 'action', 'app', 'page_action'),
('app:config:policy', '编辑安全策略', 'action', 'app', 'page_action'),
('app:export', '导出应用配置', 'action', 'app', 'page_action'),
('app:import', '导入应用配置', 'action', 'app', 'page_action'),
('app:knowledge-update', '管理知识更新', 'action', 'app', 'page_action'),
('app:member', '管理应用成员', 'action', 'app', 'page_action'),
('workflow:create', '创建业务流', 'action', 'workflow', 'page_action'),
('workflow:edit', '编辑业务流', 'action', 'workflow', 'page_action'),
('workflow:delete', '删除业务流', 'action', 'workflow', 'page_action'),
('workflow:publish', '发布业务流', 'action', 'workflow', 'page_action'),
('workflow:run', '运行业务流', 'action', 'workflow', 'page_action'),
('tool:create', '创建工具', 'action', 'app', 'page_action'),
('tool:edit', '编辑工具', 'action', 'app', 'page_action'),
('tool:delete', '删除工具', 'action', 'app', 'page_action'),
('tool:test', '测试工具', 'action', 'app', 'page_action'),
('mcp:create', '创建MCP服务', 'action', 'app', 'page_action'),
('mcp:edit', '编辑MCP服务', 'action', 'app', 'page_action'),
('mcp:delete', '删除MCP服务', 'action', 'app', 'page_action'),
('mcp:test', '测试MCP工具', 'action', 'app', 'page_action'),
('skill:create', '创建技能', 'action', 'app', 'page_action'),
('skill:edit', '编辑技能', 'action', 'app', 'page_action'),
('skill:delete', '删除技能', 'action', 'app', 'page_action'),
('skill:export', '导出技能', 'action', 'app', 'page_action'),
('skill:share', '配置技能分享', 'action', 'app', 'page_action'),
('admin:access', '访问管理后台', 'action', 'admin', 'page_action'),
('admin:user:create', '添加人员', 'action', 'admin', 'page_action'),
('admin:user:edit', '编辑人员', 'action', 'admin', 'page_action'),
('admin:user:role-config', '人员角色配置', 'action', 'admin', 'page_action'),
('admin:user:disable', '启用/禁用人员', 'action', 'admin', 'page_action'),
('admin:role:create', '新增角色', 'action', 'admin', 'page_action'),
('admin:role:edit', '编辑角色', 'action', 'admin', 'page_action'),
('admin:role:delete', '删除角色', 'action', 'admin', 'page_action'),
('admin:role:set-default', '设为默认角色', 'action', 'admin', 'page_action'),
('admin:org:create', '新增组织', 'action', 'admin', 'page_action'),
('admin:org:edit', '编辑组织', 'action', 'admin', 'page_action'),
('admin:org:delete', '删除组织', 'action', 'admin', 'page_action'),
('admin:system', '系统配置', 'action', 'admin', 'page_action'),
('admin:audit', '审计管理', 'action', 'admin', 'page_action'),
('model:create', '新增模型', 'action', 'admin', 'page_action'),
('model:edit', '编辑模型', 'action', 'admin', 'page_action'),
('model:delete', '删除模型', 'action', 'admin', 'page_action'),
('model:test', '测试模型', 'action', 'admin', 'page_action'),
('model:import', '导入模型', 'action', 'admin', 'page_action'),
('model:export', '导出模型', 'action', 'admin', 'page_action'),
('model:toggle', '上架/下架模型', 'action', 'admin', 'page_action'),
('dictionary:create', '新建字典条目', 'action', 'admin', 'page_action'),
('dictionary:edit', '编辑字典条目', 'action', 'admin', 'page_action'),
('dictionary:delete', '删除字典条目', 'action', 'admin', 'page_action'),
('sensitive-word:create', '添加敏感词', 'action', 'admin', 'page_action'),
('sensitive-word:edit', '编辑敏感词', 'action', 'admin', 'page_action'),
('sensitive-word:delete', '删除敏感词', 'action', 'admin', 'page_action'),
('sensitive-word:import', '批量导入敏感词', 'action', 'admin', 'page_action'),
('terminology:library:create', '新建术语库', 'action', 'admin', 'page_action'),
('terminology:library:delete', '删除术语库', 'action', 'admin', 'page_action'),
('terminology:term:create', '新建术语', 'action', 'admin', 'page_action'),
('terminology:term:edit', '编辑术语', 'action', 'admin', 'page_action'),
('terminology:term:delete', '删除术语', 'action', 'admin', 'page_action'),
('terminology:import', '批量导入术语', 'action', 'admin', 'page_action'),
('api-key:create', '创建密钥', 'action', 'admin', 'page_action'),
('api-key:edit', '编辑密钥', 'action', 'admin', 'page_action'),
('api-key:delete', '删除密钥', 'action', 'admin', 'page_action'),
('config:save', '保存系统配置', 'action', 'admin', 'page_action'),
('config:import', '导入系统配置', 'action', 'admin', 'page_action'),
('config:export', '导出系统配置', 'action', 'admin', 'page_action'),
('review:approve', '审核通过', 'action', 'review', 'page_action'),
('review:reject', '审核驳回', 'action', 'review', 'page_action'),
('review:flow-manage', '审核流程管理', 'action', 'review', 'page_action'),
('review:detail', '查看审核详情', 'action', 'review', 'page_action'),
('review:remind', '提醒审核', 'action', 'review', 'page_action'),
('review:export', '导出审核记录', 'action', 'review', 'page_action'),
('feedback:create', '新增反馈', 'action', 'admin', 'page_action'),
('feedback:reply', '回复反馈', 'action', 'admin', 'page_action'),
('feedback:delete', '删除反馈', 'action', 'admin', 'page_action'),
('data-mining:create', '新建数据挖掘任务', 'action', 'admin', 'page_action'),
('data-mining:run', '运行数据挖掘任务', 'action', 'admin', 'page_action'),
('data-mining:delete', '删除数据挖掘任务', 'action', 'admin', 'page_action'),

-- ===== API接口权限 (category='api') =====
('api:auth:login', '用户登录', 'action', 'admin', 'api'),
('api:auth:userinfo', '获取用户信息', 'action', 'admin', 'api'),
('api:role:list', '角色列表', 'action', 'admin', 'api'),
('api:role:detail', '角色详情', 'action', 'admin', 'api'),
('api:role:create', '创建角色', 'action', 'admin', 'api'),
('api:role:update', '更新角色', 'action', 'admin', 'api'),
('api:role:delete', '删除角色', 'action', 'admin', 'api'),
('api:role:set-default', '设置默认角色', 'action', 'admin', 'api'),
('api:permission:list', '权限列表', 'action', 'admin', 'api'),
('api:permission:tree', '权限树', 'action', 'admin', 'api'),
('api:permission:create', '创建权限', 'action', 'admin', 'api'),
('api:permission:update', '更新权限', 'action', 'admin', 'api'),
('api:permission:delete', '删除权限', 'action', 'admin', 'api'),
('api:personnel:list', '人员列表', 'action', 'admin', 'api'),
('api:personnel:create', '创建人员', 'action', 'admin', 'api'),
('api:personnel:update', '更新人员', 'action', 'admin', 'api'),
('api:personnel:assign-roles', '分配角色', 'action', 'admin', 'api'),
('api:personnel:status', '修改人员状态', 'action', 'admin', 'api'),
('api:org:tree', '组织树', 'action', 'admin', 'api'),
('api:org:flat', '组织扁平列表', 'action', 'admin', 'api'),
('api:org:departments', '部门列表', 'action', 'admin', 'api'),
('api:org:members', '部门成员', 'action', 'admin', 'api'),
('api:org:create', '创建组织', 'action', 'admin', 'api'),
('api:org:update', '更新组织', 'action', 'admin', 'api'),
('api:org:delete', '删除组织', 'action', 'admin', 'api'),
('api:kb:acl', '知识库ACL管理', 'action', 'kb', 'api'),
('api:user:acl', '用户ACL查询', 'action', 'kb', 'api'),
('api:kb:list', '知识库列表', 'action', 'kb', 'api'),
('api:kb:detail', '知识库详情', 'action', 'kb', 'api'),
('api:kb:create', '创建知识库', 'action', 'kb', 'api'),
('api:kb:update', '更新知识库', 'action', 'kb', 'api'),
('api:kb:delete', '删除知识库', 'action', 'kb', 'api'),
('api:kb:category:list', '分类列表', 'action', 'kb', 'api'),
('api:kb:category:create', '创建分类', 'action', 'kb', 'api'),
('api:kb:category:update', '更新分类', 'action', 'kb', 'api'),
('api:kb:category:delete', '删除分类', 'action', 'kb', 'api'),
('api:kb:tag:list', '标签列表', 'action', 'kb', 'api'),
('api:kb:file:list', '文件列表', 'action', 'kb', 'api'),
('api:kb:file:upload', '上传文件', 'action', 'kb', 'api'),
('api:kb:file:update', '更新文件', 'action', 'kb', 'api'),
('api:kb:file:delete', '删除文件', 'action', 'kb', 'api'),
('api:kb:file:process', '处理文件', 'action', 'kb', 'api'),
('api:kb:file:download', '下载文件', 'action', 'kb', 'api'),
('api:kb:file:preview', '预览文件', 'action', 'kb', 'api'),
('api:kb:file:restore', '恢复文件', 'action', 'kb', 'api'),
('api:kb:file:copy', '复制文件', 'action', 'kb', 'api'),
('api:kb:file:move', '移动文件', 'action', 'kb', 'api'),
('api:kb:folder:list', '文件夹列表', 'action', 'kb', 'api'),
('api:kb:folder:create', '创建文件夹', 'action', 'kb', 'api'),
('api:kb:folder:rename', '重命名文件夹', 'action', 'kb', 'api'),
('api:kb:folder:delete', '删除文件夹', 'action', 'kb', 'api'),
('api:kb:chunk:list', '分片列表', 'action', 'kb', 'api'),
('api:kb:chunk:create', '创建分片', 'action', 'kb', 'api'),
('api:kb:chunk:update', '更新分片', 'action', 'kb', 'api'),
('api:kb:chunk:delete', '删除分片', 'action', 'kb', 'api'),
('api:kb:qa:list', '问答对列表', 'action', 'kb', 'api'),
('api:kb:qa:create', '创建问答对', 'action', 'kb', 'api'),
('api:kb:qa:update', '更新问答对', 'action', 'kb', 'api'),
('api:kb:qa:delete', '删除问答对', 'action', 'kb', 'api'),
('api:kb:qa:confirm', '确认问答对', 'action', 'kb', 'api'),
('api:kb:graph:view', '知识图谱查看', 'action', 'kb', 'api'),
('api:kb:graph:build', '构建图谱', 'action', 'kb', 'api'),
('api:kb:graph:settings', '图谱设置', 'action', 'kb', 'api'),
('api:kb:graph:search', '图谱搜索', 'action', 'kb', 'api'),
('api:kb:strategy:list', '解析策略列表', 'action', 'kb', 'api'),
('api:kb:strategy:create', '创建解析策略', 'action', 'kb', 'api'),
('api:kb:strategy:update', '更新解析策略', 'action', 'kb', 'api'),
('api:kb:strategy:delete', '删除解析策略', 'action', 'kb', 'api'),
('api:kb:strategy:resolve', '解析策略解析', 'action', 'kb', 'api'),
('api:kb:eval:list', '评估列表', 'action', 'kb', 'api'),
('api:kb:eval:run', '运行评估', 'action', 'kb', 'api'),
('api:kb:eval:delete', '删除评估', 'action', 'kb', 'api'),
('api:kb:benchmark:list', '基准列表', 'action', 'kb', 'api'),
('api:kb:benchmark:create', '创建基准', 'action', 'kb', 'api'),
('api:kb:benchmark:delete', '删除基准', 'action', 'kb', 'api'),
('api:kb:benchmark:generate', '生成题目', 'action', 'kb', 'api'),
('api:kb:version:list', '版本列表', 'action', 'kb', 'api'),
('api:kb:version:create', '创建版本', 'action', 'kb', 'api'),
('api:kb:version:transition', '版本流转', 'action', 'kb', 'api'),
('api:kb:publish:history', '发布历史', 'action', 'kb', 'api'),
('api:kb:publish:publish', '发布知识', 'action', 'kb', 'api'),
('api:kb:publish:revoke', '撤回发布', 'action', 'kb', 'api'),
('api:kb:publish:plan:list', '发布计划列表', 'action', 'kb', 'api'),
('api:kb:publish:plan:create', '创建发布计划', 'action', 'kb', 'api'),
('api:kb:log:list', '知识库日志', 'action', 'kb', 'api'),
('api:app:list', '应用列表', 'action', 'app', 'api'),
('api:app:detail', '应用详情', 'action', 'app', 'api'),
('api:app:create', '创建应用', 'action', 'app', 'api'),
('api:app:update', '更新应用', 'action', 'app', 'api'),
('api:app:delete', '删除应用', 'action', 'app', 'api'),
('api:app:run', '运行应用', 'action', 'app', 'api'),
('api:app:config:basic', '基础配置', 'action', 'app', 'api'),
('api:app:config:dialog', '对话配置', 'action', 'app', 'api'),
('api:app:config:prompt', '提示词配置', 'action', 'app', 'api'),
('api:app:config:policy', '安全策略', 'action', 'app', 'api'),
('api:app:config:export', '导出配置', 'action', 'app', 'api'),
('api:app:config:import', '导入配置', 'action', 'app', 'api'),
('api:app:config:advanced', '高级配置', 'action', 'app', 'api'),
('api:app:trigger:list', '触发器列表', 'action', 'app', 'api'),
('api:app:trigger:create', '创建触发器', 'action', 'app', 'api'),
('api:app:trigger:update', '更新触发器', 'action', 'app', 'api'),
('api:app:trigger:delete', '删除触发器', 'action', 'app', 'api'),
('api:app:trigger:test', '测试触发器', 'action', 'app', 'api'),
('api:app:kb:bind', '知识库绑定', 'action', 'app', 'api'),
('api:app:skill:bind', '技能绑定', 'action', 'app', 'api'),
('api:app:tool:bind', '工具绑定', 'action', 'app', 'api'),
('api:app:mcp:bind', 'MCP绑定', 'action', 'app', 'api'),
('api:app:database:bind', '数据库绑定', 'action', 'app', 'api'),
('api:app:dialog-test:list', '对话测试列表', 'action', 'app', 'api'),
('api:app:dialog-test:create', '创建对话测试', 'action', 'app', 'api'),
('api:app:dialog-test:update', '更新对话测试', 'action', 'app', 'api'),
('api:app:dialog-test:delete', '删除对话测试', 'action', 'app', 'api'),
('api:app:optimization:list', '优化列表', 'action', 'app', 'api'),
('api:app:optimization:create', '创建优化', 'action', 'app', 'api'),
('api:app:optimization:update', '更新优化', 'action', 'app', 'api'),
('api:app:optimization:delete', '删除优化', 'action', 'app', 'api'),
('api:app:optimization:apply', '应用优化', 'action', 'app', 'api'),
('api:app:conversation:list', '对话记录列表', 'action', 'app', 'api'),
('api:app:conversation:detail', '对话记录详情', 'action', 'app', 'api'),
('api:app:conversation:delete', '删除对话记录', 'action', 'app', 'api'),
('api:app:chat:session', '会话管理', 'action', 'app', 'api'),
('api:app:chat:message', '消息管理', 'action', 'app', 'api'),
('api:app:chat:feedback', '消息反馈', 'action', 'app', 'api'),
('api:app:publish', '应用发布', 'action', 'app', 'api'),
('api:app:monitor', '应用监控', 'action', 'app', 'api'),
('api:workflow:list', '工作流列表', 'action', 'workflow', 'api'),
('api:workflow:detail', '工作流详情', 'action', 'workflow', 'api'),
('api:workflow:create', '创建工作流', 'action', 'workflow', 'api'),
('api:workflow:update', '更新工作流', 'action', 'workflow', 'api'),
('api:workflow:delete', '删除工作流', 'action', 'workflow', 'api'),
('api:workflow:publish', '发布工作流', 'action', 'workflow', 'api'),
('api:workflow:execute', '执行工作流', 'action', 'workflow', 'api'),
('api:workflow:node:manage', '节点管理', 'action', 'workflow', 'api'),
('api:workflow:testcase:manage', '测试用例管理', 'action', 'workflow', 'api'),
('api:tool:list', '工具列表', 'action', 'app', 'api'),
('api:tool:create', '创建工具', 'action', 'app', 'api'),
('api:tool:update', '更新工具', 'action', 'app', 'api'),
('api:tool:delete', '删除工具', 'action', 'app', 'api'),
('api:mcp:list', 'MCP服务列表', 'action', 'app', 'api'),
('api:mcp:create', '创建MCP', 'action', 'app', 'api'),
('api:mcp:update', '更新MCP', 'action', 'app', 'api'),
('api:mcp:delete', '删除MCP', 'action', 'app', 'api'),
('api:mcp:toggle', '切换MCP', 'action', 'app', 'api'),
('api:mcp:test', '测试MCP工具', 'action', 'app', 'api'),
('api:skill:list', '技能列表', 'action', 'app', 'api'),
('api:skill:detail', '技能详情', 'action', 'app', 'api'),
('api:skill:create', '创建技能', 'action', 'app', 'api'),
('api:skill:update', '更新技能', 'action', 'app', 'api'),
('api:skill:delete', '删除技能', 'action', 'app', 'api'),
('api:skill:file:manage', '技能文件管理', 'action', 'app', 'api'),
('api:skill:export', '导出技能', 'action', 'app', 'api'),
('api:database:list', '数据库列表', 'action', 'app', 'api'),
('api:database:detail', '数据库详情', 'action', 'app', 'api'),
('api:database:create', '创建数据库', 'action', 'app', 'api'),
('api:database:update', '更新数据库', 'action', 'app', 'api'),
('api:database:delete', '删除数据库', 'action', 'app', 'api'),
('api:model:list', '模型列表', 'action', 'admin', 'api'),
('api:model:detail', '模型详情', 'action', 'admin', 'api'),
('api:model:create', '创建模型', 'action', 'admin', 'api'),
('api:model:update', '更新模型', 'action', 'admin', 'api'),
('api:model:delete', '删除模型', 'action', 'admin', 'api'),
('api:model:toggle', '切换模型', 'action', 'admin', 'api'),
('api:model:test:chat', '测试对话', 'action', 'admin', 'api'),
('api:model:test:embedding', '测试Embedding', 'action', 'admin', 'api'),
('api:model:test:rerank', '测试Rerank', 'action', 'admin', 'api'),
('api:model:import', '导入模型', 'action', 'admin', 'api'),
('api:model:export', '导出模型', 'action', 'admin', 'api'),
('api:dictionary:list', '字典列表', 'action', 'admin', 'api'),
('api:dictionary:create', '创建字典', 'action', 'admin', 'api'),
('api:dictionary:update', '更新字典', 'action', 'admin', 'api'),
('api:dictionary:delete', '删除字典', 'action', 'admin', 'api'),
('api:dictionary:types', '字典类型', 'action', 'admin', 'api'),
('api:sensitive-word:list', '敏感词列表', 'action', 'admin', 'api'),
('api:sensitive-word:create', '创建敏感词', 'action', 'admin', 'api'),
('api:sensitive-word:update', '更新敏感词', 'action', 'admin', 'api'),
('api:sensitive-word:delete', '删除敏感词', 'action', 'admin', 'api'),
('api:terminology:library:list', '术语库列表', 'action', 'admin', 'api'),
('api:terminology:library:create', '创建术语库', 'action', 'admin', 'api'),
('api:terminology:library:delete', '删除术语库', 'action', 'admin', 'api'),
('api:terminology:term:list', '术语列表', 'action', 'admin', 'api'),
('api:terminology:term:create', '创建术语', 'action', 'admin', 'api'),
('api:terminology:term:delete', '删除术语', 'action', 'admin', 'api'),
('api:config:list', '系统配置列表', 'action', 'admin', 'api'),
('api:config:save', '保存配置', 'action', 'admin', 'api'),
('api:config:history', '配置历史', 'action', 'admin', 'api'),
('api:config:import', '导入配置', 'action', 'admin', 'api'),
('api:config:export', '导出配置', 'action', 'admin', 'api'),
('api:security-policy:list', '安全策略列表', 'action', 'admin', 'api'),
('api:security-policy:create', '创建安全策略', 'action', 'admin', 'api'),
('api:security-policy:update', '更新安全策略', 'action', 'admin', 'api'),
('api:security-policy:delete', '删除安全策略', 'action', 'admin', 'api'),
('api:publish-strategy:list', '发布策略列表', 'action', 'admin', 'api'),
('api:publish-strategy:create', '创建发布策略', 'action', 'admin', 'api'),
('api:publish-strategy:update', '更新发布策略', 'action', 'admin', 'api'),
('api:publish-strategy:delete', '删除发布策略', 'action', 'admin', 'api'),
('api:notification:list', '通知列表', 'action', 'admin', 'api'),
('api:notification:send', '发送通知', 'action', 'admin', 'api'),
('api:notification:read', '标记已读', 'action', 'admin', 'api'),
('api:notification:delete', '删除通知', 'action', 'admin', 'api'),
('api:home:data', '首页数据', 'action', 'admin', 'api'),
('api:analytics:kb', '知识库分析', 'action', 'admin', 'api'),
('api:feedback:list', '反馈列表', 'action', 'admin', 'api'),
('api:feedback:create', '创建反馈', 'action', 'admin', 'api'),
('api:feedback:update', '更新反馈', 'action', 'admin', 'api'),
('api:feedback:delete', '删除反馈', 'action', 'admin', 'api'),
('api:feedback:reply', '回复反馈', 'action', 'admin', 'api'),
('api:retrieval:log:list', '检索日志', 'action', 'admin', 'api'),
('api:retrieval:log:analysis', '检索日志分析', 'action', 'admin', 'api'),
('api:audit:log:list', '审计日志', 'action', 'admin', 'api'),
('api:data-mining:list', '数据挖掘任务列表', 'action', 'admin', 'api'),
('api:data-mining:create', '创建数据挖掘任务', 'action', 'admin', 'api'),
('api:data-mining:delete', '删除数据挖掘任务', 'action', 'admin', 'api'),
('api:data-mining:run', '运行数据挖掘任务', 'action', 'admin', 'api'),
('api:agent:list', 'Agent列表', 'action', 'admin', 'api'),
('api:agent:create', '创建Agent', 'action', 'admin', 'api'),
('api:agent:update', '更新Agent', 'action', 'admin', 'api'),
('api:agent:delete', '删除Agent', 'action', 'admin', 'api'),
('api:agent:run', '运行Agent', 'action', 'admin', 'api');
-- Super admin has all permissions
INSERT INTO sys_role_permission (role_id, permission_key)
SELECT 'role_super_admin', perm_key FROM sys_permission;

-- KB admin permissions
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
('role_kb_admin', 'menu:home'), ('role_kb_admin', 'menu:knowledge'),
('role_kb_admin', 'menu:knowledge:list'), ('role_kb_admin', 'menu:admin'),
('role_kb_admin', 'menu:admin:index'),
('role_kb_admin', 'menu:admin:system'), ('role_kb_admin', 'menu:admin:system:kb-config'),
('role_kb_admin', 'menu:admin:system:terminology'),
('role_kb_admin', 'menu:admin:account'),
('role_kb_admin', 'menu:admin:account:org'), ('role_kb_admin', 'menu:admin:account:personnel'),
('role_kb_admin', 'menu:admin:account:permissions'),
('role_kb_admin', 'menu:operation'),
('role_kb_admin', 'menu:knowledge-review'),
('role_kb_admin', 'kb:create'), ('role_kb_admin', 'kb:edit'), ('role_kb_admin', 'kb:delete'),
('role_kb_admin', 'kb:upload'), ('role_kb_admin', 'kb:manage_chunks'), ('role_kb_admin', 'kb:manage_graph'),
('role_kb_admin', 'kb:manage_eval'), ('role_kb_admin', 'kb:manage_strategy'), ('role_kb_admin', 'kb:view'),
('role_kb_admin', 'kb:search'), ('role_kb_admin', 'kb:acl_manage'),
('role_kb_admin', 'admin:access'), ('role_kb_admin', 'admin:org'), ('role_kb_admin', 'admin:user'),
('role_kb_admin', 'admin:user:create'), ('role_kb_admin', 'admin:user:edit'), ('role_kb_admin', 'admin:user:disable');

-- KB user permissions
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
('role_kb_user', 'menu:home'), ('role_kb_user', 'menu:knowledge'), ('role_kb_user', 'menu:knowledge:list'),
('role_kb_user', 'menu:application'), ('role_kb_user', 'menu:application:center'),
('role_kb_user', 'kb:view'), ('role_kb_user', 'kb:search'), ('role_kb_user', 'kb:upload'),
('role_kb_user', 'kb:manage_chunks'), ('role_kb_user', 'kb:manage_graph'),
('role_kb_user', 'kb:manage_eval'), ('role_kb_user', 'kb:manage_strategy'),
('role_kb_user', 'kb:create'), ('role_kb_user', 'kb:edit'),
('role_kb_user', 'app:use'), ('role_kb_user', 'qa:manage'), ('role_kb_user', 'testcase:manage');

-- Readonly permissions
INSERT INTO sys_role_permission (role_id, permission_key) VALUES
('role_readonly', 'menu:home'), ('role_readonly', 'menu:knowledge'), ('role_readonly', 'menu:knowledge:list'),
('role_readonly', 'menu:application'), ('role_readonly', 'menu:application:center'),
('role_readonly', 'kb:view'), ('role_readonly', 'kb:search'),
('role_readonly', 'app:use');

-- Default admin user (password: admin123)
INSERT INTO sys_user (id, username, real_name, email, password_hash, role_id, status) VALUES
('user_admin', 'admin', '超级管理员', 'admin@fastrag.com', '$2b$10$kwtu6144DqB4Q4IqDbbsROvyxm.gYLLizL0Qbvm0otat.MBfaoJiG', 'role_super_admin', 'enabled');

-- User-Role bindings (sys_user_role)
INSERT INTO sys_user_role (user_id, role_id) VALUES
('user_admin', 'role_super_admin');

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


-- ==================== M6 数据挖掘 ====================
INSERT INTO data_mining_task (id, name, kb_id, rule_type, rule_config, status, result_summary, creator) VALUES
('dm_001', '高频退款问题挖掘', 'kb_sample', 'frequency', '{"threshold": 10, "days": 30}', 'enabled', '{"matchedCount": 28, "topKeywords": ["退款","退货","物流"], "runStatus": "success"}', 'admin');

-- ==================== M14 系统设置管理 ====================
INSERT INTO sys_config (config_key, config_value, config_type, description, is_default, is_system) VALUES
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

-- ==================== M20 知识库分类种子数据 ====================
INSERT INTO kb_category (id, name, description, color, icon, sort) VALUES
('cat_tech', '技术文档', '技术类知识库', '#1890ff', 'Document', 1),
('cat_product', '产品手册', '产品相关文档', '#52c41a', 'Box', 2),
('cat_faq', '常见问题', 'FAQ知识库', '#faad14', 'QuestionFilled', 3),
('cat_default', '默认分类', '未分类的知识库', '#909399', 'Folder', 0);

-- ==================== M20 通知种子数据 ====================
INSERT INTO sys_notification (id, title, content, notify_type, source_type, source_id, target_user, status, created_at) VALUES
('ntf_001', '知识库文件更新', '知识库「示例知识库」新增文件：产品需求文档v2.0.docx', 'knowledge_update', 'kb', 'kb_sample', NULL, 'read', '2026-06-01 09:15:00'),
('ntf_002', '审核任务待处理', '您有 3 条知识审核任务待处理，请及时审核', 'review_pending', 'review', 'task_batch_001', 'user_admin', 'read', '2026-06-02 10:30:00'),
('ntf_003', '发布计划执行成功', '发布计划「每日自动发布」执行完成，成功发布 12 条知识', 'publish_success', 'publish', 'plan_daily', NULL, 'read', '2026-06-03 08:00:00'),
('ntf_004', '模型调用异常告警', '模型 Qwen3-72B 过去1小时调用失败率达到 5.2%，请关注', 'model_alert', 'model', 'qwen3-72b', NULL, 'read', '2026-06-04 14:20:00'),
('ntf_005', '知识库容量预警', '知识库「示例知识库」存储空间已使用 85%，建议清理过期文件', 'storage_warning', 'kb', 'kb_sample', NULL, 'unread', '2026-06-05 11:00:00');

-- ==================== M20 安全策略种子数据 ====================
INSERT INTO sys_security_policy (id, name, policy_type, pattern, action, priority, enabled, description) VALUES
('sp_001', '内网IP白名单', 'ip_whitelist', '192.168.0.0/16,10.0.0.0/8', 'allow', 1, 1, '允许内网IP段访问系统'),
('sp_002', '敏感词过滤', 'keyword_filter', 'password,secret,密钥,token', 'block', 2, 1, '过滤包含敏感关键词的输入'),
('sp_003', '高频访问限制', 'rate_limit', '100/60', 'throttle', 3, 1, '限制单用户每分钟最多100次请求'),
('sp_004', '文件类型限制', 'file_type', 'exe,bat,sh,cmd,ps1', 'block', 4, 1, '禁止上传可执行文件'),
('sp_005', 'SQL注入防护', 'sql_injection', '(SELECT|DROP|DELETE|UPDATE|INSERT).*FROM', 'block', 0, 1, '拦截常见SQL注入模式');

-- ==================== M20 发布策略种子数据 ====================
INSERT INTO sys_publish_strategy (id, name, strategy_type, config, priority, enabled, description) VALUES
('ps_001', '自动审核发布', 'auto_publish', '{"minApproval":2,"workHours":"08:00-20:00"}', 1, 1, '达到最低审批数后在工作时间内自动发布'),
('ps_002', '定时批量发布', 'scheduled_publish', '{"cron":"0 9 * * 1-5"}', 2, 1, '每个工作日9点批量发布审核通过的知识'),
('ps_003', '敏感内容复查', 'sensitive_review', '{"checkFields":["title","content"]}', 0, 1, '包含敏感词的知识需要额外审核'),
('ps_004', '版本回滚策略', 'rollback', '{"keepVersions":10,"maxAgeDays":30}', 3, 0, '保留最近10个版本，超过30天的旧版本可清理');

-- ==================== M20 对话测试案例种子数据 ====================
INSERT INTO app_dialog_test (id, app_id, name, query, expected_answer, tags, created_by, created_at) VALUES
('dt_001', 'app_001', '企业请假制度查询', '员工请事假需要提前几天申请？', '根据公司考勤管理制度，员工请事假需提前3个工作日向直属主管提交书面申请，经部门负责人审批同意后方可休假。', '"制度查询"', 'admin', '2026-06-28 10:00:00'),
('dt_002', 'app_001', '报销流程咨询', '差旅费报销需要提供哪些材料？', '差旅费报销需提供以下材料：1.差旅审批单（需主管签字）；2.交通票据原件；3.住宿发票（需为正规增值税发票）；4.出差补助申请表。', '"流程查询"', 'admin', '2026-06-28 10:05:00'),
('dt_003', 'app_001', 'IT系统故障报修', 'VPN连接不上怎么办？', 'VPN连接问题可按以下步骤排查：1.检查网络连接是否正常；2.确认VPN客户端版本是否为最新版本；3.尝试切换VPN服务器节点；4.如仍无法连接，请联系IT服务热线400-888-0012。', '"故障排查"', 'admin', '2026-06-28 10:10:00'),
('dt_004', 'app_001', '绩效考核标准查询', '部门绩效考核的评分等级有哪些？', '部门绩效考核评分共分为5个等级：S级（优秀，90分以上）占10%，A级（良好，80-89分）占30%，B级（合格，70-79分）占50%，C级（待改进，60-69分）占8%，D级（不合格，60分以下）占2%。', '"考核查询"', 'admin', '2026-06-28 10:15:00'),
('dt_005', 'app_001', '新员工入职指引', '新员工入职第一天需要携带什么材料？', '新员工入职第一天请携带：1.身份证原件及复印件2份；2.学历学位证书原件及复印件1份；3.原单位离职证明原件；4.一寸免冠照片2张；5.银行卡复印件1份。', '"入职指引"', 'admin', '2026-06-28 10:20:00');
