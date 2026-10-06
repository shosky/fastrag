// @vitest-environment jsdom
// 集成用例经 renderMarkdown（DOMPurify 需 DOM 实现，沿 markdown.test.ts 先例）；
// injectCitationRefs 本体为纯字符串函数。
import { describe, it, expect } from 'vitest'
import { injectCitationRefs } from './citeSources'
import { renderMarkdown } from './markdown'

// 契约锚点：docs/contract/ui.md 特性 19 State Contract「引用角标」——
// 消毒后 HTML 中 [N]（N ≤ 来源数）替换为 sup.cite-ref；N 超出来源数不替换
// （防模型幻觉编号伪装成引用）；替换在 sanitize 之后，产物只含自有安全 HTML。

describe('injectCitationRefs（引用角标注入）', () => {
  it('N ≤ 来源数：[N] 替换为 sup.cite-ref（自有安全 HTML，含 title）', () => {
    const out = injectCitationRefs('<p>结论一 [1]。结论二 [2]。</p>', 2)
    expect(out).toContain('<sup class="cite-ref" title="来源 1">[1]</sup>')
    expect(out).toContain('<sup class="cite-ref" title="来源 2">[2]</sup>')
    expect(out.startsWith('<p>')).toBe(true)
  })

  it('N 超出来源数不替换（防幻觉编号）', () => {
    const html = '<p>命中 [1]，幻觉 [3]。</p>'
    const out = injectCitationRefs(html, 2)
    expect(out).toContain('<sup class="cite-ref" title="来源 1">[1]</sup>')
    expect(out).toContain('幻觉 [3]。')
    expect(out).not.toContain('来源 3')
  })

  it('[0] 与超界三位数不替换', () => {
    const html = '<p>零 [0] 超界 [100]</p>'
    expect(injectCitationRefs(html, 5)).toBe(html)
  })

  it('无来源（sourceCount=0）或空 HTML 原样返回', () => {
    const html = '<p>引用 [1]</p>'
    expect(injectCitationRefs(html, 0)).toBe(html)
    expect(injectCitationRefs('', 5)).toBe('')
  })

  it('同一 N 多次出现全部替换', () => {
    const out = injectCitationRefs('<p>[1] 与 [1]</p>', 1)
    expect(out.split('<sup class="cite-ref" title="来源 1">[1]</sup>').length - 1).toBe(2)
  })

  it('多位数来源号：[10] 在来源数足够时替换', () => {
    const out = injectCitationRefs('<p>第十条 [10]</p>', 10)
    expect(out).toContain('<sup class="cite-ref" title="来源 10">[10]</sup>')
  })

  it('HTML 标签/属性内的 [N] 不受影响（仅处理文本段）', () => {
    const html = '<a href="/file[1].pdf">链接[1]</a>'
    const out = injectCitationRefs(html, 5)
    expect(out).toContain('href="/file[1].pdf"')
    expect(out).toContain('<sup class="cite-ref" title="来源 1">[1]</sup>')
  })

  it('code/pre 内的 [N] 不替换（代码示例不是引用标注）', () => {
    const html = '<p>见 [1]</p><pre><code>arr[1] = x[2]</code></pre>'
    const out = injectCitationRefs(html, 5)
    expect(out).toContain('<sup class="cite-ref" title="来源 1">[1]</sup>')
    expect(out).toContain('<code>arr[1] = x[2]</code>')
  })

  it('嵌套 pre>code 关闭后仍正确恢复文本态', () => {
    const html = '<pre><code>a[1]</code></pre><p>b[1]</p><pre>c[2]</pre>'
    const out = injectCitationRefs(html, 5)
    expect(out).toContain('<code>a[1]</code>')
    expect(out).toContain('<p>b<sup class="cite-ref" title="来源 1">[1]</sup></p>')
    expect(out).toContain('<pre>c[2]</pre>')
  })
})

describe('injectCitationRefs × renderMarkdown（消毒后注入，端到端）', () => {
  it('markdown 正文中的 [N] 在消毒后 HTML 里替换为角标', () => {
    const html = renderMarkdown('这是一句带引用的结论 [1]。\n\n另一段 [2]。')
    const out = injectCitationRefs(html, 2)
    expect(out).toContain('<sup class="cite-ref" title="来源 1">[1]</sup>')
    expect(out).toContain('<sup class="cite-ref" title="来源 2">[2]</sup>')
  })

  it('角标产物自身安全：不含脚本/内联事件', () => {
    const out = injectCitationRefs('<p>结论 [1]</p>', 1)
    expect(out).not.toContain('<script')
    expect(out).not.toContain('onerror')
    expect(out).not.toContain('onclick')
  })
})
