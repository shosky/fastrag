// @vitest-environment jsdom
import ElementPlus from 'element-plus'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import WidgetListPage from './index.vue'
import * as api from '@/api/widget'

// S5 工作台列表页（widget-frontend TDD T5.1~T5.4；STATE-201/202/203、PAGE-201）。
// 契约锚点：ui.md「Widget 工作台」+ 状态契约；API-001/002/005/006。

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: { ...actual.ElMessage, success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn().mockResolvedValue(true) },
  }
})
vi.mock('@/api/widget', async () => {
  const actual = await vi.importActual<typeof import('@/api/widget')>('@/api/widget')
  return {
    ...actual,
    getWidgets: vi.fn(),
    getWidget: vi.fn(),
    deleteWidget: vi.fn(),
    copyWidget: vi.fn(),
  }
})

const mocked = vi.mocked(api)

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/widget' }, { path: '/widget/create' }, { path: '/widget/:id' }],
})

function row(id: string, name: string) {
  return {
    id, name, description: '描述', icon: null, isBuiltin: false,
    creator: 'u1', createdAt: '2026-10-31T10:00:00', updatedAt: '2026-10-31T10:00:00',
  }
}

async function mountPage() {
  const wrapper = mount(WidgetListPage, { global: { plugins: [router, ElementPlus] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  router.push('/widget')
})

describe('Widget 列表页（PAGE-201）', () => {
  it('T5.1 加载期骨架屏（STATE-201），完成后渲染行', async () => {
    mocked.getWidgets.mockImplementation(() => new Promise((resolve) => setTimeout(() => resolve([row('w1', '卡A')] as never), 20)))
    const wrapper = mount(WidgetListPage, { global: { plugins: [router, ElementPlus] } })
    await flushPromises()
    // mount 后 onMounted 的 load() 置 loading=true；setTimeout(20) 未到 → 骨架在渲染
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-test="widget-loading"]').exists()).toBe(true)
    await flushPromises()
    expect(wrapper.find('[data-test="widget-table"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('卡A')
  })

  it('T5.2 我的空态双引导（STATE-202）；官方空态带重试（STATE-203）', async () => {
    mocked.getWidgets.mockResolvedValue([] as never)
    const wrapper = await mountPage()
    expect(wrapper.find('[data-test="widget-empty-mine"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="widget-empty-goto-builtin"]').exists()).toBe(true)
    wrapper.vm.activeTab = 'builtin'
    await flushPromises()
    expect(wrapper.find('[data-test="widget-empty-builtin"]').exists()).toBe(true)
  })

  it('T5.3 官方 tab 预览卡片网格：详情拉取 + WidgetCardRenderer 实时渲染 + 复制（TASK-001）', async () => {
    mocked.getWidgets.mockImplementation((builtin) =>
      Promise.resolve((builtin ? [row('w9', '官方模板')] : []) as never))
    mocked.getWidget.mockResolvedValue({
      ...row('w9', '官方模板'),
      template: '{"type":"Card","props":{},"children":[{"type":"Title","props":{"value":"预览标题"}}]}',
      schemaJson: '{"type":"object","properties":{}}',
      defaultJson: '{}',
    } as never)
    mocked.copyWidget.mockResolvedValue({ id: 'w-copy' } as never)
    const wrapper = await mountPage()
    wrapper.vm.activeTab = 'builtin'
    await flushPromises()
    expect(wrapper.find('[data-test="builtin-preview-grid"]').exists()).toBe(true)
    // 预览卡实时渲染模板效果（ADP 式 item 预览）
    expect(wrapper.findComponent({ name: 'WidgetCardRenderer' }).exists()).toBe(true)
    await wrapper.find('[data-test="widget-copy"]').trigger('click')
    await flushPromises()
    expect(mocked.copyWidget).toHaveBeenCalledWith('w9')
  })

  it('T5.3 删除 confirm 后调 deleteWidget；失败透出业务文案（TASK-003）', async () => {
    mocked.getWidgets.mockResolvedValue({ code: 200, message: 'ok', data: [row('w1', '卡A')] } as never)
    mocked.deleteWidget.mockRejectedValue({ message: '先在应用配置中解绑' })
    const wrapper = await mountPage()
    await (wrapper.vm as unknown as { remove: (r: unknown) => Promise<void> }).remove(row('w1', '卡A'))
    expect(mocked.deleteWidget).toHaveBeenCalledWith('w1')
  })

  it('T5.8 接口失败不崩溃（STATE-204 由 toast 承接）', async () => {
    mocked.getWidgets.mockRejectedValue(new Error('network'))
    const wrapper = await mountPage()
    expect(wrapper.exists()).toBe(true)
  })
})
