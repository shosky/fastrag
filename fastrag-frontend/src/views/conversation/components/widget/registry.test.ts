import { describe, it, expect } from 'vitest'
import { registry, registryKeys } from './registry'
import { BACKEND_WIDGET_COMPONENT_WHITELIST } from './whitelist'

// S2 白名单↔注册表守护（widget-frontend TDD T2.1，REQ-WG-A06 前端半边）。
// 双份常量集合相等：后端白名单快照（whitelist.ts fixture）vs 前端渲染注册表键集。
// 单侧多出/缺失即失败——新增组件必须两端各加一项 + 本文件各加一条断言。

describe('注册表↔后端白名单守护（T2.1）', () => {
  it('registry 键集 === 后端白名单常量集（单侧多出即失败）', () => {
    expect(registryKeys()).toEqual([...BACKEND_WIDGET_COMPONENT_WHITELIST].sort())
  })

  it('白名单 19 项无重复、无空名', () => {
    expect(new Set(BACKEND_WIDGET_COMPONENT_WHITELIST).size).toBe(BACKEND_WIDGET_COMPONENT_WHITELIST.length)
    expect(BACKEND_WIDGET_COMPONENT_WHITELIST.every((n) => n.length > 0)).toBe(true)
  })

  it('注册表每项值均为组件（非 undefined——漏 import/拼错名在此暴露）', () => {
    for (const key of registryKeys()) {
      expect(registry[key], `registry["${key}"] 未注册`).toBeTruthy()
    }
  })

  it('容器/布局/文本/内容/交互/表单六族组件齐备（19 个 SFC）', () => {
    expect(registryKeys()).toEqual(
      [
        'Badge', 'Button', 'Caption', 'Card', 'Checkbox', 'Col', 'Divider', 'Form',
        'Icon', 'Image', 'Input', 'ListView', 'ListViewItem', 'Markdown', 'RadioGroup',
        'Row', 'Select', 'Text', 'Title',
      ].sort()
    )
  })
})
