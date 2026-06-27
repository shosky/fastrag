import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    redirect: '/home',
    children: [
      // ===== 平台门户 =====
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '主页', icon: 'HomeFilled' },
      },
      {
        path: 'workspace',
        name: 'Workspace',
        component: () => import('@/views/workspace/index.vue'),
        meta: { title: '工作台', icon: 'Monitor' },
      },
      {
        path: 'workspace/custom',
        name: 'CustomWorkspace',
        component: () => import('@/views/workspace/custom-workspace.vue'),
        meta: { title: '自定义工作台', hidden: true },
      },

      // ===== 知识管理 =====
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识库', icon: 'Collection' },
      },
      {
        path: 'knowledge/create',
        name: 'KnowledgeCreate',
        component: () => import('@/views/knowledge/create.vue'),
        meta: { title: '创建知识库', hidden: true },
      },
      {
        path: 'knowledge/:id',
        name: 'KnowledgeDetail',
        component: () => import('@/views/knowledge/detail/index.vue'),
        meta: { title: '知识库详情', hidden: true },
      },
      {
        path: 'knowledge/:id/parse-strategy',
        name: 'KnowledgeParseStrategy',
        component: () => import('@/views/knowledge/detail/parse-strategy.vue'),
        meta: { title: '解析策略管理', hidden: true },
      },
      {
        path: 'knowledge/:id/edit',
        name: 'KnowledgeEdit',
        component: () => import('@/views/knowledge/edit.vue'),
        meta: { title: '编辑知识库', hidden: true },
      },
      {
        path: 'knowledge/:id/debug',
        name: 'KnowledgeDebug',
        component: () => import('@/views/knowledge/detail/debug.vue'),
        meta: { title: '知识库调试', hidden: true },
      },
      {
        path: 'knowledge/:id/api-doc',
        name: 'KnowledgeApiDoc',
        component: () => import('@/views/knowledge/detail/api-doc.vue'),
        meta: { title: 'API 文档', hidden: true },
      },
      {
        path: 'knowledge/:id/chunks/:fileId',
        name: 'KnowledgeChunks',
        component: () => import('@/views/knowledge/detail/chunks.vue'),
        meta: { title: '分片管理', hidden: true },
      },
      {
        path: 'knowledge/categories',
        name: 'KnowledgeCategories',
        component: () => import('@/views/knowledge/categories.vue'),
        meta: { title: '知识库分类' },
      },
      {
        path: 'knowledge/tags',
        name: 'KnowledgeTags',
        component: () => import('@/views/knowledge/tags.vue'),
        meta: { title: '知识库标签' },
      },
      // ===== 业务流 =====
      // ===== 应用与运营 =====
      {
        path: 'application',
        name: 'Application',
        component: () => import('@/views/application/index.vue'),
        meta: { title: '应用中心', icon: 'Grid', roles: ['super_admin', 'kb_admin', 'kb_user', 'readonly'] as const },
      },
      {
        path: 'application/prompt-templates',
        name: 'PromptTemplates',
        component: () => import('@/views/answer-kb/templates.vue'),
        meta: { title: 'Prompt模板', roles: ['super_admin', 'kb_admin', 'kb_user'] as const },
      },
      {
        path: 'application/my-tools',
        name: 'MyTools',
        component: () => import('@/views/application/my-tools.vue'),
        meta: { title: '我的工具', hidden: true },
      },
      {
        path: 'application/my-tools/create',
        name: 'HttpToolCreate',
        component: () => import('@/views/application/tool/create.vue'),
        meta: { title: '创建 HTTP 工具', hidden: true },
      },
      {
        path: 'application/my-tools/:id/edit',
        name: 'HttpToolEdit',
        component: () => import('@/views/application/tool/edit.vue'),
        meta: { title: '编辑 HTTP 工具', hidden: true },
      },
      {
        path: 'application/mcp-management',
        name: 'McpManagement',
        component: () => import('@/views/application/mcp-services.vue'),
        meta: { title: 'MCP管理', hidden: true },
      },
      {
        path: 'application/mcp-management/create',
        name: 'McpCreate',
        component: () => import('@/views/application/mcp/create.vue'),
        meta: { title: '添加 MCP 服务', hidden: true },
      },
      {
        path: 'application/mcp-management/:id',
        name: 'McpDetail',
        component: () => import('@/views/application/mcp/detail.vue'),
        meta: { title: 'MCP 服务详情', hidden: true },
      },
      {
        path: 'application/mcp-management/:id/edit',
        name: 'McpEdit',
        component: () => import('@/views/application/mcp/edit.vue'),
        meta: { title: '编辑 MCP 服务', hidden: true },
      },
      {
        path: 'application/skill-management',
        name: 'SkillManagement',
        component: () => import('@/views/application/skill-management.vue'),
        meta: { title: '技能管理', hidden: true },
      },
      {
        path: 'application/skill-management/create',
        name: 'SkillCreate',
        component: () => import('@/views/application/skill/create.vue'),
        meta: { title: '创建技能', hidden: true },
      },
      {
        path: 'application/skill-management/:id/edit',
        name: 'SkillEdit',
        component: () => import('@/views/application/skill/edit.vue'),
        meta: { title: '编辑技能', hidden: true },
      },
      {
        path: 'application/:id/editor',
        name: 'AppEditor',
        component: () => import('@/views/application/editor/index.vue'),
        meta: { title: '应用编辑', hidden: true },
      },
      {
        path: 'application/:id/runtime',
        name: 'AppRuntime',
        component: () => import('@/views/application/runtime.vue'),
        meta: { title: '应用运行', hidden: true },
      },
      {
        path: 'operation',
        name: 'Operation',
        redirect: '/operation/kb-analytics',
        meta: { title: '运营中心', icon: 'TrendCharts' },
        children: [
          {
            path: 'kb-analytics',
            name: 'KbAnalytics',
            component: () => import('@/views/operation/kb-analytics.vue'),
            meta: { title: '知识资产分析' },
          },
          {
            path: 'model-monitor',
            name: 'ModelMonitor',
            component: () => import('@/views/operation/model-monitor.vue'),
            meta: { title: '模型监控分析' },
          },
          {
            path: 'feedback',
            name: 'Feedback',
            component: () => import('@/views/operation/feedback.vue'),
            meta: { title: '问答反馈分析' },
          },
          {
            path: 'qa-detail',
            name: 'QaDetail',
            component: () => import('@/views/operation/qa-detail.vue'),
            meta: { title: '问答明细' },
          },
        ],
      },

      // ===== 管理中心（需要管理员角色） =====
      {
        path: 'admin',
        name: 'Admin',
        redirect: '/admin/index',
        meta: { title: '管理中心', icon: 'Tools', roles: ['super_admin', 'kb_admin'] as const },
        children: [
          {
            path: 'index',
            name: 'AdminIndex',
            component: () => import('@/views/admin/index.vue'),
            meta: { title: '管理中心概览', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'system/kb-config',
            name: 'SystemKbConfig',
            component: () => import('@/views/admin/system/kb-config.vue'),
            meta: { title: '知识库配置', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'system/general-settings',
            name: 'GeneralSettings',
            component: () => import('@/views/admin/system/general-settings.vue'),
            meta: { title: '通用设置', roles: ['super_admin'] as const },
          },
          {
            path: 'system/sensitive-words',
            name: 'SensitiveWords',
            component: () => import('@/views/admin/system/sensitive-words.vue'),
            meta: { title: '敏感词设置', roles: ['super_admin'] as const },
          },
          {
            path: 'system/dictionary',
            name: 'Dictionary',
            component: () => import('@/views/admin/system/dictionary.vue'),
            meta: { title: '字典管理', roles: ['super_admin'] as const },
          },
          {
            path: 'system/terminology',
            name: 'Terminology',
            component: () => import('@/views/admin/system/terminology.vue'),
            meta: { title: '术语管理', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'system/query-rules',
            name: 'QueryRules',
            component: () => import('@/views/admin/system/query-rules.vue'),
            meta: { title: '查询规则', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'account/roles',
            name: 'Roles',
            component: () => import('@/views/admin/account/roles.vue'),
            meta: { title: '角色管理', roles: ['super_admin'] as const },
          },
          {
            path: 'account/roles/:id/permissions',
            name: 'RolePermissions',
            component: () => import('@/views/admin/account/role-permissions.vue'),
            meta: { title: '角色权限配置', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'account/organization',
            name: 'Organization',
            component: () => import('@/views/admin/account/organization.vue'),
            meta: { title: '组织管理', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'account/team',
            name: 'Team',
            component: () => import('@/views/admin/account/team.vue'),
            meta: { title: '团队管理', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'account/personnel',
            name: 'Personnel',
            component: () => import('@/views/admin/account/personnel.vue'),
            meta: { title: '人员管理', roles: ['super_admin'] as const },
          },
          {
            path: 'permissions',
            name: 'Permissions',
            component: () => import('@/views/admin/permissions/index.vue'),
            meta: { title: '权限管理', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'audit/system-log',
            name: 'SystemLog',
            component: () => import('@/views/admin/audit/system-log.vue'),
            meta: { title: '系统日志', roles: ['super_admin'] as const },
          },
          {
            path: 'audit/device-login',
            name: 'DeviceLogin',
            component: () => import('@/views/admin/audit/device-login.vue'),
            meta: { title: '设备登录分析', roles: ['super_admin'] as const },
          },
          {
            path: 'audit/login-security',
            name: 'LoginSecurity',
            component: () => import('@/views/admin/audit/login-security.vue'),
            meta: { title: '登录安全配置', roles: ['super_admin'] as const },
          },
          {
            path: 'audit/review-center',
            name: 'ReviewCenter',
            component: () => import('@/views/admin/audit/review-center.vue'),
            meta: { title: '审核中心', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'content/notification',
            name: 'Notification',
            component: () => import('@/views/admin/content/notification.vue'),
            meta: { title: '通知管理', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'content/tags',
            name: 'Tags',
            component: () => import('@/views/admin/content/tags.vue'),
            meta: { title: '标签管理', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'content/prompts',
            name: 'Prompts',
            component: () => import('@/views/admin/content/prompts.vue'),
            meta: { title: '提示词', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'content/templates',
            name: 'Templates',
            component: () => import('@/views/admin/content/templates.vue'),
            meta: { title: '文档模板', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'content/download',
            name: 'Download',
            component: () => import('@/views/admin/content/download.vue'),
            meta: { title: '下载中心', roles: ['super_admin', 'kb_admin'] as const },
          },
          {
            path: 'platform/third-party',
            name: 'ThirdParty',
            component: () => import('@/views/admin/platform/third-party.vue'),
            meta: { title: '三方平台', roles: ['super_admin'] as const },
          },
          {
            path: 'platform/model-management',
            name: 'ModelManagement',
            component: () => import('@/views/admin/platform/model-management.vue'),
            meta: { title: '模型管理', roles: ['super_admin'] as const },
          },
          {
            path: 'platform/api-keys',
            name: 'ApiKeys',
            component: () => import('@/views/admin/platform/api-keys.vue'),
            meta: { title: '开放密钥', roles: ['super_admin'] as const },
          },
        ],
      },

      // ===== 发布与评估（仅保留机器人发布） =====
      {
        path: 'publish-eval/release',
        name: 'RobotRelease',
        component: () => import('@/views/publish-eval/release.vue'),
        meta: { title: '机器人发布' },
      },

      // ===== 机器人运营 =====
      {
        path: 'robot-operation',
        name: 'RobotOperation',
        redirect: '/robot-operation/faq-analysis',
        meta: { title: '运营中心', icon: 'DataLine' },
        children: [
          { path: 'faq-analysis', name: 'FaqAnalysis', component: () => import('@/views/robot-operation/faq-analysis.vue'), meta: { title: 'FAQ知识分析' } },
          { path: 'multi-turn', name: 'MultiTurnAnalysis', component: () => import('@/views/robot-operation/multi-turn.vue'), meta: { title: '多轮对话分析' } },
          { path: 'intent', name: 'IntentAnalysis', component: () => import('@/views/robot-operation/intent.vue'), meta: { title: '意图知识分析' } },
        ],
      },

      // ===== 知识审核管理 =====
      {
        path: 'knowledge-review',
        name: 'KnowledgeReview',
        redirect: '/knowledge-review/flows',
        meta: { title: '知识审核', icon: 'Checked' },
        children: [
          { path: 'flows', name: 'ReviewFlows', component: () => import('@/views/knowledge-review/flows.vue'), meta: { title: '审核流程管理' } },
          { path: 'flow-design', name: 'ReviewFlowDesign', component: () => import('@/views/knowledge-review/flow-design.vue'), meta: { title: '审核流程设计' } },
          { path: 'listeners', name: 'ReviewListeners', component: () => import('@/views/knowledge-review/listeners.vue'), meta: { title: '监听管理' } },
          { path: 'compliance', name: 'ReviewCompliance', component: () => import('@/views/knowledge-review/compliance.vue'), meta: { title: '合规性检查' } },
          { path: 'reports', name: 'ReviewReports', component: () => import('@/views/knowledge-review/reports.vue'), meta: { title: '审核报告' } },
          { path: 'quality', name: 'ReviewQuality', component: () => import('@/views/knowledge-review/quality.vue'), meta: { title: '质量评估' } },
        ],
      },

      // ===== 插件与数据库管理 =====
      {
        path: 'plugin-db',
        name: 'PluginDb',
        redirect: '/plugin-db/plugins',
        meta: { title: '插件与数据库', icon: 'Connection' },
        children: [
          { path: 'plugins', name: 'PluginManagement', component: () => import('@/views/plugin-db/plugins.vue'), meta: { title: '插件管理' } },
          { path: 'databases', name: 'DatabaseManagement', component: () => import('@/views/plugin-db/databases.vue'), meta: { title: '数据库管理' } },
        ],
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/home',
  },
]

export default routes
