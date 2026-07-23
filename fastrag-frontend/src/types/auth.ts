// ===========================================================================
// 权限体系类型定义
//
// 角色枚举 + 权限常量 + 知识库 ACL + 角色→权限映射
// ===========================================================================

/** 系统角色枚举 */
export const SYSTEM_ROLES = {
  SUPER_ADMIN: 'super_admin',
  KB_ADMIN: 'kb_admin',
  KB_USER: 'kb_user',
  READONLY: 'readonly',
} as const

export type SystemRole = typeof SYSTEM_ROLES[keyof typeof SYSTEM_ROLES]

/** 角色元数据（用于角色管理页展示） */
export interface RoleMeta {
  id: string
  key: SystemRole
  name: string
  description: string
  permissions: string[]
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

/** 权限常量（资源:操作） */
export const PERMISSIONS = {
  // ===== 菜单权限 =====
  MENU_HOME: 'menu:home',
  MENU_KNOWLEDGE: 'menu:knowledge',
  MENU_KNOWLEDGE_LIST: 'menu:knowledge:list',
  MENU_KNOWLEDGE_CATEGORIES: 'menu:knowledge:categories',
  MENU_APPLICATION: 'menu:application',
  MENU_APPLICATION_CENTER: 'menu:application:center',
  MENU_APPLICATION_RUNTIME: 'menu:application:runtime',
  MENU_APPLICATION_MY_TOOLS: 'menu:application:my-tools',
  MENU_APPLICATION_MCP: 'menu:application:mcp',
  MENU_APPLICATION_SKILL: 'menu:application:skill',
  MENU_APPLICATION_DATABASE: 'menu:application:database',
  MENU_WORKFLOW: 'menu:workflow',
  MENU_ADMIN: 'menu:admin',
  MENU_ADMIN_INDEX: 'menu:admin:index',
  MENU_ADMIN_SYSTEM: 'menu:admin:system',
  MENU_ADMIN_SYSTEM_GENERAL: 'menu:admin:system:general',
  MENU_ADMIN_SYSTEM_KB_CONFIG: 'menu:admin:system:kb-config',
  MENU_ADMIN_SYSTEM_SENSITIVE: 'menu:admin:system:sensitive',
  MENU_ADMIN_SYSTEM_DICTIONARY: 'menu:admin:system:dictionary',
  MENU_ADMIN_SYSTEM_TERMINOLOGY: 'menu:admin:system:terminology',
  MENU_ADMIN_ACCOUNT: 'menu:admin:account',
  MENU_ADMIN_ACCOUNT_ROLES: 'menu:admin:account:roles',
  MENU_ADMIN_ACCOUNT_ORG: 'menu:admin:account:org',
  MENU_ADMIN_ACCOUNT_PERSONNEL: 'menu:admin:account:personnel',
  MENU_ADMIN_ACCOUNT_PERMISSIONS: 'menu:admin:account:permissions',
  MENU_ADMIN_AUDIT: 'menu:admin:audit',
  MENU_ADMIN_AUDIT_LOG: 'menu:admin:audit:system-log',
  MENU_ADMIN_PLATFORM: 'menu:admin:platform',
  MENU_ADMIN_PLATFORM_MODEL: 'menu:admin:platform:model',
  MENU_ADMIN_PLATFORM_APIKEY: 'menu:admin:platform:apikey',
  MENU_ADMIN_CONTENT: 'menu:admin:content',
  MENU_KNOWLEDGE_REVIEW: 'menu:knowledge-review',
  MENU_KNOWLEDGE_REVIEW_FLOWS: 'menu:knowledge-review:flows',
  MENU_KNOWLEDGE_REVIEW_FLOW_DESIGN: 'menu:knowledge-review:flow-design',
  MENU_KNOWLEDGE_REVIEW_LISTENERS: 'menu:knowledge-review:listeners',
  MENU_KNOWLEDGE_REVIEW_COMPLIANCE: 'menu:knowledge-review:compliance',
  MENU_KNOWLEDGE_REVIEW_REPORTS: 'menu:knowledge-review:reports',
  MENU_KNOWLEDGE_REVIEW_QUALITY: 'menu:knowledge-review:quality',
  MENU_OPERATION: 'menu:operation',
  MENU_OPERATION_KB_ANALYTICS: 'menu:operation:kb-analytics',
  MENU_OPERATION_MODEL_MONITOR: 'menu:operation:model-monitor',
  MENU_OPERATION_FEEDBACK: 'menu:operation:feedback',
  MENU_OPERATION_QA_DETAIL: 'menu:operation:qa-detail',
  MENU_OPERATION_RETRIEVAL_ANALYSIS: 'menu:operation:retrieval-analysis',
  MENU_ROBOT_FAQ: 'menu:robot-operation:faq',
  MENU_ROBOT_MULTI_TURN: 'menu:robot-operation:multi-turn',
  MENU_ROBOT_INTENT: 'menu:robot-operation:intent',
  MENU_ROBOT_DATA_MINING: 'menu:robot-operation:data-mining',
  MENU_APPLICATION_TOOLS: 'menu:application:tools',
  MENU_APPLICATION_WORKFLOW: 'menu:application:workflow',
  MENU_WORKSPACE: 'menu:workspace',

  // ===== 知识库操作权限 =====
  KB_CREATE: 'kb:create',
  KB_EDIT: 'kb:edit',
  KB_DELETE: 'kb:delete',
  KB_UPLOAD: 'kb:upload',
  KB_MANAGE_CHUNKS: 'kb:manage_chunks',
  KB_MANAGE_GRAPH: 'kb:manage_graph',
  KB_MANAGE_EVAL: 'kb:manage_eval',
  KB_MANAGE_STRATEGY: 'kb:manage_strategy',
  KB_VIEW: 'kb:view',
  KB_SEARCH: 'kb:search',
  KB_ACL_MANAGE: 'kb:acl_manage',
  KB_EXPORT: 'kb:export',

  // ===== 管理后台操作权限 =====
  ADMIN_ACCESS: 'admin:access',
  ADMIN_USER: 'admin:user',
  ADMIN_USER_CREATE: 'admin:user:create',
  ADMIN_USER_EDIT: 'admin:user:edit',
  ADMIN_USER_ROLE_CONFIG: 'admin:user:role-config',
  ADMIN_USER_DISABLE: 'admin:user:disable',
  ADMIN_ROLE: 'admin:role',
  ADMIN_ROLE_CREATE: 'admin:role:create',
  ADMIN_ROLE_EDIT: 'admin:role:edit',
  ADMIN_ROLE_DELETE: 'admin:role:delete',
  ADMIN_ROLE_SET_DEFAULT: 'admin:role:set-default',
  ADMIN_ORG: 'admin:org',
  ADMIN_ORG_CREATE: 'admin:org:create',
  ADMIN_ORG_EDIT: 'admin:org:edit',
  ADMIN_ORG_DELETE: 'admin:org:delete',
  ADMIN_SYSTEM: 'admin:system',
  ADMIN_AUDIT: 'admin:audit',

  // ===== 应用操作权限 =====
  APP_CREATE: 'app:create',
  APP_EDIT: 'app:edit',
  APP_DELETE: 'app:delete',
  APP_USE: 'app:use',
  APP_PUBLISH: 'app:publish',

  // ===== 业务流操作权限 =====
  WORKFLOW_CREATE: 'workflow:create',
  WORKFLOW_EDIT: 'workflow:edit',
  WORKFLOW_DELETE: 'workflow:delete',
  WORKFLOW_PUBLISH: 'workflow:publish',
  WORKFLOW_RUN: 'workflow:run',

  // ===== 审核权限 =====
  REVIEW_APPROVE: 'review:approve',
  REVIEW_REJECT: 'review:reject',
  REVIEW_FLOW_MANAGE: 'review:flow-manage',

  // ===== 其他操作权限 =====
  QA_MANAGE: 'qa:manage',
  TEST_CASE_MANAGE: 'testcase:manage',
  HOME_WORKSPACE_MANAGE: 'home:workspace:manage',

  // ===== 补充的页面操作权限 =====
  KB_FOLDER_CREATE: 'kb:folder:create',
  KB_FILE_RENAME: 'kb:file:rename',
  KB_FILE_MOVE: 'kb:file:move',
  KB_FILE_RESTORE: 'kb:file:restore',
  KB_FILE_PERMANENT_DELETE: 'kb:file:permanent-delete',
  KB_VERSION_CREATE: 'kb:version:create',
  KB_VERSION_PUBLISH: 'kb:version:publish',
  KB_VERSION_REVOKE: 'kb:version:revoke',
  KB_CATEGORY_CREATE: 'kb:category:create',
  KB_CATEGORY_EDIT: 'kb:category:edit',
  KB_CATEGORY_DELETE: 'kb:category:delete',
  APP_CONFIG_BASIC: 'app:config:basic',
  APP_CONFIG_PROMPT: 'app:config:prompt',
  APP_CONFIG_POLICY: 'app:config:policy',
  APP_EXPORT: 'app:export',
  APP_IMPORT: 'app:import',
  APP_KNOWLEDGE_UPDATE: 'app:knowledge-update',
  APP_MEMBER: 'app:member',
  TOOL_CREATE: 'tool:create',
  TOOL_EDIT: 'tool:edit',
  TOOL_DELETE: 'tool:delete',
  TOOL_TEST: 'tool:test',
  MCP_CREATE: 'mcp:create',
  MCP_EDIT: 'mcp:edit',
  MCP_DELETE: 'mcp:delete',
  MCP_TEST: 'mcp:test',
  SKILL_CREATE: 'skill:create',
  SKILL_EDIT: 'skill:edit',
  SKILL_DELETE: 'skill:delete',
  SKILL_EXPORT: 'skill:export',
  SKILL_SHARE: 'skill:share',
  MODEL_CREATE: 'model:create',
  MODEL_EDIT: 'model:edit',
  MODEL_DELETE: 'model:delete',
  MODEL_TEST: 'model:test',
  MODEL_IMPORT: 'model:import',
  MODEL_EXPORT: 'model:export',
  MODEL_TOGGLE: 'model:toggle',
  DICTIONARY_CREATE: 'dictionary:create',
  DICTIONARY_EDIT: 'dictionary:edit',
  DICTIONARY_DELETE: 'dictionary:delete',
  SENSITIVE_WORD_CREATE: 'sensitive-word:create',
  SENSITIVE_WORD_EDIT: 'sensitive-word:edit',
  SENSITIVE_WORD_DELETE: 'sensitive-word:delete',
  SENSITIVE_WORD_IMPORT: 'sensitive-word:import',
  TERMINOLOGY_LIBRARY_CREATE: 'terminology:library:create',
  TERMINOLOGY_LIBRARY_DELETE: 'terminology:library:delete',
  TERMINOLOGY_TERM_CREATE: 'terminology:term:create',
  TERMINOLOGY_TERM_EDIT: 'terminology:term:edit',
  TERMINOLOGY_TERM_DELETE: 'terminology:term:delete',
  TERMINOLOGY_IMPORT: 'terminology:import',
  API_KEY_CREATE: 'api-key:create',
  API_KEY_EDIT: 'api-key:edit',
  API_KEY_DELETE: 'api-key:delete',
  CONFIG_SAVE: 'config:save',
  CONFIG_IMPORT: 'config:import',
  CONFIG_EXPORT: 'config:export',
  FEEDBACK_CREATE: 'feedback:create',
  FEEDBACK_REPLY: 'feedback:reply',
  FEEDBACK_DELETE: 'feedback:delete',
  REVIEW_DETAIL: 'review:detail',
  REVIEW_REMIND: 'review:remind',
  REVIEW_EXPORT: 'review:export',
  AUDIT_LOG_VIEW: 'audit:log:view',
  DATA_MINING_CREATE: 'data-mining:create',
  DATA_MINING_RUN: 'data-mining:run',
  DATA_MINING_DELETE: 'data-mining:delete',
} as const

export type Permission = typeof PERMISSIONS[keyof typeof PERMISSIONS]

/** 权限树节点（用于角色管理页的权限勾选树） */
export interface PermissionTreeNode {
  key: string
  label: string
  children?: PermissionTreeNode[]
}

/** 权限树定义 — 三层结构：分类 → 模块分组 → 具体权限 */
export const PERMISSION_TREE: PermissionTreeNode[] = [
  // ===== 菜单权限 =====
  {
    key: 'cat_menu',
    label: '菜单权限',
    children: [
      { key: PERMISSIONS.MENU_HOME, label: '首页' },
      { key: PERMISSIONS.MENU_WORKSPACE, label: '工作台' },
      {
        key: 'menu_knowledge',
        label: '知识库',
        children: [
          { key: PERMISSIONS.MENU_KNOWLEDGE_LIST, label: '知识库列表' },
          { key: PERMISSIONS.MENU_KNOWLEDGE_CATEGORIES, label: '知识库分类' },
        ],
      },
      {
        key: 'menu_application',
        label: '应用中心',
        children: [
          { key: PERMISSIONS.MENU_APPLICATION_CENTER, label: '应用中心' },
          { key: PERMISSIONS.MENU_APPLICATION_RUNTIME, label: '应用运行' },
          { key: PERMISSIONS.MENU_APPLICATION_MY_TOOLS, label: '我的工具' },
          { key: PERMISSIONS.MENU_APPLICATION_MCP, label: 'MCP管理' },
          { key: PERMISSIONS.MENU_APPLICATION_SKILL, label: '技能管理' },
          { key: PERMISSIONS.MENU_APPLICATION_DATABASE, label: '数据库管理' },
        ],
      },
      { key: PERMISSIONS.MENU_WORKFLOW, label: '业务流' },
      {
        key: 'menu_admin',
        label: '管理中心',
        children: [
          { key: PERMISSIONS.MENU_ADMIN_INDEX, label: '管理中心概览' },
          {
            key: 'menu_admin_system',
            label: '系统管理',
            children: [
              { key: PERMISSIONS.MENU_ADMIN_SYSTEM_GENERAL, label: '通用设置' },
              { key: PERMISSIONS.MENU_ADMIN_SYSTEM_KB_CONFIG, label: '知识库配置' },
              { key: PERMISSIONS.MENU_ADMIN_SYSTEM_SENSITIVE, label: '敏感词设置' },
              { key: PERMISSIONS.MENU_ADMIN_SYSTEM_DICTIONARY, label: '字典管理' },
              { key: PERMISSIONS.MENU_ADMIN_SYSTEM_TERMINOLOGY, label: '术语管理' },
            ],
          },
          {
            key: 'menu_admin_account',
            label: '账号权限',
            children: [
              { key: PERMISSIONS.MENU_ADMIN_ACCOUNT_ROLES, label: '角色管理' },
              { key: PERMISSIONS.MENU_ADMIN_ACCOUNT_ORG, label: '组织管理' },
              { key: PERMISSIONS.MENU_ADMIN_ACCOUNT_PERSONNEL, label: '人员管理' },
              { key: PERMISSIONS.MENU_ADMIN_ACCOUNT_PERMISSIONS, label: '权限管理' },
            ],
          },
          {
            key: 'menu_admin_audit',
            label: '安全审计',
            children: [
              { key: PERMISSIONS.MENU_ADMIN_AUDIT_LOG, label: '系统日志' },
            ],
          },
          {
            key: 'menu_admin_platform',
            label: '开放平台',
            children: [
              { key: PERMISSIONS.MENU_ADMIN_PLATFORM_MODEL, label: '模型管理' },
              { key: PERMISSIONS.MENU_ADMIN_PLATFORM_APIKEY, label: '开放密钥' },
            ],
          },
        ],
      },
      {
        key: 'menu_knowledge_review',
        label: '知识审核',
        children: [
          { key: PERMISSIONS.MENU_KNOWLEDGE_REVIEW_FLOWS, label: '审核流程管理' },
          { key: PERMISSIONS.MENU_KNOWLEDGE_REVIEW_FLOW_DESIGN, label: '审核流程设计' },
          { key: PERMISSIONS.MENU_KNOWLEDGE_REVIEW_LISTENERS, label: '监听管理' },
          { key: PERMISSIONS.MENU_KNOWLEDGE_REVIEW_COMPLIANCE, label: '合规性检查' },
          { key: PERMISSIONS.MENU_KNOWLEDGE_REVIEW_REPORTS, label: '审核报告' },
          { key: PERMISSIONS.MENU_KNOWLEDGE_REVIEW_QUALITY, label: '质量评估' },
        ],
      },
      {
        key: 'menu_operation',
        label: '运营中心',
        children: [
          { key: PERMISSIONS.MENU_OPERATION_KB_ANALYTICS, label: '知识资产分析' },
          { key: PERMISSIONS.MENU_OPERATION_MODEL_MONITOR, label: '模型监控分析' },
          { key: PERMISSIONS.MENU_OPERATION_FEEDBACK, label: '用户反馈' },
          { key: PERMISSIONS.MENU_OPERATION_QA_DETAIL, label: '问答明细' },
          { key: PERMISSIONS.MENU_OPERATION_RETRIEVAL_ANALYSIS, label: '检索日志分析' },
        ],
      },
      {
        key: 'menu_robot_operation',
        label: '机器人运营',
        children: [
          { key: PERMISSIONS.MENU_ROBOT_FAQ, label: 'FAQ知识分析' },
          { key: PERMISSIONS.MENU_ROBOT_MULTI_TURN, label: '多轮对话分析' },
          { key: PERMISSIONS.MENU_ROBOT_INTENT, label: '意图知识分析' },
          { key: PERMISSIONS.MENU_ROBOT_DATA_MINING, label: '数据挖掘' },
        ],
      },
    ],
  },
  // ===== 页面操作 =====
  {
    key: 'cat_page_action',
    label: '页面操作',
    children: [
      {
        key: 'action_kb',
        label: '知识库操作',
        children: [
          { key: PERMISSIONS.KB_CREATE, label: '创建知识库' },
          { key: PERMISSIONS.KB_EDIT, label: '编辑知识库' },
          { key: PERMISSIONS.KB_DELETE, label: '删除知识库' },
          { key: PERMISSIONS.KB_UPLOAD, label: '上传文件' },
          { key: PERMISSIONS.KB_MANAGE_CHUNKS, label: '管理分片' },
          { key: PERMISSIONS.KB_MANAGE_GRAPH, label: '管理知识图谱' },
          { key: PERMISSIONS.KB_MANAGE_EVAL, label: '管理评估' },
          { key: PERMISSIONS.KB_MANAGE_STRATEGY, label: '管理解析策略' },
          { key: PERMISSIONS.KB_VIEW, label: '查看知识库' },
          { key: PERMISSIONS.KB_SEARCH, label: '搜索知识库' },
          { key: PERMISSIONS.KB_ACL_MANAGE, label: '知识库权限管理' },
          { key: PERMISSIONS.KB_EXPORT, label: '导出知识库' },
          { key: PERMISSIONS.KB_FOLDER_CREATE, label: '新建文件夹' },
          { key: PERMISSIONS.KB_FILE_RENAME, label: '重命名文件' },
          { key: PERMISSIONS.KB_FILE_MOVE, label: '移动文件' },
          { key: PERMISSIONS.KB_FILE_RESTORE, label: '恢复文件' },
          { key: PERMISSIONS.KB_FILE_PERMANENT_DELETE, label: '永久删除文件' },
          { key: PERMISSIONS.KB_VERSION_CREATE, label: '创建版本' },
          { key: PERMISSIONS.KB_VERSION_PUBLISH, label: '发布版本' },
          { key: PERMISSIONS.KB_VERSION_REVOKE, label: '撤回版本' },
          { key: PERMISSIONS.KB_CATEGORY_CREATE, label: '创建分类' },
          { key: PERMISSIONS.KB_CATEGORY_EDIT, label: '编辑分类' },
          { key: PERMISSIONS.KB_CATEGORY_DELETE, label: '删除分类' },
          { key: PERMISSIONS.QA_MANAGE, label: '问答管理' },
          { key: PERMISSIONS.TEST_CASE_MANAGE, label: '测试用例管理' },
        ],
      },
      {
        key: 'action_app',
        label: '应用操作',
        children: [
          { key: PERMISSIONS.APP_CREATE, label: '创建应用' },
          { key: PERMISSIONS.APP_EDIT, label: '编辑应用' },
          { key: PERMISSIONS.APP_DELETE, label: '删除应用' },
          { key: PERMISSIONS.APP_USE, label: '使用应用' },
          { key: PERMISSIONS.APP_PUBLISH, label: '发布应用' },
          { key: PERMISSIONS.APP_CONFIG_BASIC, label: '编辑基本参数' },
          { key: PERMISSIONS.APP_CONFIG_PROMPT, label: '编辑提示词' },
          { key: PERMISSIONS.APP_CONFIG_POLICY, label: '编辑安全策略' },
          { key: PERMISSIONS.APP_EXPORT, label: '导出应用配置' },
          { key: PERMISSIONS.APP_IMPORT, label: '导入应用配置' },
          { key: PERMISSIONS.APP_KNOWLEDGE_UPDATE, label: '管理知识更新' },
          { key: PERMISSIONS.APP_MEMBER, label: '管理应用成员' },
        ],
      },
      {
        key: 'action_workflow',
        label: '业务流操作',
        children: [
          { key: PERMISSIONS.WORKFLOW_CREATE, label: '创建业务流' },
          { key: PERMISSIONS.WORKFLOW_EDIT, label: '编辑业务流' },
          { key: PERMISSIONS.WORKFLOW_DELETE, label: '删除业务流' },
          { key: PERMISSIONS.WORKFLOW_PUBLISH, label: '发布业务流' },
          { key: PERMISSIONS.WORKFLOW_RUN, label: '运行业务流' },
        ],
      },
      {
        key: 'action_admin',
        label: '管理后台操作',
        children: [
          { key: PERMISSIONS.ADMIN_ACCESS, label: '访问管理后台' },
          { key: PERMISSIONS.ADMIN_USER_CREATE, label: '添加人员' },
          { key: PERMISSIONS.ADMIN_USER_EDIT, label: '编辑人员' },
          { key: PERMISSIONS.ADMIN_USER_ROLE_CONFIG, label: '人员角色配置' },
          { key: PERMISSIONS.ADMIN_USER_DISABLE, label: '启用/禁用人员' },
          { key: PERMISSIONS.ADMIN_ROLE_CREATE, label: '新增角色' },
          { key: PERMISSIONS.ADMIN_ROLE_EDIT, label: '编辑角色' },
          { key: PERMISSIONS.ADMIN_ROLE_DELETE, label: '删除角色' },
          { key: PERMISSIONS.ADMIN_ROLE_SET_DEFAULT, label: '设为默认角色' },
          { key: PERMISSIONS.ADMIN_ORG_CREATE, label: '新增组织' },
          { key: PERMISSIONS.ADMIN_ORG_EDIT, label: '编辑组织' },
          { key: PERMISSIONS.ADMIN_ORG_DELETE, label: '删除组织' },
          { key: PERMISSIONS.ADMIN_SYSTEM, label: '系统配置' },
          { key: PERMISSIONS.ADMIN_AUDIT, label: '审计管理' },
          { key: PERMISSIONS.AUDIT_LOG_VIEW, label: '查看审计日志' },
          { key: PERMISSIONS.DICTIONARY_CREATE, label: '新建字典条目' },
          { key: PERMISSIONS.DICTIONARY_EDIT, label: '编辑字典条目' },
          { key: PERMISSIONS.DICTIONARY_DELETE, label: '删除字典条目' },
          { key: PERMISSIONS.SENSITIVE_WORD_CREATE, label: '添加敏感词' },
          { key: PERMISSIONS.SENSITIVE_WORD_EDIT, label: '编辑敏感词' },
          { key: PERMISSIONS.SENSITIVE_WORD_DELETE, label: '删除敏感词' },
          { key: PERMISSIONS.SENSITIVE_WORD_IMPORT, label: '批量导入敏感词' },
          { key: PERMISSIONS.TERMINOLOGY_LIBRARY_CREATE, label: '新建术语库' },
          { key: PERMISSIONS.TERMINOLOGY_LIBRARY_DELETE, label: '删除术语库' },
          { key: PERMISSIONS.TERMINOLOGY_TERM_CREATE, label: '新建术语' },
          { key: PERMISSIONS.TERMINOLOGY_TERM_EDIT, label: '编辑术语' },
          { key: PERMISSIONS.TERMINOLOGY_TERM_DELETE, label: '删除术语' },
          { key: PERMISSIONS.TERMINOLOGY_IMPORT, label: '批量导入术语' },
          { key: PERMISSIONS.MODEL_CREATE, label: '新增模型' },
          { key: PERMISSIONS.MODEL_EDIT, label: '编辑模型' },
          { key: PERMISSIONS.MODEL_DELETE, label: '删除模型' },
          { key: PERMISSIONS.MODEL_TEST, label: '测试模型' },
          { key: PERMISSIONS.MODEL_IMPORT, label: '导入模型' },
          { key: PERMISSIONS.MODEL_EXPORT, label: '导出模型' },
          { key: PERMISSIONS.MODEL_TOGGLE, label: '上架/下架模型' },
          { key: PERMISSIONS.API_KEY_CREATE, label: '创建密钥' },
          { key: PERMISSIONS.API_KEY_EDIT, label: '编辑密钥' },
          { key: PERMISSIONS.API_KEY_DELETE, label: '删除密钥' },
          { key: PERMISSIONS.CONFIG_SAVE, label: '保存系统配置' },
          { key: PERMISSIONS.CONFIG_IMPORT, label: '导入系统配置' },
          { key: PERMISSIONS.CONFIG_EXPORT, label: '导出系统配置' },
          { key: PERMISSIONS.FEEDBACK_CREATE, label: '新增反馈' },
          { key: PERMISSIONS.FEEDBACK_REPLY, label: '回复反馈' },
          { key: PERMISSIONS.FEEDBACK_DELETE, label: '删除反馈' },
          { key: PERMISSIONS.DATA_MINING_CREATE, label: '新建数据挖掘任务' },
          { key: PERMISSIONS.DATA_MINING_RUN, label: '运行数据挖掘任务' },
          { key: PERMISSIONS.DATA_MINING_DELETE, label: '删除数据挖掘任务' },
        ],
      },
      {
        key: 'action_review',
        label: '审核操作',
        children: [
          { key: PERMISSIONS.REVIEW_APPROVE, label: '审核通过' },
          { key: PERMISSIONS.REVIEW_REJECT, label: '审核驳回' },
          { key: PERMISSIONS.REVIEW_FLOW_MANAGE, label: '审核流程管理' },
          { key: PERMISSIONS.REVIEW_DETAIL, label: '查看审核详情' },
          { key: PERMISSIONS.REVIEW_REMIND, label: '提醒审核' },
          { key: PERMISSIONS.REVIEW_EXPORT, label: '导出审核记录' },
        ],
      },
      {
        key: 'action_tool',
        label: '工具/MCP/技能',
        children: [
          { key: PERMISSIONS.TOOL_CREATE, label: '创建工具' },
          { key: PERMISSIONS.TOOL_EDIT, label: '编辑工具' },
          { key: PERMISSIONS.TOOL_DELETE, label: '删除工具' },
          { key: PERMISSIONS.TOOL_TEST, label: '测试工具' },
          { key: PERMISSIONS.MCP_CREATE, label: '创建MCP服务' },
          { key: PERMISSIONS.MCP_EDIT, label: '编辑MCP服务' },
          { key: PERMISSIONS.MCP_DELETE, label: '删除MCP服务' },
          { key: PERMISSIONS.MCP_TEST, label: '测试MCP工具' },
          { key: PERMISSIONS.SKILL_CREATE, label: '创建技能' },
          { key: PERMISSIONS.SKILL_EDIT, label: '编辑技能' },
          { key: PERMISSIONS.SKILL_DELETE, label: '删除技能' },
          { key: PERMISSIONS.SKILL_EXPORT, label: '导出技能' },
          { key: PERMISSIONS.SKILL_SHARE, label: '配置技能分享' },
        ],
      },
    ],
  },
]

// ===========================================================================
// 菜单-权限映射
// ===========================================================================

/** 菜单项定义（与 Sidebar.vue 的 navModules 对齐） */
export interface MenuPermission {
  path: string
  title: string
  requiredPerms: string[]
  children?: MenuPermission[]
}

/**
 * 菜单权限映射表。
 * 声明每个菜单项需要哪些权限才能看到。
 * Sidebar.vue 的过滤逻辑从此表读取，取代硬编码 requirePerm。
 */
export const MENU_PERMISSION_MAP: MenuPermission[] = [
  {
    path: '/home',
    title: '首页',
    requiredPerms: [PERMISSIONS.MENU_HOME],
  },
  {
    path: '/knowledge',
    title: '知识库',
    requiredPerms: [PERMISSIONS.MENU_KNOWLEDGE],
  },
  {
    path: '/application',
    title: '应用',
    requiredPerms: [PERMISSIONS.MENU_APPLICATION],
    children: [
      { path: '/application', title: '应用中心', requiredPerms: [PERMISSIONS.APP_USE] },
      { path: '/application/my-tools', title: '我的工具', requiredPerms: [PERMISSIONS.APP_USE] },
      { path: '/application/mcp-management', title: 'MCP管理', requiredPerms: [PERMISSIONS.APP_EDIT] },
      { path: '/application/skill-management', title: '技能管理', requiredPerms: [PERMISSIONS.APP_EDIT] },
      { path: '/application/database-management', title: '数据库管理', requiredPerms: [PERMISSIONS.APP_EDIT] },
      { path: '/application/workflow', title: '业务流', requiredPerms: [PERMISSIONS.WORKFLOW_EDIT] },
      { path: '/application/test-cases', title: '测试案例', requiredPerms: [PERMISSIONS.TEST_CASE_MANAGE] },
      { path: '/operation/kb-analytics', title: '运营中心', requiredPerms: [PERMISSIONS.ADMIN_ACCESS] },
    ],
  },
  {
    path: '/admin',
    title: '管理',
    requiredPerms: [PERMISSIONS.MENU_ADMIN],
    children: [
      { path: '/admin/index', title: '管理中心概览', requiredPerms: [PERMISSIONS.ADMIN_ACCESS] },
      {
        path: '/admin/system',
        title: '系统管理',
        requiredPerms: [PERMISSIONS.MENU_ADMIN_SYSTEM],
        children: [
          { path: '/admin/system/general-settings', title: '通用设置', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/system/kb-config', title: '知识库配置', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/system/sensitive-words', title: '敏感词设置', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/system/dictionary', title: '字典管理', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
	          { path: '/admin/system/terminology', title: '术语管理', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
        ],
      },
      {
        path: '/admin/account',
        title: '账号权限',
        requiredPerms: [PERMISSIONS.MENU_ADMIN_ACCOUNT],
        children: [
          { path: '/admin/account/roles', title: '角色管理', requiredPerms: [PERMISSIONS.ADMIN_ROLE] },
          { path: '/admin/account/organization', title: '组织管理', requiredPerms: [PERMISSIONS.ADMIN_ORG] },
          { path: '/admin/account/personnel', title: '人员管理', requiredPerms: [PERMISSIONS.ADMIN_USER] },
          { path: '/admin/permissions', title: '权限管理', requiredPerms: [PERMISSIONS.ADMIN_ROLE] },
        ],
      },
      {
        path: '/admin/audit',
        title: '安全审计',
        requiredPerms: [PERMISSIONS.MENU_ADMIN_AUDIT],
        children: [
	          { path: '/admin/audit/system-log', title: '系统日志', requiredPerms: [PERMISSIONS.ADMIN_AUDIT] },
        ],
      },
      {
        path: '/admin/platform',
        title: '开放平台',
        requiredPerms: [PERMISSIONS.MENU_ADMIN_PLATFORM],
        children: [
          { path: '/admin/platform/model-management', title: '模型管理', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/platform/api-keys', title: '开放密钥', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
        ],
      },
    ],
  },
]

// =========================================================================// 权限详情（用于权限管理页展示）
// =========================================================================

/** 权限类型 */
export type PermissionType = 'menu' | 'action'

/** 权限详情 */
export interface PermissionDetail {
  key: string
  name: string
  type: PermissionType
  group: string
  description?: string
}

// ===========================================================================
// 角色 → 权限映射（mock 层用，后端登录时会返回用户的 permissions）
// ===========================================================================
export const ROLE_PERMISSIONS: Record<SystemRole, string[]> = {
  super_admin: ['*'],
  kb_admin: [
    // 菜单权限
    PERMISSIONS.MENU_HOME, PERMISSIONS.MENU_KNOWLEDGE, PERMISSIONS.MENU_KNOWLEDGE_LIST,
    PERMISSIONS.MENU_APPLICATION, PERMISSIONS.MENU_APPLICATION_CENTER,
    PERMISSIONS.MENU_ADMIN, PERMISSIONS.MENU_ADMIN_INDEX,
    PERMISSIONS.MENU_ADMIN_SYSTEM, PERMISSIONS.MENU_ADMIN_SYSTEM_KB_CONFIG,
    PERMISSIONS.MENU_ADMIN_SYSTEM_TERMINOLOGY,
    PERMISSIONS.MENU_ADMIN_ACCOUNT, PERMISSIONS.MENU_ADMIN_ACCOUNT_ORG,
    PERMISSIONS.MENU_ADMIN_ACCOUNT_PERSONNEL, PERMISSIONS.MENU_ADMIN_ACCOUNT_PERMISSIONS,
    PERMISSIONS.MENU_KNOWLEDGE_REVIEW, PERMISSIONS.MENU_OPERATION,
    // 知识库权限
    PERMISSIONS.KB_CREATE, PERMISSIONS.KB_EDIT, PERMISSIONS.KB_DELETE,
    PERMISSIONS.KB_UPLOAD, PERMISSIONS.KB_MANAGE_CHUNKS, PERMISSIONS.KB_MANAGE_GRAPH,
    PERMISSIONS.KB_MANAGE_EVAL, PERMISSIONS.KB_MANAGE_STRATEGY,
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH, PERMISSIONS.KB_ACL_MANAGE,
    // 管理权限
    PERMISSIONS.ADMIN_ACCESS, PERMISSIONS.ADMIN_USER, PERMISSIONS.ADMIN_USER_CREATE,
    PERMISSIONS.ADMIN_USER_EDIT, PERMISSIONS.ADMIN_USER_DISABLE,
    PERMISSIONS.ADMIN_ORG, PERMISSIONS.ADMIN_ORG_CREATE, PERMISSIONS.ADMIN_ORG_EDIT,
    PERMISSIONS.ADMIN_SYSTEM,
    // 应用权限
    PERMISSIONS.APP_CREATE, PERMISSIONS.APP_EDIT, PERMISSIONS.APP_USE,
    // 业务流权限
    PERMISSIONS.WORKFLOW_CREATE, PERMISSIONS.WORKFLOW_EDIT,
    PERMISSIONS.WORKFLOW_DELETE, PERMISSIONS.WORKFLOW_PUBLISH,
    // 审核权限
    PERMISSIONS.REVIEW_APPROVE, PERMISSIONS.REVIEW_REJECT,
    PERMISSIONS.REVIEW_FLOW_MANAGE,
    // 其他
    PERMISSIONS.QA_MANAGE, PERMISSIONS.TEST_CASE_MANAGE,
  ],
  kb_user: [
    // 菜单权限
    PERMISSIONS.MENU_HOME, PERMISSIONS.MENU_KNOWLEDGE, PERMISSIONS.MENU_KNOWLEDGE_LIST,
    PERMISSIONS.MENU_APPLICATION, PERMISSIONS.MENU_APPLICATION_CENTER,
    // 知识库权限
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH, PERMISSIONS.KB_UPLOAD,
    PERMISSIONS.KB_MANAGE_CHUNKS, PERMISSIONS.KB_MANAGE_GRAPH,
    PERMISSIONS.KB_MANAGE_EVAL, PERMISSIONS.KB_MANAGE_STRATEGY,
    PERMISSIONS.KB_CREATE, PERMISSIONS.KB_EDIT,
    // 应用权限
    PERMISSIONS.APP_USE, PERMISSIONS.APP_CREATE, PERMISSIONS.APP_EDIT,
    // 其他
    PERMISSIONS.QA_MANAGE, PERMISSIONS.TEST_CASE_MANAGE,
  ],
  readonly: [
    // 菜单权限
    PERMISSIONS.MENU_HOME, PERMISSIONS.MENU_KNOWLEDGE, PERMISSIONS.MENU_KNOWLEDGE_LIST,
    PERMISSIONS.MENU_APPLICATION, PERMISSIONS.MENU_APPLICATION_CENTER,
    // 只读权限
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH,
    PERMISSIONS.APP_USE,
  ],
}

/** 角色中文名映射 */
export const ROLE_LABELS: Record<SystemRole, string> = {
  super_admin: '超级管理员',
  kb_admin: '知识库管理员',
  kb_user: '知识库用户',
  readonly: '只读用户',
}

// ===========================================================================
// 知识库 ACL（Access Control List）
// ===========================================================================

/** 知识库内角色 */
export type KBRole = 'owner' | 'editor' | 'viewer'

/** 知识库 ACL 条目 */
export interface KBAclEntry {
  kbId: string
  userId: string
  kbRole: KBRole
  grantedBy: string
  grantedAt: string
}

/** 知识库角色 → 可执行操作 */
export const KB_ROLE_PERMISSIONS: Record<KBRole, string[]> = {
  owner: [
    PERMISSIONS.KB_EDIT, PERMISSIONS.KB_DELETE, PERMISSIONS.KB_UPLOAD,
    PERMISSIONS.KB_MANAGE_CHUNKS, PERMISSIONS.KB_MANAGE_GRAPH,
    PERMISSIONS.KB_MANAGE_EVAL, PERMISSIONS.KB_MANAGE_STRATEGY,
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH, PERMISSIONS.KB_ACL_MANAGE,
  ],
  editor: [
    PERMISSIONS.KB_UPLOAD, PERMISSIONS.KB_MANAGE_CHUNKS,
    PERMISSIONS.KB_MANAGE_GRAPH, PERMISSIONS.KB_MANAGE_EVAL,
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH,
  ],
  viewer: [
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH,
  ],
}

/** 知识库角色中文名 */
export const KB_ROLE_LABELS: Record<KBRole, string> = {
  owner: '所有者',
  editor: '编辑者',
  viewer: '查看者',
}
