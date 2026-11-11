// @vitest-environment jsdom
// 组件级测试（qa-pairs 批量确认，2026-11-02）：
// 缺陷面/新面——问答对面板勾选、表头全选（筛选结果语义）、批量确认弹窗统计与提交闭环。
// 追溯：[Test] → ui.md「qa-pairs 链」UI/状态契约 → spec/qa-pairs.md REQ-B04/B05 §8（决议 D2-D5/D8）。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ElementPlus from 'element-plus'

vi.mock('element-plus', async () => {
  // 静音 ElMessage/ElMessageBox（交互断言走 mock），其余组件保持真实（同 WidgetConfig.test 惯例）
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn() },
  }
})

vi.mock('@/api', () => ({
  getQaPairs: vi.fn(),
  batchConfirmQaPairs: vi.fn(),
  createQaPair: vi.fn(),
  updateQaPair: vi.fn(),
  deleteQaPair: vi.fn(),
  confirmQaPair: vi.fn(),
}))

// KB 权限门控：面板测试恒为 editor 视角（viewer 不可见属权限指令面，不在本切片）
vi.mock('@/composables/useAuth', () => ({
  useAuth: () => ({ hasKBPermission: () => true }),
}))

import QaPanel from './QaPanel.vue'
import * as api from '@/api'

const ROWS = [
  { id: 'd1', question: '问题一', answer: '答案一', source: 'manual', status: 'draft', fileName: null },
  { id: 'd2', question: '问题二', answer: '答案二', source: 'ai', status: 'draft', fileName: null },
  { id: 'c1', question: '问题三', answer: '答案三', source: 'manual', status: 'confirmed', fileName: null },
]

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.getQaPairs).mockResolvedValue({ list: ROWS, total: ROWS.length })
  vi.mocked(api.batchConfirmQaPairs).mockResolvedValue({ confirmedCount: 2, skippedCount: 1 })
  vi.mocked(ElMessageBox.confirm).mockResolvedValue('ok')
  document.body.innerHTML = ''
})

// jsdom 无 ResizeObserver（Element Plus 表格布局依赖），测试环境补桩
class ResizeObserverStub { observe() {} unobserve() {} disconnect() {} }
;(globalThis as unknown as { ResizeObserver?: unknown }).ResizeObserver ??= ResizeObserverStub

function mountPanel(rows: typeof ROWS = ROWS): VueWrapper {
  vi.mocked(api.getQaPairs).mockResolvedValue({ list: rows, total: rows.length })
  return mount(QaPanel, { props: { kbId: 'kb1' }, global: { plugins: [ElementPlus] } })
}

function checkboxes(wrapper: VueWrapper) {
  return wrapper.findAllComponents({ name: 'ElCheckbox' })
}
function bulkBar(wrapper: VueWrapper) {
  return wrapper.find('.qa-panel__bulk-bar')
}
function confirmBtn(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((b) => b.text().trim().startsWith('批量确认'))
}

describe('QaPanel 批量确认（qa-pairs 链）', () => {
  it('表头全选=筛选结果全选：操作栏显示已选计数，混选时按钮可用', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    // 初始无操作栏
    expect(bulkBar(wrapper).exists()).toBe(false)

    // 表头复选框（首个 ElCheckbox）→ 3 行全选
    await checkboxes(wrapper)[0].vm.$emit('change', true)
    await nextTick()

    expect(bulkBar(wrapper).text()).toContain('已选 3 条')
    expect(confirmBtn(wrapper)!.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('所选均为已确认时_批量确认按钮禁用', async () => {
    const wrapper = mountPanel(ROWS.filter((r) => r.status === 'confirmed'))
    await flushPromises()

    await checkboxes(wrapper)[0].vm.$emit('change', true)
    await nextTick()

    expect(confirmBtn(wrapper)!.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('混选提交：弹窗统计 N-M-K、调用批量端点、成功后刷新并清空选中', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await checkboxes(wrapper)[0].vm.$emit('change', true)
    await nextTick()
    await confirmBtn(wrapper)!.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    const msg = vi.mocked(ElMessageBox.confirm).mock.calls[0][0] as string
    expect(msg).toContain('共 3 条')
    expect(msg).toContain('其中 1 条已确认将跳过')
    expect(msg).toContain('本次确认 2 条')

    expect(api.batchConfirmQaPairs).toHaveBeenCalledWith('kb1', ['d1', 'd2', 'c1'])
    expect(ElMessage.success).toHaveBeenCalledWith('已确认 2 条，跳过 1 条')
    expect(vi.mocked(api.getQaPairs).mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(bulkBar(wrapper).exists()).toBe(false)
    wrapper.unmount()
  })

  it('取消二次确认_不提交且保留选中', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    const wrapper = mountPanel()
    await flushPromises()

    await checkboxes(wrapper)[0].vm.$emit('change', true)
    await nextTick()
    await confirmBtn(wrapper)!.trigger('click')
    await flushPromises()

    expect(api.batchConfirmQaPairs).not.toHaveBeenCalled()
    expect(bulkBar(wrapper).exists()).toBe(true)
    wrapper.unmount()
  })
})
