// @vitest-environment jsdom
// 组件级测试（qa-generation 缺陷回归，2026-11-02）：
// 缺陷——触发受理完成后 `triggering` 复位，进度轮询（任务 running）期间「提炼」按钮仍可点击。
// 回归口径（docs/implement/qa-generation.md §8）：
//  1) 初次打开（后端无进行中任务）→ 按钮可用；
//  2) 触发后任务 running → 按钮禁用（重复点击不重复触发）；全部终态 → 按钮恢复可用；
//  3) 关闭重开 → restoreRunningTasks 按后端任务账本恢复 running 任务，按钮保持禁用。
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, type Component } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { ElButton } from 'element-plus'

vi.mock('element-plus', async () => {
  // 仅静音 ElMessage（防 jsdom 过渡动画/定时器噪声），其余组件保持真实（同 WidgetConfig.test 惯例）
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return { ...actual, ElMessage: { ...actual.ElMessage, success: vi.fn(), error: vi.fn(), warning: vi.fn() } }
})

vi.mock('@/api', () => ({
  getModels: vi.fn(async () => []),
  triggerQaGeneration: vi.fn(async () => ({})),
  getQaGenerationStatus: vi.fn(async () => null),
}))

import QaGenerateDialog from './QaGenerateDialog.vue'
import { getModels, triggerQaGeneration, getQaGenerationStatus } from '@/api'

const statusMock = vi.mocked(getQaGenerationStatus)
const triggerMock = vi.mocked(triggerQaGeneration)

// 直渲染 default + footer 插槽的 el-dialog 替身（规避 teleport，页脚按钮留在 wrapper 内可查）
const DialogStub = defineComponent({
  name: 'ElDialogStub',
  props: { modelValue: Boolean, title: String },
  setup(_, { slots }) {
    return () => h('div', { class: 'dialog-stub' }, [slots.default?.(), slots.footer?.()])
  },
})

// 测试环境无 unplugin-vue-components，模板中的 el-* 组件需显式替身/注册（同 MessageBubble.test 惯例）；
// el-button 保留真组件以断言 disabled 属性，其余仅作占位渲染
const STUBS: Record<string, Component | boolean> = {
  'el-dialog': DialogStub,
  'el-alert': true,
  'el-form': true,
  'el-form-item': true,
  'el-input-number': true,
  'el-input': true,
  'el-radio-group': true,
  'el-radio': true,
  'el-select': true,
  'el-option': true,
  'el-table': true,
  'el-table-column': true,
  'el-tag': true,
}

const RUNNING_VIEW = { status: 'running', totalChunks: 5, succeededChunks: 2, failedChunks: 0, error: null }
const SUCCESS_VIEW = { status: 'success', totalChunks: 5, succeededChunks: 5, failedChunks: 0, error: null }

function mountDialog(): VueWrapper {
  return mount(QaGenerateDialog, {
    props: { modelValue: false, kbId: 'kb1', files: [{ id: 'f1', name: '文件一.pdf' }] },
    global: { stubs: STUBS, components: { ElButton } },
  })
}

async function openDialog(wrapper: VueWrapper): Promise<void> {
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
}

function triggerButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((b) => b.text().trim().startsWith('提炼'))
}

describe('QaGenerateDialog 提炼按钮可用性', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(getModels).mockResolvedValue([])
    triggerMock.mockResolvedValue({})
    statusMock.mockResolvedValue(null)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  it('初次打开且后端无进行中任务时，提炼按钮可用', async () => {
    const wrapper = mountDialog()
    await openDialog(wrapper)

    const btn = triggerButton(wrapper)
    expect(btn).toBeDefined()
    expect(btn!.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('触发后任务进行中按钮禁用（重复点击不重复触发），全部终态后恢复可用', async () => {
    const wrapper = mountDialog()
    await openDialog(wrapper)

    await triggerButton(wrapper)!.trigger('click')
    await flushPromises()

    // 受理完成、任务 running：按钮禁用 + submitted 已通知父页 + 立即轮询过一次
    expect(triggerMock).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('submitted')).toHaveLength(1)
    expect(triggerButton(wrapper)!.attributes('disabled')).toBeDefined()

    // 进行中重复点击：hasActiveTask 守卫直接返回，不产生第二次触发
    await triggerButton(wrapper)!.trigger('click')
    await flushPromises()
    expect(triggerMock).toHaveBeenCalledTimes(1)

    // 后端账本推进到 success 终态：下一轮轮询后按钮恢复可用
    statusMock.mockResolvedValue(SUCCESS_VIEW)
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(triggerButton(wrapper)!.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('关闭重开对话框：按后端账本恢复 running 任务，按钮保持禁用直至终态', async () => {
    statusMock.mockResolvedValue(RUNNING_VIEW)
    const wrapper = mountDialog()
    await openDialog(wrapper)

    // 首次打开即恢复进行中任务 → 禁用
    expect(triggerButton(wrapper)!.attributes('disabled')).toBeDefined()

    // 关闭（停轮询）后重开：restoreRunningTasks 再次恢复 → 仍禁用
    await wrapper.setProps({ modelValue: false })
    await flushPromises()
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(triggerButton(wrapper)!.attributes('disabled')).toBeDefined()

    // 任务终态后恢复可用
    statusMock.mockResolvedValue(SUCCESS_VIEW)
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(triggerButton(wrapper)!.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
})
