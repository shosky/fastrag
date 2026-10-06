// @vitest-environment jsdom
// DOMPurify 需要 DOM 实现（document/DOMParser）：全局 vitest 环境保持 node
// （vitest.config.ts 既有约定），本文件是首个需要 DOM 的测试，按 config 注释
// 「引入组件级测试时再切换」以文件级注解局部启用 jsdom（DOMPurify 官方测试基线；
// happy-dom 下其模板解析存在 img 整体丢弃/属性残留问题，故不采用）。
import { describe, it, expect } from 'vitest'
import { renderMarkdown } from './markdown'

// 契约锚点：docs/design/app-chat-optimize-frontend.md Step 1 验收——
// <script>/<img onerror>/javascript: 三类样例消毒后无残留；正常 md 渲染不变。

describe('renderMarkdown 消毒', () => {
  it('剥离 <script> 标签', () => {
    const html = renderMarkdown('前文\n\n<script>alert(1)</script>\n\n后文')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('alert(1)<')
    expect(html).toContain('前文')
    expect(html).toContain('后文')
  })

  it('剥离 img 内联 onerror 属性', () => {
    const html = renderMarkdown('<img src="x.png" onerror="alert(1)">')
    expect(html).not.toContain('onerror')
    expect(html).toContain('<img')
    expect(html).toContain('src="x.png"')
  })

  it('剥离 javascript: 链接协议（href 被移除）', () => {
    const html = renderMarkdown('[点我](javascript:alert(1))')
    expect(html).not.toContain('javascript:')
    expect(html).toContain('<a')
    expect(html).toContain('点我')
  })

  it('剥离 onclick/onload 与 style/iframe（FORBID 清单兜底）', () => {
    const html = renderMarkdown(
      '<div onclick="alert(1)">t</div><iframe src="https://evil.example"></iframe>'
    )
    expect(html).not.toContain('onclick')
    expect(html).not.toContain('<iframe')
  })
})

describe('renderMarkdown 正常渲染不受影响', () => {
  it('GFM 表格渲染为 table', () => {
    const html = renderMarkdown('| a | b |\n| --- | --- |\n| 1 | 2 |')
    expect(html).toContain('<table>')
    expect(html).toContain('<th>a</th>')
    expect(html).toContain('<td>2</td>')
  })

  it('代码块与行内代码渲染', () => {
    const html = renderMarkdown('```js\nconst a = 1\n```\n行内 `code` 标记')
    expect(html).toContain('<pre>')
    expect(html).toContain('<code>')
    expect(html).toContain('行内 <code>code</code> 标记')
  })

  it('链接与换行（breaks）渲染', () => {
    const html = renderMarkdown('[FastRAG](https://example.com)\n第二行')
    expect(html).toContain('<a href="https://example.com"')
    expect(html).toContain('FastRAG</a>')
    expect(html).toContain('<br')
  })

  it('空串入参返回空串', () => {
    expect(renderMarkdown('')).toBe('')
  })
})
