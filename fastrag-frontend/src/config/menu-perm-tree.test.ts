// S1 静态不变量（nav-shell TDD T1.3/T1.4/T1.5）：权限树分组重排后键集冻结、通知零注册、四段分组。
import { describe, it, expect } from 'vitest'
import { MENU_PERM_TREE } from './menu-perm-tree'

// 基线：改造前 git HEAD 的 MENU_PERM_TREE 全部 keys 并集（41 个，2026-10-26 提取）
const BASELINE_KEYS = [
  'admin:audit',
  'admin:org',
  'admin:role',
  'admin:system',
  'admin:user',
  'app:use',
  'menu:admin',
  'menu:admin:account',
  'menu:admin:account:org',
  'menu:admin:account:permissions',
  'menu:admin:account:personnel',
  'menu:admin:account:roles',
  'menu:admin:audit',
  'menu:admin:audit:system-log',
  'menu:admin:platform',
  'menu:admin:platform:apikey',
  'menu:admin:platform:model',
  'menu:admin:system',
  'menu:admin:system:dictionary',
  'menu:admin:system:general',
  'menu:admin:system:kb-config',
  'menu:admin:system:sensitive',
  'menu:admin:system:terminology',
  'menu:application',
  'menu:application:center',
  'menu:application:database',
  'menu:application:mcp',
  'menu:application:my-tools',
  'menu:application:runtime',
  'menu:application:skill',
  'menu:application:workflow',
  'menu:home',
  'menu:knowledge',
  'menu:knowledge:categories',
  'menu:knowledge:list',
  'menu:operation:kb-analytics',
  'menu:operation:model-monitor',
  'testcase:manage',
  'workflow:edit',
]

/** 递归收集树中全部 keys（去重——同一权限键可挂多个页面节点） */
function collectKeys(nodes: any[]): string[] {
  const set = new Set<string>()
  for (const n of nodes) {
    if (n.keys) for (const k of n.keys) set.add(k)
    if (n.children) for (const k of collectKeys(n.children)) set.add(k)
  }
  return [...set].sort()
}

/** 递归收集带 keys 的节点 (title, keys) */
function collectKeyedNodes(nodes: any[]): Array<{ title: string; keys: string[] }> {
  const out: Array<{ title: string; keys: string[] }> = []
  for (const n of nodes) {
    if (n.keys?.length) out.push({ title: n.title, keys: n.keys })
    if (n.children) out.push(...collectKeyedNodes(n.children))
  }
  return out
}

describe('权限树静态不变量（nav-shell T1.3-T1.5）', () => {
  it('权限键全集与改造前基线完全一致（键集冻结，无增无减）', () => {
    const keys = collectKeys(MENU_PERM_TREE as any[]).sort()
    expect(keys).toEqual([...BASELINE_KEYS].sort())
  })

  it('页面节点键绑定冻结：模型管理/知识库列表/术语管理的 keys 与基线一致', () => {
    const keyed = collectKeyedNodes(MENU_PERM_TREE as any[])
    const byTitle = new Map(keyed.map((n) => [n.title, n.keys]))
    expect(byTitle.get('模型管理')).toEqual(['menu:admin:platform:model', 'admin:system'])
    expect(byTitle.get('知识库列表')).toEqual(['menu:knowledge:list'])
    expect(byTitle.get('术语管理')).toEqual(['menu:admin:system:terminology', 'admin:system'])
  })

  it('通知中心零注册：树中不出现通知中心路由与标题', () => {
    expect(JSON.stringify(MENU_PERM_TREE)).not.toContain('/notification')
    expect(JSON.stringify(MENU_PERM_TREE)).not.toContain('通知中心')
  })

  it('分组节点标题随导航四段重排：主页/构建/资源/管理 + 数据分析子分组', () => {
    const topTitles = (MENU_PERM_TREE as any[]).map((n) => n.title)
    expect(topTitles).toEqual(['主页', '构建', '资源', '管理'])

    const admin = (MENU_PERM_TREE as any[]).find((n) => n.title === '管理')
    const childTitles = (admin.children as any[]).map((n) => n.title)
    expect(childTitles).toContain('数据分析')
    const dataAnalysis = admin.children.find((n: any) => n.title === '数据分析')
    const daTitles = (dataAnalysis.children as any[]).map((n: any) => n.title)
    // 2026-11-12：检索日志分析并入知识库分析（tab），反馈管理模块下线
    expect(daTitles).toEqual(['知识库分析', '模型监控'])
  })

  it('资源段包含自管理域移入的模型管理；旧「运营中心」分组名不再出现', () => {
    const resource = (MENU_PERM_TREE as any[]).find((n) => n.title === '资源')
    const titles = JSON.stringify((resource.children as any[]).map((n: any) => n.title))
    expect(titles).toContain('模型管理')
    expect(JSON.stringify(MENU_PERM_TREE)).not.toContain('运营中心')
    expect(JSON.stringify(MENU_PERM_TREE)).not.toContain('工具与服务')
  })
})
