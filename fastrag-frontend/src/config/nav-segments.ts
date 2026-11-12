/**
 * 平台导航四段单一事实源（nav-shell 链，MOD-NAV-SHELL-FRONTEND）。
 *
 * 菜单按「对话 / 构建 / 资源 / 管理」动线分组（对标腾讯云 ADP，CONTEXT.md 决策快照 #1/#2）；
 * 主页顶置于四段之上（决策 #5）。权限键零改动（ADR-NAV-PERM-TREE-DECOUPLING）：
 * 本文件只描述展示归属，菜单可见性由 Sidebar 按 path 查 MENU_PERMISSION_MAP 决定。
 * 增删菜单只改本数组；页面路由与权限树不改。
 *
 * 修订 #14（2026-10-26）：用户入口移至内容卡头右上角（UserMenu 组件）。
 * 修订 #15（2026-10-26）：管理段 12 项收进用户下拉「管理」分组；数据分析 4 项回到
 * 侧边栏作为第四段；侧边栏支持折叠（图标态 + 悬停浮出完整菜单）。权限门禁与权限树零改动。
 *
 * 用户下拉语义分组（修订 #16）：头部（姓名）→ 用户信息 → 门户资源（操作手册/产品动态）
 * → 管理（12 项权限门禁）→ 底部独立退出系统（danger，恒在最底）。
 */

/** 菜单项（展示归属描述，不含权限键——权限由 path → MENU_PERMISSION_MAP 解析） */
export interface NavItem {
  /** 菜单标识 */
  key: string
  /** 显示文字 */
  title: string
  /** 既有路由路径（零新增路由，除 /notification 接线外不改路由表） */
  path: string
  /** Element Plus 图标名（全局注册，字符串引用） */
  icon?: string
}

/** 用户下拉内的子分组（非交互标签；cascade=true 时渲染为级联子菜单行，修订 #17） */
export interface NavSubgroup {
  title: string
  items: NavItem[]
  /** 级联子菜单：下拉内仅渲染一行（标题+箭头），悬停向左弹出子面板 */
  cascade?: boolean
}

/** 导航段（对话 / 构建 / 资源 / 数据分析） */
export interface NavSegment {
  key: 'conversation' | 'build' | 'resource' | 'analytics'
  title: string
  items: NavItem[]
}

/** 用户下拉菜单项 */
export interface UserMenuItem {
  key: 'profile' | 'manual' | 'changelog' | 'logout'
  title: string
  /** 跳转目标（profile/logout 为动作项，无 path） */
  path?: string
}

/** 导航配置（Sidebar / UserMenu 渲染的唯一输入） */
export interface NavConfig {
  /** 顶置主页项（段结构之外） */
  home: NavItem
  /** 四段，顺序即渲染顺序 */
  segments: NavSegment[]
  /** 用户下拉：头部正下方单项（用户信息） */
  userMenuProfile: UserMenuItem
  /** 用户下拉语义分组（修订 #16）：门户资源（恒显）+ 管理（权限门禁） */
  userMenuGroups: NavSubgroup[]
  /** 用户下拉底部独立退出（danger，恒在最底） */
  userMenuLogout: UserMenuItem
}

export const navConfig: NavConfig = {
  home: { key: 'home', title: '主页', path: '/home', icon: 'HomeFilled' },

  segments: [
    {
      key: 'conversation',
      title: '对话',
      items: [{ key: 'conversation-center', title: '会话中心', path: '/conversation', icon: 'ChatLineRound' }],
    },
    {
      key: 'build',
      title: '构建',
      items: [{ key: 'app-center', title: '应用中心', path: '/application', icon: 'Grid' }],
    },
    {
      key: 'resource',
      title: '资源',
      items: [
        { key: 'knowledge-list', title: '知识库', path: '/knowledge', icon: 'Collection' },
        { key: 'my-tools', title: '我的工具', path: '/application/my-tools', icon: 'Tools' },
        { key: 'mcp-management', title: 'MCP管理', path: '/application/mcp-management', icon: 'Connection' },
        { key: 'skill-management', title: '技能管理', path: '/application/skill-management', icon: 'MagicStick' },
        { key: 'sandbox-configs', title: '沙箱配置', path: '/application/sandbox-management', icon: 'Box' },
        { key: 'database-management', title: '数据库管理', path: '/application/database-management', icon: 'Coin' },
        { key: 'model-management', title: '模型管理', path: '/admin/platform/model-management', icon: 'Cpu' },
        { key: 'prompt-templates', title: '提示词', path: '/application/prompt-templates', icon: 'ChatDotRound' },
        // widget 链（PAGE-201）：资源段末项；无权限键不入权限树，登录恒显（同会话中心口径）
        { key: 'widget', title: 'Widget', path: '/widget', icon: 'Postcard' },
      ],
    },
    {
      key: 'analytics',
      title: '数据分析',
      items: [
        { key: 'kb-analytics', title: '知识库分析', path: '/operation/kb-analytics', icon: 'DataAnalysis' },
        { key: 'model-monitor', title: '模型监控', path: '/operation/model-monitor', icon: 'Monitor' },
      ],
    },
  ],

  userMenuProfile: { key: 'profile', title: '用户信息' },

  // 修订 #16：门户资源分组（公开页，恒显，平铺）+ 管理分组（修订 #17，级联子菜单）
  userMenuGroups: [
    {
      title: '门户资源',
      items: [
        { key: 'manual', title: '操作手册', path: '/portal/manual', icon: 'Reading' },
        { key: 'changelog', title: '产品动态', path: '/portal/changelog', icon: 'List' },
      ],
    },
    {
      title: '管理',
      cascade: true,
      items: [
        { key: 'general-settings', title: '通用设置', path: '/admin/system/general-settings', icon: 'Setting' },
        { key: 'sandbox-settings', title: '沙箱设置', path: '/admin/system/sandbox-settings', icon: 'Box' },
        { key: 'kb-config', title: '知识库配置', path: '/admin/system/kb-config', icon: 'Collection' },
        { key: 'sensitive-words', title: '敏感词设置', path: '/admin/system/sensitive-words', icon: 'WarningFilled' },
        { key: 'dictionary', title: '字典管理', path: '/admin/system/dictionary', icon: 'Notebook' },
        { key: 'terminology', title: '术语管理', path: '/admin/system/terminology', icon: 'Reading' },
        { key: 'roles', title: '角色管理', path: '/admin/account/roles', icon: 'User' },
        { key: 'organization', title: '组织管理', path: '/admin/account/organization', icon: 'OfficeBuilding' },
        { key: 'personnel', title: '人员管理', path: '/admin/account/personnel', icon: 'UserFilled' },
        { key: 'permissions', title: '权限管理', path: '/admin/permissions', icon: 'Key' },
        { key: 'system-log', title: '系统日志', path: '/admin/audit/system-log', icon: 'Document' },
        { key: 'api-keys', title: '开放密钥', path: '/admin/platform/api-keys', icon: 'Key' },
      ],
    },
  ],

  userMenuLogout: { key: 'logout', title: '退出系统' },
}

