import { describe, it, expect, vi, afterEach } from 'vitest'
import {
  toWidgetSnapshot,
  toPreviewSnapshot,
  buildWidgetAction,
  parseTemplate,
  interpolate,
  mergeRenderData,
  isSubmitAction,
  isLocalAction,
  isUnknownAction,
} from './snapshot'

// S1 转换点直测（widget-frontend TDD T1.1~T1.4）。
// 契约锚点：ui.md「widget 链映射」三个钉名转换点 + ADR-0011-WIDGET-TEMPLATE-DSL。

afterEach(() => {
  vi.restoreAllMocks()
})

describe('toWidgetSnapshot（T1.1，API-012）', () => {
  const fullData = {
    widgetId: '1937',
    widgetKey: 'order-confirm',
    name: '订单确认卡',
    template: '{"type":"Card","props":{},"children":[]}',
    data: { order: 'A-1024', total: 42 },
  }

  it('五要素逐字段透传（实时 SSE 与历史 toolCalls 同构）', () => {
    const snap = toWidgetSnapshot({ success: true, output: '已生成卡片', durationMs: 45, data: fullData })
    expect(snap).toEqual(fullData)
  })

  it('success=false → null（STATE-207 路径不产卡，Error 文本回灌 LLM 前端忽略）', () => {
    expect(toWidgetSnapshot({ success: false, output: 'ERR', durationMs: 1 })).toBeNull()
  })

  it('无 data → null；result 缺失 → null', () => {
    expect(toWidgetSnapshot({ success: true, output: '', durationMs: 1 })).toBeNull()
    expect(toWidgetSnapshot(null)).toBeNull()
    expect(toWidgetSnapshot(undefined)).toBeNull()
  })

  it('五要素缺失/类型不符 → null + console.warn（fail-soft 不抛）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    expect(toWidgetSnapshot({ success: true, data: { widgetId: 'x' } })).toBeNull()
    expect(toWidgetSnapshot({ success: true, data: { ...fullData, template: 123 } })).toBeNull()
    expect(toWidgetSnapshot({ success: true, data: { ...fullData, data: [] } })).toBeNull()
    expect(warn).toHaveBeenCalled()
  })
})

describe('toPreviewSnapshot（T1.2，STATE-206 预览数据源）', () => {
  it('template+defaultJson → 快照且 preview 语义就位（合成标识 + 数据 = defaultJson）', () => {
    const snap = toPreviewSnapshot('{"type":"Card","props":{},"children":[]}', '{"city":"北京"}')
    expect(snap).not.toBeNull()
    expect(snap!.template).toBe('{"type":"Card","props":{},"children":[]}')
    expect(snap!.data).toEqual({ city: '北京' })
    expect(snap!.widgetId).toBe('__preview__')
    expect(snap!.widgetKey).toBe('__preview__')
    expect(typeof snap!.name).toBe('string')
  })

  it('坏模板 → null（编辑页预览面板显示占位）', () => {
    expect(toPreviewSnapshot('not-json', '{}')).toBeNull()
    expect(toPreviewSnapshot('', '{}')).toBeNull()
  })

  it('坏 defaultJson 归一空对象 + warn（模板仍可渲染）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const snap = toPreviewSnapshot('{"type":"Text","props":{"text":"{{a}}"}}', '{bad')
    expect(snap!.data).toEqual({})
    expect(warn).toHaveBeenCalled()
  })
})

describe('buildWidgetAction（T1.3，API-011）', () => {
  it('snapshot + 表单收集值 → {widgetId,widgetKey,action:sys.submit,payload}', () => {
    const snap = toWidgetSnapshot({
      success: true,
      data: {
        widgetId: '1937',
        widgetKey: 'order-confirm',
        name: '订单确认卡',
        template: '{}',
        data: {},
      },
    })!
    const action = buildWidgetAction(snap, { dine_in: true, note: '少辣' })
    expect(action).toEqual({
      widgetId: '1937',
      widgetKey: 'order-confirm',
      action: 'sys.submit',
      payload: { dine_in: true, note: '少辣' },
    })
  })

  it('表单值为空时 payload 为空对象（非表单卡合法回传）', () => {
    const snap = toPreviewSnapshot('{"type":"Card"}', '{}')!
    expect(buildWidgetAction(snap, {}).payload).toEqual({})
  })
})

describe('插值与合并次序（T1.4，快照 #16 ADR-0011）', () => {
  it('mergeRenderData：default 先填充、data 覆盖——同名出 data 值、default 独有出 default 值', () => {
    expect(mergeRenderData({ a: 'default-a', b: 'default-b' }, { b: 'data-b', c: 'data-c' })).toEqual({
      a: 'default-a',
      b: 'data-b',
      c: 'data-c',
    })
    expect(mergeRenderData(null, { x: 1 })).toEqual({ x: 1 })
    expect(mergeRenderData({ y: 2 }, undefined)).toEqual({ y: 2 })
  })

  it('interpolate：data 有值出 data 值；两侧皆无出空串（空串插值即隐藏文本）', () => {
    expect(interpolate('订单 {{order}} 共 {{total}} 元', { order: 'A-1024', total: 42 })).toBe('订单 A-1024 共 42 元')
    expect(interpolate('你好，{{name}}', {})).toBe('你好，')
    expect(interpolate('{{a.b}}', { a: { b: '深路径' } })).toBe('深路径')
    expect(interpolate('无占位', {})).toBe('无占位')
  })

  it('preview 路径合并：defaultJson 独有变量出 default 值，模板未填变量出空串', () => {
    const template = '{"type":"Text","props":{"text":"{{city}} / {{missing}}"}}'
    const snap = toPreviewSnapshot(template, '{"city":"北京"}')!
    const resolved = interpolate('{{city}} / {{missing}}', snap.data)
    expect(resolved).toBe('北京 / ')
  })

  it('parseTemplate：合法树返回节点；坏 JSON/非对象/缺 type → null', () => {
    expect(parseTemplate('{"type":"Card","props":{},"children":[]}')).toEqual({
      type: 'Card',
      props: {},
      children: [],
    })
    expect(parseTemplate('[1,2]')).toBeNull()
    expect(parseTemplate('{"props":{}}')).toBeNull()
    expect(parseTemplate('nope')).toBeNull()
  })
})

describe('动作语义判别（T3.3/T3.4 前置）', () => {
  it('sys.submit 提交类 / go_to_url+download 本地类 / 未知类型', () => {
    expect(isSubmitAction({ type: 'sys.submit' })).toBe(true)
    expect(isSubmitAction({ action: 'sys.submit' })).toBe(true)
    expect(isLocalAction({ type: 'sys.go_to_url', url: 'https://x' })).toBe(true)
    expect(isLocalAction({ type: 'sys.download', url: 'https://x/f.pdf' })).toBe(true)
    expect(isUnknownAction({ type: 'sys.explode' })).toBe(true)
    expect(isUnknownAction({ type: 'sys.submit' })).toBe(false)
    expect(isUnknownAction({})).toBe(false)
  })
})
