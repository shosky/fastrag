// @vitest-environment jsdom
// S1/S2/S3/S4 组件级测试（widget-frontend TDD T1.5~T1.7、T2.2~T2.5、T3.1~T3.5、T4.2/T4.3）：
// WidgetCardRenderer 递归渲染 / 插值合并次序 / 消毒与 v-html 边界 / 图片协议白名单 /
// 降级矩阵 / 交互窗口与豁免 / 回传负载组装与防重 / 失败恢复。
// Element Plus 组件手动注册（测试环境无 unplugin-vue-components）。
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import {
  ElButton,
  ElCheckbox,
  ElCol,
  ElDivider,
  ElIcon,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElTag,
} from 'element-plus'
import WidgetCardRenderer from './WidgetCardRenderer.vue'
import type { WidgetSnapshot } from '@/types/widget'

const ep = {
  ElButton,
  ElCheckbox,
  ElCol,
  ElDivider,
  ElIcon,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElTag,
}

function snapOf(template: unknown, data: Record<string, unknown> = {}): WidgetSnapshot {
  return {
    widgetId: 'w1',
    widgetKey: 'order-confirm',
    name: '订单确认卡',
    template: JSON.stringify(template),
    data,
  }
}

function mountCard(snapshot: WidgetSnapshot | null, extra: Record<string, unknown> = {}) {
  return mount(WidgetCardRenderer, {
    props: { snapshot, interactive: true, ...extra },
    global: { components: ep },
  })
}

/** 带卡片动作行的最小模板（S3/S4 共用） */
function formCard(actions: unknown[]): WidgetSnapshot {
  return snapOf({ type: 'Card', props: { actions }, children: [] })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('S1 递归渲染与插值（T1.4/T1.5）', () => {
  it('T1.5 Card>Row>Col>Title/Text 嵌套树渲染出对应组件树', () => {
    const wrapper = mountCard(
      snapOf({
        type: 'Card',
        props: {},
        children: [
          {
            type: 'Row',
            props: {},
            children: [
              { type: 'Col', props: { span: 12 }, children: [{ type: 'Title', props: { text: '订单确认', level: 2 } }] },
              { type: 'Col', props: { span: 12 }, children: [{ type: 'Text', props: { text: '桌号 A-12' } }] },
            ],
          },
        ],
      })
    )
    expect(wrapper.find('.widget-w-card-box').exists()).toBe(true)
    expect(wrapper.find('.widget-w-row').exists()).toBe(true)
    expect(wrapper.findAll('.widget-w-col')).toHaveLength(2)
    expect(wrapper.find('.widget-w-title').text()).toBe('订单确认')
    expect(wrapper.find('.widget-w-text').text()).toBe('桌号 A-12')
  })

  it('T1.5 ListView/ListViewItem 列表渲染', () => {
    const wrapper = mountCard(
      snapOf({
        type: 'ListView',
        props: {},
        children: [
          { type: 'ListViewItem', props: {}, children: [{ type: 'Text', props: { text: '宫保鸡丁 x1' } }] },
          { type: 'ListViewItem', props: {}, children: [{ type: 'Text', props: { text: '米饭 x1' } }] },
        ],
      })
    )
    expect(wrapper.find('.widget-w-list').exists()).toBe(true)
    expect(wrapper.findAll('.widget-w-list-item')).toHaveLength(2)
    expect(wrapper.find('.widget-w-list-item').text()).toContain('宫保鸡丁 x1')
  })

  it('T1.5/T1.3 Form 内表单控件可输入并可收集（提交 payload 并入表单值）', async () => {
    const wrapper = mountCard(
      snapOf({
        type: 'Card',
        props: {},
        children: [
          { type: 'Input', props: { name: 'note', label: '备注' } },
          { type: 'Button', props: { text: '确认', action: 'sys.submit', primary: true } },
        ],
      })
    )
    await wrapper.find('input').setValue('少辣')
    await wrapper.find('button').trigger('click')
    const emitted = wrapper.emitted('action')
    expect(emitted).toHaveLength(1)
    expect(emitted![0][0]).toEqual({
      widgetId: 'w1',
      widgetKey: 'order-confirm',
      action: 'sys.submit',
      payload: { note: '少辣' },
    })
  })

  it('T1.4 插值合并次序：default 独有出 default 值、同名出 data 值、两侧皆无为空串', () => {
    const wrapper = mountCard(
      snapOf(
        {
          type: 'Card',
          props: {},
          children: [
            { type: 'Text', props: { text: '{{onlyDefault}}|{{shared}}|{{nowhere}}' } },
          ],
        },
        { onlyDefault: '默认值', shared: 'data 值', nothing: 1 }
      )
    )
    // onlyDefault：default 独有（后端已按 default 填充进快照 data）→ 出 default 值
    // shared：同名 → 出 data 值；nowhere：两侧皆无 → 空串
    expect(wrapper.find('.widget-w-text').text()).toBe('默认值|data 值|')
  })

  it('状态文本区渲染 snapshot.name + Card.status；动作行由根 Card actions 渲染', () => {
    const wrapper = mountCard(
      snapOf({
        type: 'Card',
        props: { status: '待确认', actions: [{ type: 'sys.submit', text: '确认' }] },
        children: [],
      })
    )
    expect(wrapper.find('.widget-card__name').text()).toBe('订单确认卡')
    expect(wrapper.find('.widget-card__status-text').text()).toBe('待确认')
    expect(wrapper.findAll('.widget-card__actions button')).toHaveLength(1)
  })
})

describe('S1 消毒与 v-html 边界（T1.6）', () => {
  it('T1.6 Markdown 组件经 renderMarkdown 输出且恶意 script 被消毒', () => {
    const wrapper = mountCard(
      snapOf({
        type: 'Markdown',
        props: { text: '**加粗**<script>alert(1)</script>' },
      })
    )
    const html = wrapper.find('.widget-w-markdown').element.innerHTML
    expect(html).not.toMatch(/<script/i) // script 元素被 DOMPurify 整体剔除
    expect(html).toContain('<strong>') // 合法 markdown 照常渲染
  })

  it('其余组件文本节点渲染无 v-html（innerHTML 无 raw 注入）', () => {
    const wrapper = mountCard(
      snapOf({
        type: 'Card',
        props: {},
        children: [{ type: 'Text', props: { text: '<img src=x onerror=alert(1)>' } }],
      })
    )
    const textEl = wrapper.find('.widget-w-text').element
    expect(textEl.innerHTML).not.toContain('<img')
    expect(wrapper.find('.widget-w-text').text()).toContain('<img src=x onerror=alert(1)>')
  })
})

describe('S1 Image src 白名单（T1.7）', () => {
  it('http(s) 渲染 img；javascript:/data: 渲染占位', () => {
    const ok = mountCard(snapOf({ type: 'Image', props: { src: 'https://a.b/c.png', alt: '图' } }))
    expect(ok.find('img.widget-w-image').exists()).toBe(true)
    expect(ok.find('img.widget-w-image').attributes('src')).toBe('https://a.b/c.png')

    const js = mountCard(snapOf({ type: 'Image', props: { src: 'javascript:alert(1)' } }))
    expect(js.find('img').exists()).toBe(false)
    expect(js.find('.widget-w-image__placeholder').exists()).toBe(true)

    const data = mountCard(snapOf({ type: 'Image', props: { src: 'data:text/html;base64,PHNjcmlwdD4=' } }))
    expect(data.find('img').exists()).toBe(false)
    expect(data.find('.widget-w-image__placeholder').exists()).toBe(true)
  })

  it('T2.5 加载失败图片 → 占位（单次渲染语义，不重试风暴）', async () => {
    const wrapper = mountCard(snapOf({ type: 'Image', props: { src: 'https://a.b/broken.png' } }))
    expect(wrapper.find('img.widget-w-image').exists()).toBe(true)
    await wrapper.find('img.widget-w-image').trigger('error')
    expect(wrapper.find('img.widget-w-image').exists()).toBe(false)
    expect(wrapper.find('.widget-w-image__placeholder').exists()).toBe(true)
  })
})

describe('S2 降级矩阵（T2.2/T2.3/T2.4）', () => {
  it('T2.2 未知组件名 → 卡内占位 + console.warn，兄弟组件照常渲染', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const wrapper = mountCard(
      snapOf({
        type: 'Card',
        props: {},
        children: [
          { type: 'Nope', props: {} },
          { type: 'Text', props: { text: '兄弟仍在' } },
        ],
      })
    )
    expect(wrapper.find('.widget-w-unknown').exists()).toBe(true)
    expect(wrapper.find('.widget-w-unknown').text()).toContain('Nope')
    expect(warn).toHaveBeenCalled()
    expect(wrapper.find('.widget-w-text').text()).toBe('兄弟仍在')
  })

  it('T2.3 模板结构坏（children 非数组 / props 缺失）→ 占位不抛未捕获异常', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    // children 非数组 + 子节点 props 缺失：fail-soft 渲染空容器
    const wrapper = mountCard(
      snapOf({ type: 'Card', children: 'not-an-array', children2: [{ type: 'Text' }] })
    )
    expect(wrapper.find('.widget-card').exists()).toBe(true)
    // 坏 JSON 模板 → 整卡模板占位
    const bad = mount(WidgetCardRenderer, {
      props: { snapshot: { ...snapOf({ type: 'Card' }), template: '{bad json' }, interactive: true },
      global: { components: ep },
    })
    expect(bad.find('.widget-card__fallback').exists()).toBe(true)
    expect(bad.find('.widget-card__fallback').text()).toContain('卡片模板无法渲染')
    expect(warn).toHaveBeenCalled()
  })

  it('T2.4 快照缺失/null → 整卡占位', () => {
    const wrapper = mount(WidgetCardRenderer, {
      props: { snapshot: null, interactive: true },
      global: { components: ep },
    })
    expect(wrapper.find('.widget-card__fallback').exists()).toBe(true)
    expect(wrapper.find('.widget-card__fallback').text()).toContain('卡片数据缺失')
  })
})

describe('S3 交互窗口与动作语义（T3.1~T3.5）', () => {
  it('T3.1 interactive=true 动作可点（emit action 负载齐）', async () => {
    const wrapper = mountCard(formCard([{ type: 'sys.submit', text: '确认' }]))
    const btn = wrapper.find('.widget-card__actions button')
    expect(btn.attributes('disabled')).toBeUndefined()
    await btn.trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(1)
    expect(wrapper.emitted('action')![0][0]).toMatchObject({ action: 'sys.submit', widgetId: 'w1' })
  })

  it('T3.2 interactive=false 置灰 disabled，点击无 emit（STATE-210）', async () => {
    const wrapper = mountCard(formCard([{ type: 'sys.submit', text: '确认' }]), { interactive: false })
    const btn = wrapper.find('.widget-card__actions button')
    expect(btn.attributes('disabled')).toBeDefined()
    await btn.trigger('click')
    expect(wrapper.emitted('action')).toBeUndefined()
  })

  it('T3.3 alwaysEnabled 豁免：置灰窗口内 go_to_url 新窗打开 / download 触发下载', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = mountCard(
      formCard([
        { type: 'sys.go_to_url', text: '查看详情', url: 'https://a.b/page', alwaysEnabled: true },
        { type: 'sys.download', text: '下载对账单', url: 'https://a.b/bill.pdf', alwaysEnabled: true },
      ]),
      { interactive: false }
    )
    const btns = wrapper.findAll('.widget-card__actions button')
    expect(btns).toHaveLength(2)
    await btns[0]!.trigger('click')
    expect(openSpy).toHaveBeenCalledWith('https://a.b/page', '_blank', 'noopener')
    await btns[1]!.trigger('click')
    expect(clickSpy).toHaveBeenCalled()
    const anchor = clickSpy.mock.instances[0] as HTMLAnchorElement | undefined
    expect(anchor?.getAttribute('download')).not.toBeNull()
    expect(anchor?.href).toBe('https://a.b/bill.pdf')
  })

  it('T3.4 未知 action type → 忽略 + console.warn，不误跳转不崩卡', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = mountCard(formCard([{ type: 'sys.explode', text: '危险' }]))
    await wrapper.find('.widget-card__actions button').trigger('click')
    expect(warn).toHaveBeenCalled()
    expect(openSpy).not.toHaveBeenCalled()
    expect(wrapper.find('.widget-card').exists()).toBe(true)
  })

  it('T3.5 preview=true → 按钮正常渲染（无禁用/无提示，2026-10-31 联调裁决）；点击不产生回传', async () => {
    const wrapper = mountCard(
      formCard([
        { type: 'sys.submit', text: '确认' },
        { type: 'sys.go_to_url', text: '查看', url: 'https://a.b', alwaysEnabled: true },
      ]),
      { interactive: false, preview: true }
    )
    // 无预览提示条、无禁用样式——预览与真实渲染同视觉
    expect(wrapper.find('.widget-card__preview-hint').exists()).toBe(false)
    const btns = wrapper.findAll('.widget-card__actions button')
    expect(btns[0]!.attributes('disabled')).toBeUndefined()
    // interactive=false：点击不 emit action（回传仍被交互窗口挡住）
    await btns[0]!.trigger('click')
    expect(wrapper.emitted('action')).toBeUndefined()
  })

  it('T3.3 非豁免本地动作在置灰窗口禁用；非法 url 忽略', async () => {
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => {})
    const noExempt = mountCard(formCard([{ type: 'sys.go_to_url', text: '跳转', url: 'https://a.b' }]), {
      interactive: false,
    })
    expect(noExempt.find('.widget-card__actions button').attributes('disabled')).toBeDefined()
    await noExempt.find('.widget-card__actions button').trigger('click')
    expect(openSpy).not.toHaveBeenCalled()

    const badUrl = mountCard(formCard([{ type: 'sys.go_to_url', text: '跳转', url: 'javascript:alert(1)' }]))
    await badUrl.find('.widget-card__actions button').trigger('click')
    expect(openSpy).not.toHaveBeenCalled()
  })
})

describe('S4 回传生命周期（T4.2/T4.3）', () => {
  it('T4.2 提交即 loading：二次点击不产生第二次 emit（防重，STATE-211）', async () => {
    const wrapper = mountCard(formCard([{ type: 'sys.submit', text: '确认' }]))
    const btn = wrapper.find('.widget-card__actions button')
    await btn.trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(1)
    await btn.trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(1) // 仍为 1
    expect(wrapper.find('.widget-card__actions button').text()).toContain('…')
  })

  it('T4.3 窗口重开（回传失败回滚）→ loading 解除、可再次提交（STATE-212）', async () => {
    const wrapper = mountCard(formCard([{ type: 'sys.submit', text: '确认' }]))
    await wrapper.find('.widget-card__actions button').trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(1)
    // 消息序回滚：interactive false→true 重开
    await wrapper.setProps({ interactive: false })
    await wrapper.setProps({ interactive: true })
    await wrapper.find('.widget-card__actions button').trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(2)
  })
})

describe('S3 表单控件族（T1.5 补充：Select/RadioGroup/Checkbox 归集）', () => {
  it('Input/Checkbox/RadioGroup 值统一归集进提交 payload', async () => {
    const wrapper = mountCard(
      snapOf({
        type: 'Form',
        props: {},
        children: [
          { type: 'Input', props: { name: 'note', defaultValue: '默认备注' } },
          { type: 'Checkbox', props: { name: 'dine_in', text: '堂食' } },
          {
            type: 'RadioGroup',
            props: { name: 'spice', options: [{ label: '微辣', value: 'mild' }, { label: '重辣', value: 'hot' }] },
          },
          { type: 'Button', props: { text: '提交', action: 'sys.submit' } },
        ],
      })
    )
    await wrapper.find('input[type="text"]').setValue('不要香菜')
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.findAll('input[type="radio"]')[1]!.setValue(true)
    await wrapper.find('button').trigger('click')
    const action = wrapper.emitted('action')![0][0] as Record<string, unknown>
    expect(action.action).toBe('sys.submit')
    expect(action.payload).toMatchObject({ note: '不要香菜', dine_in: true, spice: 'hot' })
  })
})
