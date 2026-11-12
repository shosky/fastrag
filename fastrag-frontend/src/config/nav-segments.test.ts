// S2 数据完整性 + 权限对齐（nav-shell TDD T2.1/T2.2/T2.6 数据面）：NavConfig 四段结构与路由表/权限映射对账。
import { describe, it, expect } from 'vitest'
import { navConfig } from './nav-segments'
import routes from '@/router/routes'
import { MENU_PERMISSION_MAP } from '@/types/auth'

/** 段内全部菜单项（修订 #15 后第四段为数据分析） */
function segmentItems(): Array<{ key: string; title: string; path: string }> {
  return navConfig.segments.flatMap((seg) => seg.items)
}

/** 下拉分组内全部菜单项 */
function dropdownItems(): Array<{ key: string; title: string; path: string }> {
  return navConfig.userMenuGroups.flatMap((sg) => sg.items)
}

/** 遍历路由表收集绝对路径 */
function flattenRoutes(records: any[], prefix = ''): string[] {
  const out: string[] = []
  for (const r of records) {
    const base = prefix.endsWith('/') ? prefix.slice(0, -1) : prefix
    const full = r.path.startsWith('/') ? r.path : base + '/' + r.path
    out.push(full)
    if (r.children) out.push(...flattenRoutes(r.children, full))
  }
  return out
}

/** 与 Sidebar.vue 相同口径：按 path 递归查 MENU_PERMISSION_MAP */
function findRequiredPerms(path: string, map = MENU_PERMISSION_MAP): string[] {
  for (const item of map) {
    if (item.path === path) return item.requiredPerms
    if (item.children) {
      const found = findRequiredPerms(path, item.children)
      if (found.length > 0) return found
    }
  }
  return []
}

describe('NavConfig 数据完整性（T2.1）', () => {
  it('顶置主页 + 四段顺序 对话/构建/资源/数据分析（修订 #15）', () => {
    expect(navConfig.home.title).toBe('主页')
    expect(navConfig.home.path).toBe('/home')
    expect(navConfig.segments.map((s) => s.title)).toEqual(['对话', '构建', '资源', '数据分析'])
    expect(navConfig.segments.map((s) => s.key)).toEqual(['conversation', 'build', 'resource', 'analytics'])
  })

  it('菜单项总数：侧边栏 13（1+1+9+2，检索日志分析并入知识库分析、反馈管理下线）+ 下拉（门户资源 2 + 管理 12）= 27', () => {
    expect(segmentItems()).toHaveLength(13)
    expect(navConfig.segments[0].items).toHaveLength(1)
    expect(navConfig.segments[1].items).toHaveLength(1)
    expect(navConfig.segments[2].items).toHaveLength(9)
    expect(navConfig.segments[3].items).toHaveLength(2)
    expect(navConfig.segments[3].title).toBe('数据分析')
    expect(dropdownItems()).toHaveLength(14)
  })

  it('资源段末项 Widget（PAGE-201）：key/path/icon 固定', () => {
    const resourceItems = navConfig.segments[2].items
    const widget = resourceItems[resourceItems.length - 1]
    expect(widget).toEqual({ key: 'widget', title: 'Widget', path: '/widget', icon: 'Postcard' })
  })

  it('管理域在用户下拉「管理」分组（修订 #15/#16/#17：门户资源在前、管理为级联）：结构与权限门禁路径', () => {
    expect(navConfig.userMenuGroups).toHaveLength(2)
    expect(navConfig.userMenuGroups[1].title).toBe('管理')
    expect(navConfig.userMenuGroups[1].cascade).toBe(true)
    expect(navConfig.userMenuGroups[0].cascade).toBeUndefined()
    expect(navConfig.userMenuGroups[1].items.map((i) => i.path)).toEqual([
      '/admin/system/general-settings',
      '/admin/system/sandbox-settings',
      '/admin/system/kb-config',
      '/admin/system/sensitive-words',
      '/admin/system/dictionary',
      '/admin/system/terminology',
      '/admin/account/roles',
      '/admin/account/organization',
      '/admin/account/personnel',
      '/admin/permissions',
      '/admin/audit/system-log',
      '/admin/platform/api-keys',
    ])
  })

  it('每个菜单项 path 均存在于路由表（侧边栏 + 下拉）', () => {
    const routePaths = new Set(flattenRoutes(routes as any[]))
    const missing = [navConfig.home.path, ...segmentItems().map((i) => i.path), ...dropdownItems().map((i) => i.path)].filter(
      (p) => !routePaths.has(p)
    )
    expect(missing).toEqual([])
  })
})

describe('菜单权限依据对齐（T2.2，权限键零改动下的映射一致性）', () => {
  it('关键哨兵：模型管理/知识库分类/知识库分析 解析出预期权限键', () => {
    expect(findRequiredPerms('/admin/platform/model-management')[0]).toBe('menu:admin:platform:model')
    expect(findRequiredPerms('/knowledge/categories')[0]).toBe('menu:knowledge:categories')
    expect(findRequiredPerms('/operation/kb-analytics')[0]).toBe('menu:operation:kb-analytics')
  })

  it('无权限键项（会话中心/沙箱配置/提示词/沙箱设置/Widget）在映射中无记录，登录恒显', () => {
    for (const p of ['/conversation', '/application/sandbox-management', '/application/prompt-templates', '/admin/system/sandbox-settings', '/widget']) {
      expect(findRequiredPerms(p)).toEqual([])
    }
  })

  it('其余菜单项均可从映射解析出首个权限键（含下拉管理分组项，无空解析）', () => {
    const alwaysVisible = new Set(['/conversation', '/application/sandbox-management', '/application/prompt-templates', '/admin/system/sandbox-settings', '/portal/manual', '/portal/changelog', '/widget'])
    for (const item of [...segmentItems(), ...dropdownItems()]) {
      if (alwaysVisible.has(item.path)) continue
      const perms = findRequiredPerms(item.path)
      expect(perms.length).toBeGreaterThan(0)
    }
  })
})

describe('不出现项（T2.6 数据面，修订 #15）', () => {
  it('侧边栏配置不含 业务流/测试案例/应用运行/运营中心/知识库分类；管理域不在段内', () => {
    const segmentsJson = JSON.stringify(navConfig.segments)
    for (const banned of ['业务流', '测试案例', '应用运行', '运营中心', '通用设置', '角色管理', '知识库分类']) {
      expect(segmentsJson).not.toContain(banned)
    }
    // 管理域仅存在于用户下拉分组
    expect(JSON.stringify(navConfig.userMenuGroups)).toContain('通用设置')
    expect(JSON.stringify(navConfig.segments)).toContain('数据分析')
  })
})

describe('用户下拉数据（T5.1 数据面，修订 #16 语义分组）', () => {
  it('三段式：用户信息单项 + 门户资源/管理分组 + 底部独立退出', () => {
    expect(navConfig.userMenuProfile.key).toBe('profile')
    expect(navConfig.userMenuProfile.title).toBe('用户信息')

    expect(navConfig.userMenuGroups.map((g) => g.title)).toEqual(['门户资源', '管理'])
    const manual = navConfig.userMenuGroups[0].items.find((i) => i.key === 'manual')
    const changelog = navConfig.userMenuGroups[0].items.find((i) => i.key === 'changelog')
    expect(manual?.path).toBe('/portal/manual')
    expect(changelog?.path).toBe('/portal/changelog')

    expect(navConfig.userMenuLogout.key).toBe('logout')
    expect(navConfig.userMenuLogout.title).toBe('退出系统')
    expect(navConfig.userMenuLogout.path).toBeUndefined()
  })
})
