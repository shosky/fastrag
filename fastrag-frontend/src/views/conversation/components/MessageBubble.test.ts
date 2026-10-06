// @vitest-environment jsdom
// 组件级测试（收尾批 2026-09-26）：MessageBubble 挂载渲染 + 交互 emit。
// 子组件（ThinkingBlock/ToolCallRenderer/SourcesBlock）与自动导入的 Element Plus
// 图标全部 stub——被测面只有气泡本体：检索提示 / 来源块 / 汇总 chips / 引用角标。
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElIcon } from 'element-plus'
import { ChatLineRound } from '@element-plus/icons-vue'
import MessageBubble from './MessageBubble.vue'
import type { ConversationChatMessage } from '@/types/conversation'

const stubs = {
  ThinkingBlock: { template: '<div class="thinking-stub" />' },
  ToolCallRenderer: { template: '<div class="toolcall-stub" />' },
  SourcesBlock: { template: '<div class="sources-stub" />' },
}

function mountBubble(message: ConversationChatMessage) {
  return mount(MessageBubble, {
    props: { message },
    global: {
      stubs,
      // 生产经 unplugin-vue-components 自动导入（components.d.ts）；测试环境无该
      // 插件，此处按运行时解析注册等价组件（el-icon → ElIcon，经 camelize 匹配）
      components: { ElIcon, ChatLineRound },
    },
  })
}

describe('MessageBubble（组件级）', () => {
  it('assistant 带 retrievalHint 渲染检索空态提示', () => {
    const wrapper = mountBubble({
      role: 'assistant',
      content: '直接回答正文',
      retrievalHint: '未选择知识库，本次直接由模型回答',
    })
    const hint = wrapper.find('.msg-hint')
    expect(hint.exists()).toBe(true)
    expect(hint.text()).toContain('未选择知识库')
  })

  it('带 sources 渲染 SourcesBlock（stub）', () => {
    const wrapper = mountBubble({
      role: 'assistant',
      content: '带来源的回答',
      sources: [{ fileName: '手册.pdf', content: '命中分片全文', kbId: 'k1', fileId: 'f1' }],
    })
    expect(wrapper.find('.sources-stub').exists()).toBe(true)
  })

  it('summaryChips：带 toolCalls + iterations 显示工具调用与轮数 chip', () => {
    const wrapper = mountBubble({
      role: 'assistant',
      content: '已完成检索',
      toolCalls: [{ id: 't1', name: 'kb_search', arguments: '{}' }],
      iterations: 3,
    })
    const summary = wrapper.find('.msg-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('1 次工具调用')
    expect(summary.text()).toContain('3 轮')
  })

  it('summaryChips：无 toolCalls 且无 iterations 时不渲染汇总行', () => {
    const wrapper = mountBubble({ role: 'assistant', content: '纯文本回答' })
    expect(wrapper.find('.msg-summary').exists()).toBe(false)
  })

  it('cite 角标点击 emit open-sources（content 含 [1] + 一条 sources）', async () => {
    const wrapper = mountBubble({
      role: 'assistant',
      content: '结论如下 [1]。',
      sources: [{ fileName: '手册.pdf' }],
    })
    const ref = wrapper.find('sup.cite-ref')
    expect(ref.exists()).toBe(true)
    await ref.trigger('click')
    expect(wrapper.emitted('open-sources')).toHaveLength(1)
  })
})
