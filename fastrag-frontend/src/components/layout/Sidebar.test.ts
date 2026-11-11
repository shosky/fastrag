// @vitest-environment jsdom
// S2/S3 侧边栏组件（nav-shell TDD T2.3-T2.6、T3.1-T3.6）：
// 四段渲染、权限过滤、空段隐标题、不出现项、高亮回溯。用户入口见 UserMenu.test.ts（修订 #14：移至内容卡头右上角）。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import { navConfig } from '@/config/nav-segments'
import { useUserStore } from '@/stores/user'

import Sidebar from './Sidebar.vue'

/** 用 navConfig 全部路径 + 深链占位构建路由（真实 router 实例，不 mock vue-router） */
function buildRouter() {
  const paths = [
    navConfig.home.path,
    ...navConfig.segments.flatMap((seg) => seg.items.map((i) => i.path)),
    '/portal',
    '/login',
    // 高亮回溯深链占位
    '/knowledge/:id',
    '/application/:id/editor',
  ]
  return createRouter({
    history: createMemoryHistory(),
    routes: paths.map((p) => ({ path: p, component: { template: '<div />' } })),
  })
}

function mountSidebar(userInfo: Record<string, unknown>) {
  const pinia = createPinia()
  const router = buildRouter()
  router.push('/home')
  const store = useUserStore(pinia)
  store.setUserInfo(userInfo as any)
  const wrapper = mount(Sidebar, {
    global: { plugins: [pinia, router, ElementPlus] },
  })
  return { wrapper, router, store }
}

const ADMIN = { username: 'admin', realName: '王翔', roles: ['super_admin'], permissions: ['*'] }
const KB_ONLY = {
  username: 'kbuser',
  realName: '',
  roles: ['kb_user'],
  permissions: ['menu:knowledge:list', 'menu:knowledge:categories'],
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

describe('四段渲染与权限过滤（T2.3/T2.4/T2.5/T2.6）', () => {
  it('全权限账号：四段全渲染，侧边栏菜单 15 + 主页 = 16 个链接（修订 #15/#18 + widget 链资源段末项）', async () => {
    const { wrapper } = mountSidebar(ADMIN)
    await flushPromises()
    expect(wrapper.findAll('.nav-item')).toHaveLength(16)
    const text = wrapper.text()
    for (const t of ['主页', '对话', '会话中心', '构建', '应用中心', '资源', '知识库', '模型管理', '提示词', 'Widget', '数据分析', '知识库分析']) {
      expect(text).toContain(t)
    }
    for (const banned of ['通用设置', '角色管理', '开放密钥']) {
      expect(text).not.toContain(banned)
    }
    expect(text).toContain('收起菜单')
  })

  it('受限账号（仅知识库列表/分类权限）：构建段隐标题；恒显项（会话中心/沙箱配置/提示词）保留', async () => {
    const { wrapper } = mountSidebar(KB_ONLY)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('对话')
    expect(text).toContain('会话中心')
    expect(text).not.toContain('构建')
    expect(text).not.toContain('应用中心')
    expect(text).toContain('资源')
    expect(text).not.toContain('知识库分类')
    expect(text).not.toContain('我的工具')
    expect(text).not.toContain('模型管理')
    expect(text).not.toContain('数据分析')
    expect(text).toContain('沙箱配置')
    expect(text).toContain('提示词')
    expect(text).not.toContain('沙箱设置')
  })

  it('受限账号可见项精确集合：会话中心/沙箱配置/提示词/Widget（主页受 menu:home 门禁；无权限键项恒显）', async () => {
    const { wrapper } = mountSidebar(KB_ONLY)
    await flushPromises()
    const items = wrapper.findAll('.nav-item').map((n) => n.text())
    expect(items).toEqual(['会话中心', '沙箱配置', '提示词', 'Widget'])
  })

  it('不出现项：渲染树不含 业务流/测试案例/应用运行/运营中心', async () => {
    const { wrapper } = mountSidebar(ADMIN)
    await flushPromises()
    const text = wrapper.text()
    for (const banned of ['业务流', '测试案例', '应用运行', '运营中心']) {
      expect(text).not.toContain(banned)
    }
  })
})

describe('高亮回溯（T3.1-T3.6）', () => {
  const CASES: Array<[string, string]> = [
    ['/home', '主页'],
    ['/knowledge/kb-1', '知识库'],
    ['/application/app-1/editor', '应用中心'],
    ['/admin/platform/model-management', '模型管理'],
    ['/knowledge/categories', '知识库'],
  ]

  for (const [path, expected] of CASES) {
    it(`${path} 激活 → 高亮「${expected}」`, async () => {
      const { wrapper, router } = mountSidebar(ADMIN)
      await router.isReady()
      await router.push(path)
      await flushPromises()
      const active = wrapper.findAll('.nav-item.active')
      expect(active).toHaveLength(1)
      expect(active[0].text()).toBe(expected)
    })
  }

  it('/operation/kb-analytics 激活 → 高亮「知识库分析」（修订 #15：数据分析回栏）', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    await router.push('/operation/kb-analytics')
    await flushPromises()
    const active = wrapper.findAll('.nav-item.active')
    expect(active).toHaveLength(1)
    expect(active[0].text()).toBe('知识库分析')
  })

  it('/admin/system/general-settings 激活 → 侧边栏无高亮项（管理域在用户下拉，修订 #15）', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    await router.push('/admin/system/general-settings')
    await flushPromises()
    expect(wrapper.findAll('.nav-item.active')).toHaveLength(0)
  })
})

describe('折叠态（修订 #15）', () => {
  it('默认展开；toggleCollapsed 后进入图标态并持久化 localStorage', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.collapsed).toBe(false)
    expect(wrapper.find('.nav-sidebar.collapsed').exists()).toBe(false)
    expect(wrapper.text()).toContain('收起菜单')

    vm.toggleCollapsed()
    await flushPromises()
    expect(vm.collapsed).toBe(true)
    expect(wrapper.find('.nav-sidebar.collapsed').exists()).toBe(true)
    expect(wrapper.find('.nav-collapse').exists()).toBe(true)
    // 图标态：无菜单文字、无段标题
    expect(wrapper.findAll('.nav-text')).toHaveLength(0)
    expect(wrapper.find('.nav-segment-title').exists()).toBe(false)
    expect(localStorage.getItem('ais_nav.sidebarCollapsed')).toBe('true')
  })

  it('localStorage 预置折叠 → 挂载即图标态；再次 toggle 恢复展开', async () => {
    localStorage.setItem('ais_nav.sidebarCollapsed', 'true')
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.collapsed).toBe(true)
    expect(wrapper.findAll('.nav-text')).toHaveLength(0)

    vm.toggleCollapsed()
    await flushPromises()
    expect(vm.collapsed).toBe(false)
    expect(wrapper.findAll('.nav-text').length).toBeGreaterThan(0)
    expect(localStorage.getItem('ais_nav.sidebarCollapsed')).toBe('false')
  })

  it('折叠开关点击交互：底部按钮触发 toggle', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    await flushPromises()
    await wrapper.find('.nav-collapse').trigger('click')
    await flushPromises()
    expect((wrapper.vm as any).collapsed).toBe(true)
  })
})

describe('折叠态悬停浮出（修订 #16）', () => {
  it('折叠后 mouseenter 侧栏 → 浮出完整菜单；mouseleave 收回', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    const vm = wrapper.vm as any
    vm.toggleCollapsed()
    await flushPromises()

    await wrapper.trigger('mouseenter')
    expect(vm.flyoutVisible).toBe(true)
    const flyout = wrapper.find('.nav-flyout')
    expect(flyout.exists()).toBe(true)
    expect(flyout.text()).toContain('数据分析')
    expect(flyout.text()).toContain('知识库分析')
    expect(flyout.findAll('.nav-item').length).toBeGreaterThan(0)

    await wrapper.trigger('mouseleave')
    expect(vm.flyoutVisible).toBe(false)
    expect(wrapper.find('.nav-flyout').exists()).toBe(false)
  })

  it('展开态悬停不出浮出层', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    await flushPromises()
    await wrapper.trigger('mouseenter')
    expect((wrapper.vm as any).flyoutVisible).toBe(false)
    expect(wrapper.find('.nav-flyout').exists()).toBe(false)
  })

  it('点击浮出层菜单项 → 导航并收回', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await router.isReady()
    const vm = wrapper.vm as any
    vm.toggleCollapsed()
    await wrapper.trigger('mouseenter')
    await flushPromises()

    await wrapper.find('.nav-flyout .nav-item').trigger('click')
    await flushPromises()
    expect(vm.flyoutVisible).toBe(false)
    expect(router.currentRoute.value.path).toBe('/home')
  })
})

describe('壳回归锚点（T5.5）', () => {
  it('侧栏 logo 点击回门户 /portal', async () => {
    const { wrapper, router } = mountSidebar(ADMIN)
    await flushPromises()
    await wrapper.find('.nav-logo').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/portal')
  })
})
