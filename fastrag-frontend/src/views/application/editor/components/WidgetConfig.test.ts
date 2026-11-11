// @vitest-environment jsdom
import ElementPlus from 'element-plus'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import WidgetConfig from './WidgetConfig.vue'
import * as api from '@/api/widget'

// S6 绑定面板（widget-frontend TDD T6.1~T6.3；PAGE-204，第六类绑定）。
// 契约锚点：ui.md「Widget 绑定面板」；API-001/007/008/009/010；ERR-WG-BINDING-LIMIT 409。

vi.mock('@/api/widget', async () => {
  const actual = await vi.importActual<typeof import('@/api/widget')>('@/api/widget')
  return {
    ...actual,
    getWidgetCandidates: vi.fn(),
    getAppWidgets: vi.fn(),
    bindAppWidget: vi.fn(),
    updateAppWidgetBinding: vi.fn(),
    deleteAppWidgetBinding: vi.fn(),
  }
})
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return { ...actual, ElMessage: { ...actual.ElMessage, success: vi.fn(), error: vi.fn(), warning: vi.fn() } }
})

const mocked = vi.mocked(api)

const candidates = [
  { id: 'w1', name: '订单确认卡', description: '确认' },
  { id: 'w2', name: '进度卡', description: null },
]

beforeEach(() => {
  vi.clearAllMocks()
  mocked.getWidgetCandidates.mockResolvedValue(candidates as never)
  mocked.getAppWidgets.mockResolvedValue([] as never)
  mocked.bindAppWidget.mockResolvedValue({ id: 'b1' } as never)
  mocked.updateAppWidgetBinding.mockResolvedValue(null as never)
  mocked.deleteAppWidgetBinding.mockResolvedValue(null as never)
})

async function mountPanel() {
  const wrapper = mount(WidgetConfig, { props: { appInfo: { id: 'app-1' } }, global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('WidgetConfig（PAGE-204）', () => {
  it('T6.1 候选列表渲染（自建资产）', async () => {
    const wrapper = await mountPanel()
    expect(wrapper.find('[data-test="widget-candidate-w1"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('订单确认卡')
  })

  it('T6.2 勾选 + 保存 → bindAppWidget 精确调用（diff 新增）', async () => {
    const wrapper = await mountPanel()
    await wrapper.find('[data-test="widget-candidate-w1"] input').setValue(true)
    await flushPromises()
    await wrapper.find('[data-test="widget-bind-save"]').trigger('click')
    await flushPromises()
    expect(mocked.bindAppWidget).toHaveBeenCalledWith('app-1', { widgetId: 'w1', enabled: true })
  })

  it('T6.2 现绑定取消勾选 → deleteAppWidgetBinding', async () => {
    mocked.getAppWidgets.mockResolvedValue([
      { id: 'b1', widgetId: 'w1', widgetName: '订单确认卡', enabled: true },
    ] as never)
    const wrapper = await mountPanel()
    // 取消勾选：经 ElCheckbox 组件实例发 update:modelValue（jsdom 对 EP 内部 input 事件支持有限）
    const checkbox = wrapper.find('[data-test="widget-candidate-w1"]').findComponent({ name: 'ElCheckbox' })
    checkbox.vm.$emit('update:modelValue', false)
    await flushPromises()
    await wrapper.find('[data-test="widget-bind-save"]').trigger('click')
    await flushPromises()
    expect(mocked.deleteAppWidgetBinding).toHaveBeenCalledWith('app-1', 'b1')
  })

  it('T6.3 上限 10：勾选被拒时 warning（ERR-WG-BINDING-LIMIT 前端预拦截）', async () => {
    mocked.getAppWidgets.mockResolvedValue(
      Array.from({ length: 10 }, (_, i) => ({
        id: `b${i}`, widgetId: `wx${i}`, widgetName: `卡${i}`, enabled: true,
      })) as never)
    const wrapper = await mountPanel()
    await wrapper.find('[data-test="widget-candidate-w1"] input').setValue(true)
    await flushPromises()
    expect(ElMessage.warning).toHaveBeenCalled()
    expect(mocked.bindAppWidget).not.toHaveBeenCalled()
  })
})
