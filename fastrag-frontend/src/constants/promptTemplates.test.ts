import { describe, it, expect } from 'vitest'
import { PROMPT_TEMPLATES, PROMPT_TEMPLATE_NOTE, type PromptTemplate } from './promptTemplates'

// 契约锚点：docs/design/app-chat-optimize-frontend.md Step 3c——
// 内置 6 常量（RAG 问答/制度查询/多轮客服/内容摘要/对比分析/表格解读），
// 每个模板非空、含变量占位说明（{花括号} 占位符 + 面板展示的使用说明）。

describe('PROMPT_TEMPLATES 契约', () => {
  it('固定 6 个模板，且为设计指定的六类场景', () => {
    expect(PROMPT_TEMPLATES).toHaveLength(6)
    const names = PROMPT_TEMPLATES.map(t => t.name)
    expect(names).toEqual(['RAG 问答', '制度查询', '多轮客服', '内容摘要', '对比分析', '表格解读'])
  })

  it('每个模板 name 唯一且非空', () => {
    const names = PROMPT_TEMPLATES.map(t => t.name)
    expect(new Set(names).size).toBe(names.length)
    names.forEach(name => expect(name.trim().length).toBeGreaterThan(0))
  })

  it('每个模板 content 非空且含 {变量占位符}', () => {
    PROMPT_TEMPLATES.forEach((t: PromptTemplate) => {
      expect(t.content.trim().length).toBeGreaterThan(0)
      expect(t.content).toMatch(/\{[^{}]+\}/)
    })
  })

  it('占位符使用说明存在且可指导替换', () => {
    expect(PROMPT_TEMPLATE_NOTE.trim().length).toBeGreaterThan(0)
    expect(PROMPT_TEMPLATE_NOTE).toContain('占位符')
  })

  it('模板正文为可直接使用的多句文案（至少 3 句）', () => {
    PROMPT_TEMPLATES.forEach(t => {
      const sentenceCount = t.content.split(/[。；!?！？]/).filter(s => s.trim().length > 0).length
      expect(sentenceCount).toBeGreaterThanOrEqual(3)
    })
  })
})
