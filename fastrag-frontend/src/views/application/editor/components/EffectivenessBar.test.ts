// @vitest-environment jsdom
// 组件级测试（收尾批 2026-09-26）：EffectivenessBar 失效项解绑。
// api 模块整体 mock（vi.mock 顶层提升），ElMessage 用 spyOn 截断真实弹窗；
// 被测面：失效项渲染解绑按钮 / 点击解绑按 type 路由到对应 API 并刷新有效性。
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElMessage } from 'element-plus'

const {
  getBindingsEffectiveness,
  getAppKbBindings,
  unbindAppKb,
  getAppToolBindings,
  unbindAppTool,
  getAppSkillBindings,
  unbindAppSkill,
  getAppMcpBindings,
  unbindAppMcp,
  getAppDbBindings,
  deleteAppDbBinding,
} = vi.hoisted(() => ({
  getBindingsEffectiveness: vi.fn(),
  getAppKbBindings: vi.fn(),
  unbindAppKb: vi.fn(),
  getAppToolBindings: vi.fn(),
  unbindAppTool: vi.fn(),
  getAppSkillBindings: vi.fn(),
  unbindAppSkill: vi.fn(),
  getAppMcpBindings: vi.fn(),
  unbindAppMcp: vi.fn(),
  getAppDbBindings: vi.fn(),
  deleteAppDbBinding: vi.fn(),
}))

vi.mock('@/api', () => ({
  getBindingsEffectiveness,
  getAppKbBindings,
  unbindAppKb,
  getAppToolBindings,
  unbindAppTool,
  getAppSkillBindings,
  unbindAppSkill,
  getAppMcpBindings,
  unbindAppMcp,
  getAppDbBindings,
  deleteAppDbBinding,
}))

import EffectivenessBar from './EffectivenessBar.vue'

function mountBar(type: 'kb' | 'tool' | 'skill' | 'mcp' | 'db' = 'kb') {
  return mount(EffectivenessBar, {
    props: { appInfo: { id: 'app-1' }, type },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(ElMessage, 'success').mockImplementation((() => ({})) as any)
  vi.spyOn(ElMessage, 'error').mockImplementation((() => ({})) as any)
})

describe('EffectivenessBar（组件级）', () => {
  it('exists=false 项渲染解绑按钮（非本面板 type 的失效项不渲染）', async () => {
    getBindingsEffectiveness.mockResolvedValue([
      { type: 'kb', id: 'kb-1', name: '在架知识库', exists: true, enabled: true },
      { type: 'kb', id: 'kb-9', name: '旧知识库', exists: false, enabled: true, reason: '资源已删除' },
      { type: 'tool', id: 'tool-9', name: '别家失效工具', exists: false, enabled: true },
    ])
    const wrapper = mountBar('kb')
    await flushPromises()

    const btns = wrapper.findAll('.eff-unbind')
    expect(btns.length).toBe(1)
    expect(wrapper.text()).toContain('旧知识库')
    expect(wrapper.text()).not.toContain('别家失效工具')
  })

  it('点击解绑：按资源 id 换算绑定行 id 调 unbindAppKb，成功后重新拉取有效性', async () => {
    getBindingsEffectiveness
      .mockResolvedValueOnce([
        { type: 'kb', id: 'kb-1', name: '在架知识库', exists: true, enabled: true },
        { type: 'kb', id: 'kb-9', name: '旧知识库', exists: false, enabled: true, reason: '资源已删除' },
      ])
      .mockResolvedValueOnce([{ type: 'kb', id: 'kb-1', name: '在架知识库', exists: true, enabled: true }])
    getAppKbBindings.mockResolvedValue([{ id: 'row-1', kbId: 'kb-9', enabled: 1 }])
    unbindAppKb.mockResolvedValue({})

    const wrapper = mountBar('kb')
    await flushPromises()
    expect(getBindingsEffectiveness).toHaveBeenCalledTimes(1)

    await wrapper.find('.eff-unbind').trigger('click')
    await flushPromises()

    // 解绑端点按绑定行 id 删：先查绑定列表换行 id（app-1, row-1），非资源 id
    expect(getAppKbBindings).toHaveBeenCalledWith('app-1')
    expect(unbindAppKb).toHaveBeenCalledTimes(1)
    expect(unbindAppKb).toHaveBeenCalledWith('app-1', 'row-1')
    // 成功后重新拉取有效性数据
    expect(getBindingsEffectiveness).toHaveBeenCalledTimes(2)
    expect(ElMessage.success).toHaveBeenCalledWith('已解绑')
  })
})
