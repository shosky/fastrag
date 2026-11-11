// @vitest-environment jsdom
// S4.6/S5.7 Header 壳（nav-shell TDD）：铃铛注入位置 + 面包屑根「主页」回归锚点。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'

const { getUnreadNotificationCount } = vi.hoisted(() => ({
  getUnreadNotificationCount: vi.fn(),
}))

vi.mock('@/api', () => ({ getUnreadNotificationCount }))

import Header from './Header.vue'

function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/home',
        component: { template: '<div />' },
        meta: { title: '主页' },
        children: [
          { path: 'detail', component: { template: '<div />' }, meta: { title: '详情' } },
        ],
      },
      { path: '/home/detail', component: { template: '<div />' }, meta: { title: '详情' } },
    ],
  })
}

async function mountHeader() {
  const router = buildRouter()
  router.push('/home/detail')
  await router.isReady()
  const wrapper = mount(Header, { global: { plugins: [createPinia(), router, ElementPlus] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  getUnreadNotificationCount.mockResolvedValue(0)
})

describe('Header 壳（T4.6 铃铛注入 / 修订 #14 用户入口右上角 / T5.7 回归锚点）', () => {
  it('header-right 顺序：铃铛 → 页面操作区 → 用户入口（最右，ADP 式）', async () => {
    const wrapper = await mountHeader()
    const right = wrapper.find('.header-right')
    expect(right.exists()).toBe(true)
    expect(right.find('.notification-bell').exists()).toBe(true)
    expect(right.find('.user-entry').exists()).toBe(true)

    const childNodes = right.element.children
    const indexOf = (el: Element) => Array.prototype.indexOf.call(childNodes, el)
    const bellIdx = indexOf(wrapper.find('.notification-bell').element)
    const actionsIdx = indexOf(right.find('#header-actions').element)
    const userIdx = indexOf(wrapper.find('.user-entry').element)
    expect(bellIdx).toBeGreaterThanOrEqual(0)
    expect(bellIdx).toBeLessThan(actionsIdx)
    expect(userIdx).toBeGreaterThan(actionsIdx)
  })

  it('面包屑根为「主页」（门户链文字改名回归锚点），页面标题正常渲染', async () => {
    const wrapper = await mountHeader()
    expect(wrapper.find('.page-title').text()).toBe('详情')
    const crumbs = wrapper.findAll('.el-breadcrumb__item')
    expect(crumbs.length).toBeGreaterThanOrEqual(2)
    expect(crumbs[0].text()).toContain('主页')
  })

  it('挂载 Header 触发铃铛未读数拉取', async () => {
    await mountHeader()
    expect(getUnreadNotificationCount).toHaveBeenCalledTimes(1)
  })
})
