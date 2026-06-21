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
  MENU_APPLICATION: 'menu:application',
  MENU_WORKFLOW: 'menu:workflow',
  MENU_ADMIN: 'menu:admin',
  MENU_ADMIN_SYSTEM: 'menu:admin:system',
  MENU_ADMIN_ACCOUNT: 'menu:admin:account',
  MENU_ADMIN_AUDIT: 'menu:admin:audit',
  MENU_ADMIN_CONTENT: 'menu:admin:content',
  MENU_ADMIN_PLATFORM: 'menu:admin:platform',

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

  // ===== 管理后台操作权限 =====
  ADMIN_ACCESS: 'admin:access',
  ADMIN_USER: 'admin:user',
  ADMIN_ROLE: 'admin:role',
  ADMIN_ORG: 'admin:org',
  ADMIN_SYSTEM: 'admin:system',
  ADMIN_AUDIT: 'admin:audit',

  // ===== 应用操作权限 =====
  APP_CREATE: 'app:create',
  APP_EDIT: 'app:edit',
  APP_USE: 'app:use',

  // ===== 业务流操作权限 =====
  WORKFLOW_CREATE: 'workflow:create',
  WORKFLOW_EDIT: 'workflow:edit',
  WORKFLOW_DELETE: 'workflow:delete',
  WORKFLOW_PUBLISH: 'workflow:publish',

  // ===== 审核权限 =====
  REVIEW_APPROVE: 'review:approve',
  REVIEW_REJECT: 'review:reject',

  // ===== 其他操作权限 =====
  QA_MANAGE: 'qa:manage',
  TEST_CASE_MANAGE: 'testcase:manage',
} as const

export type Permission = typeof PERMISSIONS[keyof typeof PERMISSIONS]

/** 权限树节点（用于角色管理页的权限勾选树） */
export interface PermissionTreeNode {
  key: string
  label: string
  children?: PermissionTreeNode[]
}

/** 权限树定义（与 PERMISSIONS 常量一一对应） */
export const PERMISSION_TREE: PermissionTreeNode[] = [
  {
    key: 'menu',
    label: '菜单权限',
    children: [
      { key: PERMISSIONS.MENU_HOME, label: '首页' },
      { key: PERMISSIONS.MENU_KNOWLEDGE, label: '知识库' },
      { key: PERMISSIONS.MENU_APPLICATION, label: '应用' },
      { key: PERMISSIONS.MENU_WORKFLOW, label: '业务流' },
      { key: PERMISSIONS.MENU_ADMIN, label: '管理中心' },
      { key: PERMISSIONS.MENU_ADMIN_SYSTEM, label: '系统管理' },
      { key: PERMISSIONS.MENU_ADMIN_ACCOUNT, label: '账号权限' },
      { key: PERMISSIONS.MENU_ADMIN_AUDIT, label: '安全审计' },
      { key: PERMISSIONS.MENU_ADMIN_CONTENT, label: '内容与工具' },
      { key: PERMISSIONS.MENU_ADMIN_PLATFORM, label: '开放平台' },
    ],
  },
  {
    key: 'kb',
    label: '知识库',
    children: [
      { key: PERMISSIONS.KB_VIEW, label: '查看知识库' },
      { key: PERMISSIONS.KB_SEARCH, label: '检索知识库' },
      { key: PERMISSIONS.KB_CREATE, label: '创建知识库' },
      { key: PERMISSIONS.KB_EDIT, label: '编辑知识库设置' },
      { key: PERMISSIONS.KB_DELETE, label: '删除知识库' },
      { key: PERMISSIONS.KB_UPLOAD, label: '上传文件' },
      { key: PERMISSIONS.KB_MANAGE_CHUNKS, label: '管理分片' },
      { key: PERMISSIONS.KB_MANAGE_GRAPH, label: '管理知识图谱' },
      { key: PERMISSIONS.KB_MANAGE_EVAL, label: '管理评估' },
      { key: PERMISSIONS.KB_MANAGE_STRATEGY, label: '管理解析策略' },
      { key: PERMISSIONS.KB_ACL_MANAGE, label: '管理知识库权限' },
    ],
  },
  {
    key: 'app',
    label: '应用中心',
    children: [
      { key: PERMISSIONS.APP_CREATE, label: '创建应用' },
      { key: PERMISSIONS.APP_EDIT, label: '编辑应用' },
      { key: PERMISSIONS.APP_USE, label: '使用应用' },
    ],
  },
  {
    key: 'workflow',
    label: '业务流',
    children: [
      { key: PERMISSIONS.WORKFLOW_CREATE, label: '创建业务流' },
      { key: PERMISSIONS.WORKFLOW_EDIT, label: '编辑业务流' },
      { key: PERMISSIONS.WORKFLOW_DELETE, label: '删除业务流' },
      { key: PERMISSIONS.WORKFLOW_PUBLISH, label: '发布业务流' },
    ],
  },
  {
    key: 'review',
    label: '审核管理',
    children: [
      { key: PERMISSIONS.REVIEW_APPROVE, label: '审核通过' },
      { key: PERMISSIONS.REVIEW_REJECT, label: '审核驳回' },
    ],
  },
  {
    key: 'admin',
    label: '管理中心',
    children: [
      { key: PERMISSIONS.ADMIN_ACCESS, label: '访问管理后台' },
      { key: PERMISSIONS.ADMIN_USER, label: '用户管理' },
      { key: PERMISSIONS.ADMIN_ROLE, label: '角色管理' },
      { key: PERMISSIONS.ADMIN_ORG, label: '组织管理' },
      { key: PERMISSIONS.ADMIN_SYSTEM, label: '系统配置' },
      { key: PERMISSIONS.ADMIN_AUDIT, label: '审计日志' },
      { key: PERMISSIONS.QA_MANAGE, label: '问答管理' },
      { key: PERMISSIONS.TEST_CASE_MANAGE, label: '测试案例管理' },
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
          { path: '/admin/system/global-settings', title: '全局设置', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/system/sensitive-words', title: '敏感词设置', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/system/dictionary', title: '字典管理', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/system/terminology', title: '术语管理', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/system/query-rules', title: '查询规则', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
        ],
      },
      {
        path: '/admin/account',
        title: '账号权限',
        requiredPerms: [PERMISSIONS.MENU_ADMIN_ACCOUNT],
        children: [
          { path: '/admin/account/roles', title: '角色管理', requiredPerms: [PERMISSIONS.ADMIN_ROLE] },
          { path: '/admin/account/organization', title: '组织管理', requiredPerms: [PERMISSIONS.ADMIN_ORG] },
          { path: '/admin/account/team', title: '团队管理', requiredPerms: [PERMISSIONS.ADMIN_ROLE] },
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
          { path: '/admin/audit/device-login', title: '设备登录分析', requiredPerms: [PERMISSIONS.ADMIN_AUDIT] },
          { path: '/admin/audit/login-security', title: '登录安全配置', requiredPerms: [PERMISSIONS.ADMIN_AUDIT] },
          { path: '/admin/audit/review-center', title: '审核中心', requiredPerms: [PERMISSIONS.REVIEW_APPROVE] },
        ],
      },
      {
        path: '/admin/content',
        title: '内容与工具',
        requiredPerms: [PERMISSIONS.MENU_ADMIN_CONTENT],
        children: [
          { path: '/admin/content/notification', title: '通知管理', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/content/tags', title: '标签管理', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/content/prompts', title: '提示词', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/content/templates', title: '文档模板', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
          { path: '/admin/content/download', title: '下载中心', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
        ],
      },
      {
        path: '/admin/platform',
        title: '开放平台',
        requiredPerms: [PERMISSIONS.MENU_ADMIN_PLATFORM],
        children: [
          { path: '/admin/platform/third-party', title: '三方平台', requiredPerms: [PERMISSIONS.ADMIN_SYSTEM] },
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
    PERMISSIONS.MENU_HOME, PERMISSIONS.MENU_KNOWLEDGE, PERMISSIONS.MENU_APPLICATION,
    PERMISSIONS.MENU_ADMIN, PERMISSIONS.MENU_ADMIN_SYSTEM, PERMISSIONS.MENU_ADMIN_ACCOUNT,
    PERMISSIONS.MENU_ADMIN_CONTENT, PERMISSIONS.MENU_ADMIN_PLATFORM,
    // 知识库权限
    PERMISSIONS.KB_CREATE, PERMISSIONS.KB_EDIT, PERMISSIONS.KB_DELETE,
    PERMISSIONS.KB_UPLOAD, PERMISSIONS.KB_MANAGE_CHUNKS, PERMISSIONS.KB_MANAGE_GRAPH,
    PERMISSIONS.KB_MANAGE_EVAL, PERMISSIONS.KB_MANAGE_STRATEGY,
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH, PERMISSIONS.KB_ACL_MANAGE,
    // 管理权限
    PERMISSIONS.ADMIN_ACCESS, PERMISSIONS.ADMIN_USER, PERMISSIONS.ADMIN_ROLE,
    PERMISSIONS.ADMIN_ORG, PERMISSIONS.ADMIN_SYSTEM,
    // 应用权限
    PERMISSIONS.APP_CREATE, PERMISSIONS.APP_EDIT, PERMISSIONS.APP_USE,
    // 业务流权限
    PERMISSIONS.WORKFLOW_CREATE, PERMISSIONS.WORKFLOW_EDIT,
    PERMISSIONS.WORKFLOW_DELETE, PERMISSIONS.WORKFLOW_PUBLISH,
    // 审核权限
    PERMISSIONS.REVIEW_APPROVE, PERMISSIONS.REVIEW_REJECT,
    // 其他
    PERMISSIONS.QA_MANAGE, PERMISSIONS.TEST_CASE_MANAGE,
  ],
  kb_user: [
    // 菜单权限
    PERMISSIONS.MENU_HOME, PERMISSIONS.MENU_KNOWLEDGE, PERMISSIONS.MENU_APPLICATION,
    // 知识库权限
    PERMISSIONS.KB_VIEW, PERMISSIONS.KB_SEARCH, PERMISSIONS.KB_UPLOAD,
    PERMISSIONS.KB_MANAGE_CHUNKS, PERMISSIONS.KB_MANAGE_GRAPH,
    PERMISSIONS.KB_MANAGE_EVAL, PERMISSIONS.KB_MANAGE_STRATEGY,
    PERMISSIONS.KB_CREATE,
    // 应用权限
    PERMISSIONS.APP_CREATE, PERMISSIONS.APP_EDIT, PERMISSIONS.APP_USE,
    // 其他
    PERMISSIONS.QA_MANAGE, PERMISSIONS.TEST_CASE_MANAGE,
  ],
  readonly: [
    // 菜单权限
    PERMISSIONS.MENU_HOME, PERMISSIONS.MENU_KNOWLEDGE, PERMISSIONS.MENU_APPLICATION,
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
