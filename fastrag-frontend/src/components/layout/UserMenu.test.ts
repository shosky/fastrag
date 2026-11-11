// @vitest-environment jsdom
// S5 用户入口（nav-shell 修订 #14：内容卡头右上角）——下拉渲染、分组权限过滤、动作流（T5.1-T5.4）。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import { navConfig } from '@/config/nav-segments'
import { useUserStore } from '@/stores/user'

const { getOrgFlat, logout } = vi.hoisted(() => ({
  getOrgFlat: vi.fn(),
  logout: vi.fn(() => Promise.resolve()),
}))

vi.mock('@/api', () => ({ getOrgFlat, logout }))

const confirmMock = vi.hoisted(() => ({ confirm: vi.fn() }))
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => confirmMock,
}))

import UserMenu from './UserMenu.vue'

function buildRouter() {
  const paths = [
    '/home',
    '/portal/manual',
    '/portal/changelog',
    '/login',
    '/operation/kb-analytics',
    '/operation/retrieval-analysis',
    '/operation/feedback',
    '/operation/model-monitor',
    '/admin/system/general-settings',
    '/admin/account/roles',
  ]
  return createRouter({
    history: createMemoryHistory(),
    routes: paths.map((p) => ({ path: p, component: { template: '<div />' } })),
  })
}

function mountUserMenu(userInfo: Record<string, unknown>) {
  const pinia = createPinia()
  const router = buildRouter()
  router.push('/home')
  const store = useUserStore(pinia)
  store.setUserInfo(userInfo as any)
  const wrapper = mount(UserMenu, {
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
  confirmMock.confirm.mockResolvedValue(true)
  getOrgFlat.mockResolvedValue([])
  localStorage.clear()
})

describe('下拉渲染（T5.1 数据面 + 修订 #14）', () => {
  it('头像 chip 渲染（姓名首字），位于组件根', async () => {
    const { wrapper } = mountUserMenu(ADMIN)
    await flushPromises()
    expect(wrapper.find('.avatar-chip').exists()).toBe(true)
    expect(wrapper.find('.avatar-circle').text()).toBe('王')
  })

  it('受控展开：头部（姓名+@用户名）+ 固定四项 + 数据分析分组渲染于 body', async () => {
    const { wrapper } = mountUserMenu(ADMIN)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.userMenuVisible = true
    await flushPromises()

    // 主下拉体（.user-menu-popover）内断言；级联子面板是独立 popper（.admin-submenu-popover），不混入
    const main = document.querySelector('.user-menu-popover')
    expect(main).toBeTruthy()
    const mainText = main!.textContent || ''
    expect(mainText).toContain('王翔')
    expect(mainText).toContain('@admin')
    expect(mainText).toContain('用户信息')
    expect(mainText).toContain('门户资源')
    expect(mainText).toContain('操作手册')
    expect(mainText).toContain('产品动态')
    expect(mainText).toContain('管理')
    // 管理分组为级联子菜单行（修订 #17）：主下拉体不平铺管理功能项
    expect(mainText).not.toContain('通用设置')
    expect(mainText).not.toContain('角色管理')
    // 退出系统置底：主下拉体内最后一个 user-menu__item（修订 #16）
    const items = Array.from(main!.querySelectorAll('.user-menu__item')).map((el) => el.textContent?.trim())
    expect(items[items.length - 1]).toBe('退出系统')
  })

  it('下拉语义分组（修订 #16）：门户资源 + 管理；权限过滤各组独立', async () => {
    const admin = mountUserMenu(ADMIN)
    await flushPromises()
    const adminVm = admin.wrapper.vm as any
    expect(adminVm.visibleDropdownGroups.map((g: any) => g.title)).toEqual(['门户资源', '管理'])
    expect(adminVm.visibleDropdownGroups[0].items).toHaveLength(2)
    expect(adminVm.visibleDropdownGroups[1].items).toHaveLength(12)
    expect(adminVm.visibleDropdownGroups[1].items.map((i: any) => i.path)).toContain('/admin/system/general-settings')

    const kb = mountUserMenu(KB_ONLY)
    await flushPromises()
    // 门户资源恒显；管理组仅剩无权限键的沙箱设置（级联行同样过滤）
    const kbGroups = (kb.wrapper.vm as any).visibleDropdownGroups
    expect(kbGroups.map((g: any) => g.title)).toEqual(['门户资源', '管理'])
    expect(kbGroups[1].items.map((i: any) => i.path)).toEqual(['/admin/system/sandbox-settings'])
    expect((kb.wrapper.vm as any).cascadeGroups[0].items).toHaveLength(1)
  })

  it('级联行渲染并悬停弹出子面板（修订 #17，ADP 企业管理式）', async () => {
    const { wrapper } = mountUserMenu(ADMIN)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.userMenuVisible = true
    await flushPromises()

    // 主下拉展开后，级联行挂载于 teleport 的主下拉体内
    const row = document.querySelector('.user-menu__cascade-row')
    expect(row).toBeTruthy()
    expect(row!.textContent).toContain('管理')

    // 受控可见性直驱：cascadeVisible=true → 子面板可见，含 12 项管理功能
    vm.cascadeVisible = true
    await flushPromises()
    // 受控可见性直驱：cascadeVisible=true → 自绘子面板挂载，含 12 项管理功能
    vm.cascadeVisible = true
    await flushPromises()
    const sub = document.querySelector('.cascade-panel')
    expect(sub).toBeTruthy()
    const subText = sub!.textContent || ''
    expect(subText).toContain('管理')
    expect(subText).toContain('通用设置')
    expect(subText).toContain('角色管理')
    expect(subText).toContain('开放密钥')

    vm.cascadeVisible = false
    await flushPromises()
    expect(document.querySelector('.cascade-panel')).toBeNull()
  })
})

describe('用户动作流（T5.2-T5.4）', () => {
  it('用户信息：打开弹窗并懒加载组织映射（仅一次）', async () => {
    getOrgFlat.mockResolvedValue([{ id: 'org-1', name: '研发部' }])
    const { wrapper } = mountUserMenu(ADMIN)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.handleProfile()
    await flushPromises()
    expect(vm.userInfoDialog).toBe(true)
    expect(getOrgFlat).toHaveBeenCalledTimes(1)
    vm.handleProfile()
    await flushPromises()
    expect(getOrgFlat).toHaveBeenCalledTimes(1)
  })

  it('门户资源分组项：关闭下拉并跳转对应门户路由', async () => {
    const { wrapper, router } = mountUserMenu(ADMIN)
    await flushPromises()
    const vm = wrapper.vm as any

    vm.handleDropdownGroupClick('/portal/manual')
    await flushPromises()
    expect(vm.userMenuVisible).toBe(false)
    expect(router.currentRoute.value.path).toBe('/portal/manual')

    vm.handleDropdownGroupClick('/portal/changelog')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/portal/changelog')
  })

  it('退出系统：确认后清会话并跳 /login；取消则停留', async () => {
    const { wrapper, router, store } = mountUserMenu(ADMIN)
    await flushPromises()
    const vm = wrapper.vm as any

    confirmMock.confirm.mockResolvedValueOnce(true)
    vm.handleLogoutClick()
    await flushPromises()
    expect(confirmMock.confirm).toHaveBeenCalled()
    expect(store.userInfo).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')

    confirmMock.confirm.mockResolvedValueOnce(false)
    vm.handleLogoutClick()
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('下拉分组项：关闭下拉并跳转对应路由', async () => {
    const { wrapper, router } = mountUserMenu(ADMIN)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.handleDropdownGroupClick('/operation/kb-analytics')
    await flushPromises()
    expect(vm.userMenuVisible).toBe(false)
    expect(router.currentRoute.value.path).toBe('/operation/kb-analytics')
  })
})
