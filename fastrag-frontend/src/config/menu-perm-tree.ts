/**
 * 菜单页面 → 页面操作 / API 接口 权限映射表。
 *
 * 角色权限配置页（/admin/account/roles/:id/permissions）按此表以「菜单页面」为单位展示权限：
 * 先勾选菜单页面，其下所有页面操作与 API 接口默认全选，可再逐项微调。
 *
 * - `keys`：勾选该菜单节点时授予/回收的权限键。页面节点同时包含 sys_permission 的菜单键
 *   与侧边栏显隐真正校验的键（与 types/auth.ts 的 MENU_PERMISSION_MAP / PERMISSIONS 对齐），
 *   二者都授予以保证菜单实际可见。
 * - `actions` / `apis`：该页面内的页面操作与 API 接口 permKey（均为 sys_permission 已有记录；
 *   渲染时会与 GET /permissions/tree 的返回求交集，后端已删除的记录不展示）。
 * - 无 `keys` 的节点为纯展示分组（与侧边栏导航分组一致），勾选时级联到全部子节点。
 *
 * 允许多对多：同一操作 / API 可挂在多个页面下，勾选任意一处即授予。
 * 未挂载：api:auth:login（登录前调用，无页面归属）、api:agent:*（暂无对应菜单页面）、
 * review:flow-manage / review:detail / review:remind / review:export 与 data-mining:* /
 * api:data-mining:*（消费页面 /knowledge-review/*、/robot-operation/* 未接入侧边栏导航，
 * 为孤儿路由，故不纳入菜单树；已有角色的存量授权保存时原样保留）。
 * 新增页面 / 权限后需同步维护此表。
 */
export interface MenuPermNode {
  /** 勾选此节点时授予/回收的门禁权限键（目录节点或页面自身） */
  keys?: string[]
  title: string
  /** 页面内的页面操作权限键 */
  actions?: string[]
  /** 页面内的 API 接口权限键 */
  apis?: string[]
  children?: MenuPermNode[]
}

/**
 * 页面操作 ↔ API 接口 联动组。
 *
 * 同组内的权限在角色权限配置页中联动勾选/取消：勾选任一权限，组内其余权限同步勾选；
 * 取消任一权限，组内其余权限同步取消（视为一个整体开关）。典型对应如
 * 「创建知识库」(kb:create) ↔ api:kb:create。
 *
 * 联动是全局语义（按 permKey 匹配，跨页面生效）：同一操作挂在多个页面下时，
 * 在任一页面勾选其联动组即授予全部对应权限。
 * 菜单键（menu:*）与无对应关系的只读列表类 API（如 api:kb:list、api:app:list）不进组，
 * 跟随所在页面的菜单勾选状态。
 * 新增页面操作 / API 权限后需同步维护此表。
 */
export const ACTION_API_LINKS: Record<string, string[]> = {
  // ----- 首页 -----
  'home:workspace:manage': ['api:home:data'],

  // ----- 知识库 -----
  'kb:create': ['api:kb:create'],
  'kb:edit': ['api:kb:update'],
  'kb:delete': ['api:kb:delete'],
  'kb:upload': ['api:kb:file:upload', 'api:kb:folder:create'],
  'kb:export': ['api:kb:file:download'],
  'kb:acl_manage': ['api:kb:acl', 'api:user:acl'],
  'kb:folder:create': ['api:kb:folder:create'],
  'kb:file:rename': ['api:kb:file:update', 'api:kb:folder:rename'],
  'kb:file:move': ['api:kb:file:move', 'api:kb:file:copy'],
  'kb:file:restore': ['api:kb:file:restore'],
  'kb:manage_chunks': [
    'api:kb:chunk:list', 'api:kb:chunk:create', 'api:kb:chunk:update', 'api:kb:chunk:delete',
  ],
  'kb:manage_graph': [
    'api:kb:graph:view', 'api:kb:graph:build', 'api:kb:graph:settings', 'api:kb:graph:search',
  ],
  'kb:manage_eval': [
    'api:kb:eval:list', 'api:kb:eval:run', 'api:kb:eval:delete',
    'api:kb:benchmark:list', 'api:kb:benchmark:create', 'api:kb:benchmark:delete', 'api:kb:benchmark:generate',
  ],
  'kb:manage_strategy': [
    'api:kb:strategy:list', 'api:kb:strategy:create', 'api:kb:strategy:update',
    'api:kb:strategy:delete', 'api:kb:strategy:resolve',
  ],
  'kb:version:create': ['api:kb:version:create'],
  'kb:version:publish': ['api:kb:publish:publish', 'api:kb:version:transition', 'api:kb:publish:plan:create'],
  'kb:version:revoke': ['api:kb:publish:revoke'],
  'qa:manage': ['api:kb:qa:list', 'api:kb:qa:create', 'api:kb:qa:update', 'api:kb:qa:delete', 'api:kb:qa:confirm'],
  'kb:category:create': ['api:kb:category:create'],
  'kb:category:edit': ['api:kb:category:update'],
  'kb:category:delete': ['api:kb:category:delete'],

  // ----- 应用 -----
  'app:create': ['api:app:create'],
  'app:edit': [
    'api:app:update',
    'api:app:config:basic', 'api:app:config:dialog', 'api:app:config:prompt', 'api:app:config:policy',
    'api:app:config:advanced',
    'api:app:trigger:list', 'api:app:trigger:create', 'api:app:trigger:update', 'api:app:trigger:delete',
    'api:app:kb:bind', 'api:app:skill:bind', 'api:app:tool:bind', 'api:app:mcp:bind', 'api:app:database:bind',
  ],
  'app:delete': ['api:app:delete'],
  'app:use': ['api:app:run', 'api:app:chat:session', 'api:app:chat:message', 'api:app:chat:feedback'],
  'app:publish': ['api:app:publish'],
  'app:export': ['api:app:config:export'],
  'app:import': ['api:app:config:import'],
  'app:knowledge-update': ['api:app:optimization:list', 'api:app:optimization:create', 'api:app:optimization:update', 'api:app:optimization:delete', 'api:app:optimization:apply'],
  'app:member': ['api:app:dialog-test:list', 'api:app:dialog-test:create', 'api:app:dialog-test:update', 'api:app:dialog-test:delete'],
  'app:config:basic': ['api:app:config:basic'],
  'app:config:prompt': ['api:app:config:prompt'],
  'app:config:policy': ['api:app:config:policy'],
  'workflow:create': ['api:workflow:create'],
  'workflow:edit': ['api:workflow:update', 'api:workflow:node:manage'],
  'workflow:delete': ['api:workflow:delete'],
  'workflow:publish': ['api:workflow:publish'],
  'workflow:run': ['api:workflow:execute'],
  'testcase:manage': ['api:workflow:testcase:manage'],
  'tool:create': ['api:tool:create'],
  'tool:edit': ['api:tool:update'],
  'tool:delete': ['api:tool:delete'],
  'tool:test': ['api:tool:create'],
  'mcp:create': ['api:mcp:create'],
  'mcp:edit': ['api:mcp:update', 'api:mcp:toggle'],
  'mcp:delete': ['api:mcp:delete'],
  'mcp:test': ['api:mcp:test'],
  'skill:create': ['api:skill:create', 'api:skill:file:manage'],
  'skill:edit': ['api:skill:update', 'api:skill:file:manage'],
  'skill:delete': ['api:skill:delete'],
  'skill:export': ['api:skill:export'],
  'skill:share': ['api:skill:file:manage'],
  'feedback:create': ['api:feedback:create'],
  'feedback:reply': ['api:feedback:reply'],
  'feedback:delete': ['api:feedback:delete'],

  // ----- 管理后台 -----
  'config:save': ['api:config:save'],
  'config:import': ['api:config:import'],
  'config:export': ['api:config:export'],
  'sensitive-word:create': ['api:sensitive-word:create'],
  'sensitive-word:edit': ['api:sensitive-word:update'],
  'sensitive-word:delete': ['api:sensitive-word:delete'],
  'sensitive-word:import': ['api:sensitive-word:create', 'api:sensitive-word:update'],
  'dictionary:create': ['api:dictionary:create'],
  'dictionary:edit': ['api:dictionary:update'],
  'dictionary:delete': ['api:dictionary:delete'],
  'terminology:library:create': ['api:terminology:library:create'],
  'terminology:library:delete': ['api:terminology:library:delete'],
  'terminology:term:create': ['api:terminology:term:create'],
  'terminology:term:edit': ['api:terminology:term:create'],
  'terminology:term:delete': ['api:terminology:term:delete'],
  'terminology:import': ['api:terminology:term:create'],
  'admin:role:create': ['api:role:create'],
  'admin:role:edit': ['api:role:update'],
  'admin:role:delete': ['api:role:delete'],
  'admin:role:set-default': ['api:role:set-default'],
  'admin:org:create': ['api:org:create'],
  'admin:org:edit': ['api:org:update'],
  'admin:org:delete': ['api:org:delete'],
  'admin:user:create': ['api:personnel:create'],
  'admin:user:edit': ['api:personnel:update'],
  'admin:user:role-config': ['api:personnel:assign-roles'],
  'admin:user:disable': ['api:personnel:status'],
  'model:create': ['api:model:create'],
  'model:edit': ['api:model:update'],
  'model:delete': ['api:model:delete'],
  'model:test': ['api:model:test:chat', 'api:model:test:embedding', 'api:model:test:rerank'],
  'model:import': ['api:model:import'],
  'model:export': ['api:model:export'],
  'model:toggle': ['api:model:toggle'],
}
// 注意：api-key:create/edit/delete（开放密钥）后端暂无对应 api:* 权限记录，不设联动。
// 注意：tool:test、kb:view、kb:search 等无独立 API 权限（列表/详情类接口跟随菜单勾选），不设联动。

export const MENU_PERM_TREE: MenuPermNode[] = [
  // ===== 首页 =====
  {
    title: '首页',
    keys: ['menu:home'],
    actions: ['home:workspace:manage'],
    apis: ['api:home:data', 'api:auth:userinfo'],
  },

  // ===== 知识库 =====
  {
    title: '知识库',
    keys: ['menu:knowledge'],
    children: [
      {
        title: '知识库列表',
        keys: ['menu:knowledge:list'],
        actions: [
          'kb:view', 'kb:search', 'kb:create', 'kb:edit', 'kb:delete', 'kb:upload', 'kb:export',
          'kb:acl_manage', 'kb:folder:create', 'kb:file:rename', 'kb:file:move', 'kb:file:restore',
          'kb:manage_chunks', 'kb:manage_graph', 'kb:manage_eval', 'kb:manage_strategy',
          'kb:version:create', 'kb:version:publish', 'kb:version:revoke', 'qa:manage',
          'review:approve', 'review:reject',
        ],
        apis: [
          'api:kb:list', 'api:kb:detail', 'api:kb:create', 'api:kb:update', 'api:kb:delete',
          'api:kb:acl', 'api:user:acl', 'api:kb:tag:list',
          'api:kb:folder:list', 'api:kb:folder:create', 'api:kb:folder:rename', 'api:kb:folder:delete',
          'api:kb:file:list', 'api:kb:file:upload', 'api:kb:file:update', 'api:kb:file:delete',
          'api:kb:file:process', 'api:kb:file:download', 'api:kb:file:preview', 'api:kb:file:restore',
          'api:kb:file:copy', 'api:kb:file:move',
          'api:kb:chunk:list', 'api:kb:chunk:create', 'api:kb:chunk:update', 'api:kb:chunk:delete',
          'api:kb:qa:list', 'api:kb:qa:create', 'api:kb:qa:update', 'api:kb:qa:delete', 'api:kb:qa:confirm',
          'api:kb:graph:view', 'api:kb:graph:build', 'api:kb:graph:settings', 'api:kb:graph:search',
          'api:kb:strategy:list', 'api:kb:strategy:create', 'api:kb:strategy:update', 'api:kb:strategy:delete',
          'api:kb:strategy:resolve',
          'api:kb:eval:list', 'api:kb:eval:run', 'api:kb:eval:delete',
          'api:kb:benchmark:list', 'api:kb:benchmark:create', 'api:kb:benchmark:delete', 'api:kb:benchmark:generate',
          'api:kb:version:list', 'api:kb:version:create', 'api:kb:version:transition',
          'api:kb:publish:history', 'api:kb:publish:publish', 'api:kb:publish:revoke',
          'api:kb:publish:plan:list', 'api:kb:publish:plan:create',
          'api:kb:log:list',
        ],
      },
      {
        title: '知识库分类',
        keys: ['menu:knowledge:categories'],
        actions: ['kb:category:create', 'kb:category:edit', 'kb:category:delete'],
        apis: ['api:kb:category:list', 'api:kb:category:create', 'api:kb:category:update', 'api:kb:category:delete'],
      },
      {
        title: '运营中心',
        children: [
          {
            title: '知识库分析',
            keys: ['menu:operation:kb-analytics'],
            apis: ['api:analytics:kb'],
          },
          {
            title: '检索日志分析',
            keys: ['menu:operation:retrieval-analysis'],
            apis: ['api:retrieval:log:list', 'api:retrieval:log:analysis'],
          },
        ],
      },
    ],
  },

  // ===== 应用 =====
  {
    title: '应用',
    keys: ['menu:application'],
    children: [
      {
        title: '应用中心',
        keys: ['menu:application:center', 'app:use'],
        actions: [
          'app:create', 'app:edit', 'app:delete', 'app:use', 'app:publish',
          'app:export', 'app:import', 'app:knowledge-update', 'app:member',
          'app:config:basic', 'app:config:prompt', 'app:config:policy',
        ],
        apis: [
          'api:app:list', 'api:app:detail', 'api:app:create', 'api:app:update', 'api:app:delete',
          'api:app:config:basic', 'api:app:config:dialog', 'api:app:config:prompt', 'api:app:config:policy',
          'api:app:config:export', 'api:app:config:import', 'api:app:config:advanced',
          'api:app:trigger:list', 'api:app:trigger:create', 'api:app:trigger:update', 'api:app:trigger:delete',
          'api:app:trigger:test',
          'api:app:kb:bind', 'api:app:skill:bind', 'api:app:tool:bind', 'api:app:mcp:bind', 'api:app:database:bind',
          'api:app:dialog-test:list', 'api:app:dialog-test:create', 'api:app:dialog-test:update',
          'api:app:dialog-test:delete',
          'api:app:optimization:list', 'api:app:optimization:create', 'api:app:optimization:update',
          'api:app:optimization:delete', 'api:app:optimization:apply',
          'api:app:publish',
        ],
      },
      {
        title: '应用运行',
        keys: ['menu:application:runtime', 'app:use'],
        actions: ['app:use'],
        apis: [
          'api:app:run', 'api:app:chat:session', 'api:app:chat:message', 'api:app:chat:feedback',
          'api:app:conversation:list', 'api:app:conversation:detail', 'api:app:conversation:delete',
        ],
      },
      {
        title: '工具与服务',
        children: [
          {
            title: '我的工具',
            keys: ['menu:application:my-tools'],
            actions: ['tool:create', 'tool:edit', 'tool:delete', 'tool:test'],
            apis: ['api:tool:list', 'api:tool:create', 'api:tool:update', 'api:tool:delete'],
          },
          {
            title: 'MCP管理',
            keys: ['menu:application:mcp'],
            actions: ['mcp:create', 'mcp:edit', 'mcp:delete', 'mcp:test'],
            apis: ['api:mcp:list', 'api:mcp:create', 'api:mcp:update', 'api:mcp:delete', 'api:mcp:toggle', 'api:mcp:test'],
          },
          {
            title: '技能管理',
            keys: ['menu:application:skill'],
            actions: ['skill:create', 'skill:edit', 'skill:delete', 'skill:export', 'skill:share'],
            apis: [
              'api:skill:list', 'api:skill:detail', 'api:skill:create', 'api:skill:update', 'api:skill:delete',
              'api:skill:file:manage', 'api:skill:export',
            ],
          },
          {
            title: '数据库管理',
            keys: ['menu:application:database'],
            apis: ['api:database:list', 'api:database:detail', 'api:database:create', 'api:database:update', 'api:database:delete'],
          },
        ],
      },
      {
        title: '业务流',
        keys: ['menu:application:workflow', 'workflow:edit'],
        actions: ['workflow:create', 'workflow:edit', 'workflow:delete', 'workflow:publish', 'workflow:run'],
        apis: [
          'api:workflow:list', 'api:workflow:detail', 'api:workflow:create', 'api:workflow:update',
          'api:workflow:delete', 'api:workflow:publish', 'api:workflow:execute', 'api:workflow:node:manage',
        ],
      },
      {
        title: '测试案例',
        keys: ['testcase:manage'],
        actions: ['testcase:manage'],
        apis: ['api:workflow:testcase:manage'],
      },
      {
        title: '运营中心',
        children: [
          {
            title: '反馈管理',
            keys: ['menu:operation:feedback'],
            actions: ['feedback:create', 'feedback:reply', 'feedback:delete'],
            apis: ['api:feedback:list', 'api:feedback:create', 'api:feedback:update', 'api:feedback:delete', 'api:feedback:reply'],
          },
          {
            title: '模型监控',
            keys: ['menu:operation:model-monitor'],
            apis: ['api:app:monitor'],
          },
        ],
      },
    ],
  },

  // ===== 管理 =====
  {
    title: '管理',
    keys: ['menu:admin'],
    children: [
      {
        title: '系统管理',
        keys: ['menu:admin:system'],
        children: [
          {
            title: '通用设置',
            keys: ['menu:admin:system:general', 'admin:system'],
            actions: ['admin:system', 'config:save', 'config:import', 'config:export'],
            apis: [
              'api:config:list', 'api:config:save', 'api:config:history', 'api:config:import', 'api:config:export',
              'api:security-policy:list', 'api:security-policy:create', 'api:security-policy:update',
              'api:security-policy:delete',
              'api:publish-strategy:list', 'api:publish-strategy:create', 'api:publish-strategy:update',
              'api:publish-strategy:delete',
              'api:notification:list', 'api:notification:send', 'api:notification:read', 'api:notification:delete',
            ],
          },
          {
            title: '知识库配置',
            keys: ['menu:admin:system:kb-config', 'admin:system'],
          },
          {
            title: '敏感词设置',
            keys: ['menu:admin:system:sensitive', 'admin:system'],
            actions: ['sensitive-word:create', 'sensitive-word:edit', 'sensitive-word:delete', 'sensitive-word:import'],
            apis: ['api:sensitive-word:list', 'api:sensitive-word:create', 'api:sensitive-word:update', 'api:sensitive-word:delete'],
          },
          {
            title: '字典管理',
            keys: ['menu:admin:system:dictionary', 'admin:system'],
            actions: ['dictionary:create', 'dictionary:edit', 'dictionary:delete'],
            apis: ['api:dictionary:list', 'api:dictionary:create', 'api:dictionary:update', 'api:dictionary:delete', 'api:dictionary:types'],
          },
          {
            title: '术语管理',
            keys: ['menu:admin:system:terminology', 'admin:system'],
            actions: [
              'terminology:library:create', 'terminology:library:delete',
              'terminology:term:create', 'terminology:term:edit', 'terminology:term:delete', 'terminology:import',
            ],
            apis: [
              'api:terminology:library:list', 'api:terminology:library:create', 'api:terminology:library:delete',
              'api:terminology:term:list', 'api:terminology:term:create', 'api:terminology:term:delete',
            ],
          },
        ],
      },
      {
        title: '账号权限',
        keys: ['menu:admin:account'],
        children: [
          {
            title: '角色管理',
            keys: ['menu:admin:account:roles', 'admin:role'],
            actions: ['admin:role:create', 'admin:role:edit', 'admin:role:delete', 'admin:role:set-default'],
            apis: ['api:role:list', 'api:role:detail', 'api:role:create', 'api:role:update', 'api:role:delete', 'api:role:set-default'],
          },
          {
            title: '组织管理',
            keys: ['menu:admin:account:org', 'admin:org'],
            actions: ['admin:org:create', 'admin:org:edit', 'admin:org:delete'],
            apis: [
              'api:org:tree', 'api:org:flat', 'api:org:departments', 'api:org:members',
              'api:org:create', 'api:org:update', 'api:org:delete',
            ],
          },
          {
            title: '人员管理',
            keys: ['menu:admin:account:personnel', 'admin:user'],
            actions: ['admin:user:create', 'admin:user:edit', 'admin:user:role-config', 'admin:user:disable'],
            apis: ['api:personnel:list', 'api:personnel:create', 'api:personnel:update', 'api:personnel:assign-roles', 'api:personnel:status'],
          },
          {
            title: '权限管理',
            keys: ['menu:admin:account:permissions', 'admin:role'],
            apis: ['api:permission:list', 'api:permission:tree', 'api:permission:create', 'api:permission:update', 'api:permission:delete'],
          },
        ],
      },
      {
        title: '安全审计',
        keys: ['menu:admin:audit'],
        children: [
          {
            title: '系统日志',
            keys: ['menu:admin:audit:system-log', 'admin:audit'],
            actions: ['admin:audit'],
            apis: ['api:audit:log:list'],
          },
        ],
      },
      {
        title: '开放平台',
        keys: ['menu:admin:platform'],
        children: [
          {
            title: '模型管理',
            keys: ['menu:admin:platform:model', 'admin:system'],
            actions: ['model:create', 'model:edit', 'model:delete', 'model:test', 'model:import', 'model:export', 'model:toggle'],
            apis: [
              'api:model:list', 'api:model:detail', 'api:model:create', 'api:model:update', 'api:model:delete',
              'api:model:toggle', 'api:model:test:chat', 'api:model:test:embedding', 'api:model:test:rerank',
              'api:model:import', 'api:model:export',
            ],
          },
          {
            title: '开放密钥',
            keys: ['menu:admin:platform:apikey', 'admin:system'],
            actions: ['api-key:create', 'api-key:edit', 'api-key:delete', 'kb:manage'],
          },
        ],
      },
    ],
  },
]
