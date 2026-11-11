// @vitest-environment jsdom
// S4 通知铃铛（nav-shell TDD T4.1-T4.5）：formatBadge 纯函数 + 拉取时机 + 跳转 + 静默失败（STATE-101）。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import NotificationBell, { formatBadge } from './NotificationBell.vue'

const { getUnreadNotificationCount } = vi.hoisted(() => ({
  getUnreadNotificationCount: vi.fn(),
}))

vi.mock('@/api', () => ({ getUnreadNotificationCount }))

function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/home', component: { template: '<div />' } },
      { path: '/knowledge', component: { template: '<div />' } },
      { path: '/notification', component: { template: '<div />' } },
    ],
  })
}

async function mountBell() {
  const router = buildRouter()
  router.push('/home')
  await router.isReady()
  const wrapper = mount(NotificationBell, { global: { plugins: [router, ElementPlus] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('formatBadge 纯函数（T4.1 五例边界）', () => {
  it('0 → 空（隐藏）；1-99 → 数值；≥100 → 99+；失败/非数值 → 空', () => {
    expect(formatBadge(0)).toBe('')
    expect(formatBadge(1)).toBe('1')
    expect(formatBadge(99)).toBe('99')
    expect(formatBadge(100)).toBe('99+')
    expect(formatBadge(undefined)).toBe('')
    expect(formatBadge(null)).toBe('')
    expect(formatBadge('42')).toBe('')
    expect(formatBadge(Number.NaN)).toBe('')
  })
})

describe('拉取时机与徽标渲染（T4.2/T4.3/T4.5）', () => {
  it('挂载即拉取一次未读数，徽标渲染数值', async () => {
    getUnreadNotificationCount.mockResolvedValue(42)
    const { wrapper } = await mountBell()
    expect(getUnreadNotificationCount).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.el-badge__content').text()).toBe('42')
  })

  it('徽标为 0 时不渲染徽标内容', async () => {
    getUnreadNotificationCount.mockResolvedValue(0)
    const { wrapper } = await mountBell()
    expect(wrapper.find('.el-badge__content').exists()).toBe(false)
  })

  it('未读数 ≥100 显示 99+', async () => {
    getUnreadNotificationCount.mockResolvedValue(128)
    const { wrapper } = await mountBell()
    expect(wrapper.find('.el-badge__content').text()).toBe('99+')
  })

  it('接口失败静默：无徽标、无报错弹窗（STATE-001）', async () => {
    getUnreadNotificationCount.mockRejectedValue(new Error('network down'))
    const { wrapper } = await mountBell()
    expect(wrapper.find('.el-badge__content').exists()).toBe(false)
    expect(document.querySelector('.el-message')).toBeNull()
  })

  it('响应非数值按失败处理：无徽标', async () => {
    getUnreadNotificationCount.mockResolvedValue({ count: 3 })
    const { wrapper } = await mountBell()
    expect(wrapper.find('.el-badge__content').exists()).toBe(false)
  })
})

describe('路由联动（T4.3 重拉 / T4.4 跳转）', () => {
  it('离开 /notification 时重拉；普通路由间切换不触发', async () => {
    getUnreadNotificationCount.mockResolvedValue(5)
    const { router } = await mountBell()

    await router.push('/notification')
    await flushPromises()
    expect(getUnreadNotificationCount).toHaveBeenCalledTimes(1)

    await router.push('/home')
    await flushPromises()
    expect(getUnreadNotificationCount).toHaveBeenCalledTimes(2)

    await router.push('/knowledge')
    await flushPromises()
    expect(getUnreadNotificationCount).toHaveBeenCalledTimes(2)
  })

  it('点击铃铛跳转 /notification', async () => {
    getUnreadNotificationCount.mockResolvedValue(1)
    const { wrapper, router } = await mountBell()
    await wrapper.find('.notification-bell').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/notification')
  })
})
