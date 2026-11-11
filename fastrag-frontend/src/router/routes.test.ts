// S1 静态不变量（nav-shell TDD T1.1/T1.2）：路由表快照 + 唯一新增路由。
// 基线取自改造前提交（77 个 path/name 对，含门户与孤儿路由；提取口径 = 源码 path 行 + 其后最近的 name 行）。
import { describe, it, expect } from 'vitest'
import routes from './routes'

type Pair = [path: string, name: string | null]

// 基线：改造前 git HEAD 的全部 (path, name) 对（2026-10-26 提取）
const BASELINE: Pair[] = [
  ['/portal', null],
  ['', 'PortalHome'],
  ['manual', 'PortalManual'],
  ['changelog', 'PortalChangelog'],
  ['/login', 'Login'],
  ['/register', 'Register'],
  ['/forgot-password', 'ForgotPassword'],
  ['/403', 'Forbidden'],
  ['/', null],
  ['home', 'Home'],
  ['workspace', 'Workspace'],
  ['workspace/custom', 'CustomWorkspace'],
  ['conversation', 'ConversationCenter'],
  ['knowledge', 'Knowledge'],
  ['knowledge/categories', 'KnowledgeCategories'],
  ['knowledge/create', 'KnowledgeCreate'],
  ['knowledge/:id', 'KnowledgeDetail'],
  ['knowledge/:id/parse-strategy', 'KnowledgeParseStrategy'],
  ['knowledge/:id/edit', 'KnowledgeEdit'],
  ['knowledge/:id/debug', 'KnowledgeDebug'],
  ['knowledge/:id/api-doc', 'KnowledgeApiDoc'],
  ['knowledge-review/management', 'ReviewManagement'],
  ['knowledge/:id/chunks/:fileId', 'KnowledgeChunks'],
  ['knowledge/:id/office/:fileId', 'KnowledgeOfficeEdit'],
  ['application', 'Application'],
  ['application/prompt-templates', 'PromptTemplates'],
  ['application/my-tools', 'MyTools'],
  ['application/my-tools/create', 'HttpToolCreate'],
  ['application/my-tools/:id/edit', 'HttpToolEdit'],
  ['application/mcp-management', 'McpManagement'],
  ['application/sandbox-management', 'SandboxManagement'],
  ['application/mcp-management/create', 'McpCreate'],
  ['application/mcp-management/:id', 'McpDetail'],
  ['application/mcp-management/:id/edit', 'McpEdit'],
  ['application/skill-management', 'SkillManagement'],
  ['application/skill-management/create', 'SkillCreate'],
  ['application/database-management', 'DatabaseManagement'],
  ['application/skill-management/:slug', 'SkillDetail'],
  ['application/:id/editor', 'AppEditor'],
  ['application/:id/config', 'AppConfig'],
  ['application/:id/workflow-manage', 'WorkflowManage'],
  ['application/runtime', 'AppRuntime'],
  ['application/:id/runtime', 'AppRuntimeOld'],
  ['operation', 'Operation'],
  ['kb-analytics', 'KbAnalytics'],
  ['model-monitor', 'ModelMonitor'],
  ['feedback', 'Feedback'],
  ['qa-detail', 'QaDetail'],
  ['retrieval-analysis', 'RetrievalAnalysis'],
  ['admin', 'Admin'],
  ['system/kb-config', 'SystemKbConfig'],
  ['system/general-settings', 'GeneralSettings'],
  ['system/sandbox-settings', 'SandboxSettings'],
  ['system/sensitive-words', 'SensitiveWords'],
  ['system/dictionary', 'Dictionary'],
  ['system/terminology', 'Terminology'],
  ['account/roles', 'Roles'],
  ['account/roles/:id/permissions', 'RolePermissions'],
  ['account/organization', 'Organization'],
  ['account/personnel', 'Personnel'],
  ['permissions', 'Permissions'],
  ['platform/model-management', 'ModelManagement'],
  ['platform/api-keys', 'ApiKeys'],
  ['audit/system-log', 'SystemLog'],
  ['robot-operation', 'RobotOperation'],
  ['faq-analysis', 'FaqAnalysis'],
  ['multi-turn', 'MultiTurnAnalysis'],
  ['intent', 'IntentAnalysis'],
  ['data-mining', 'DataMining'],
  ['knowledge-review', 'KnowledgeReview'],
  ['flows', 'ReviewFlows'],
  ['flow-design', 'ReviewFlowDesign'],
  ['listeners', 'ReviewListeners'],
  ['compliance', 'ReviewCompliance'],
  ['reports', 'ReviewReports'],
  ['quality', 'ReviewQuality'],
  ['/:pathMatch(.*)*', null],
]

/** 遍历路由表收集全部 (path, name) 对（与基线提取口径一致：子路由 path 为相对段） */
function collectPairs(records: any[]): Pair[] {
  const out: Pair[] = []
  for (const r of records) {
    out.push([r.path, r.name ?? null])
    if (r.children) out.push(...collectPairs(r.children))
  }
  return out
}

describe('路由表静态不变量（nav-shell T1.1/T1.2）', () => {
  /** 新增路由白名单：notification（nav-shell）+ widget 三条（widget 链 PAGE-201~203） */
  const ADDED: Pair[] = [
    ['notification', 'Notification'],
    ['widget', 'WidgetList'],
    ['widget/create', 'WidgetCreate'],
    ['widget/:id', 'WidgetEdit'],
  ]
  const isAdded = ([p, n]: Pair) => ADDED.some(([ap, an]) => p === ap && n === an)

  it('既有路由 path/name 映射零变化（基线 77 对逐对保留 + 恰增 notification 与 widget 三对）', () => {
    const current = collectPairs(routes)
    expect(current.filter((pair) => !isAdded(pair))).toEqual(BASELINE)
    for (const added of ADDED) {
      expect(current.filter(([p, n]) => p === added[0] && n === added[1])).toHaveLength(1)
    }
    expect(current).toHaveLength(BASELINE.length + ADDED.length)
  })

  it('新增路由恰一条 /notification（AppLayout 子路由族，复用既有通知中心视图）', () => {
    const appLayout = routes.find((r: any) => r.path === '/')
    expect(appLayout).toBeTruthy()
    const notification = (appLayout!.children as any[]).filter((c) => c.path === 'notification')
    expect(notification).toHaveLength(1)
    expect(notification[0].name).toBe('Notification')
    expect(notification[0].meta?.title).toBe('通知中心')
    expect(String(notification[0].component)).toContain('notification/index')
  })

  it('widget 工作台三路由（PAGE-201~203）：AppLayout 平铺、hidden、照 skill 块形态', () => {
    const appLayout = routes.find((r: any) => r.path === '/')
    const children = (appLayout!.children as any[]).filter((c) => String(c.path).startsWith('widget'))
    expect(children.map((c) => c.name)).toEqual(['WidgetList', 'WidgetCreate', 'WidgetEdit'])
    for (const c of children) {
      expect(c.meta?.hidden).toBe(true)
    }
    expect(children[0].meta?.title).toBe('Widget')
    expect(children[1].meta?.title).toBe('新建 Widget')
    expect(children[2].meta?.title).toBe('编辑 Widget')
    expect(String(children[0].component)).toContain('widget/index')
    expect(String(children[1].component)).toContain('widget/WidgetCreate')
    expect(String(children[2].component)).toContain('widget/WidgetEdit')
  })
})
