import { describe, it, expect } from 'vitest'
import { buildSessionMarkdown, type SessionExportMessage } from './sessionMarkdown'

// 契约锚点：docs/design/app-chat-optimize-frontend.md Step 2——
// buildSessionMarkdown 纯函数：# 会话标题 / **用户：**·助手分节 / 来源文件名列表；
// 三态输入：空消息 / 含来源 / 含思考。

describe('buildSessionMarkdown', () => {
  it('空消息：无标题返回空串，有标题仅输出标题行', () => {
    expect(buildSessionMarkdown([])).toBe('')
    expect(buildSessionMarkdown([], '测试会话')).toBe('# 测试会话')
  })

  it('含来源：助手分节带「> 来源：」文件名列表，去重保序', () => {
    const messages: SessionExportMessage[] = [
      { role: 'user', content: '什么是知识图谱？' },
      {
        role: 'assistant',
        content: '知识图谱是实体关系网络。',
        sources: [
          { fileName: 'kg.pdf' },
          { fileName: 'intro.docx' },
          { fileName: 'kg.pdf' }, // 重复文件名应去重
        ],
      },
    ]
    const md = buildSessionMarkdown(messages, '问答')
    expect(md).toContain('# 问答')
    expect(md).toContain('**用户：**\n\n什么是知识图谱？')
    expect(md).toContain('**助手：**\n\n知识图谱是实体关系网络。')
    expect(md).toContain('> 来源：kg.pdf、intro.docx')
    expect(md).not.toContain('kg.pdf、kg.pdf')
    // 分节间以水平线分隔
    expect(md).toContain('---')
  })

  it('含思考：思考过程分节随助手消息导出；与来源可叠加', () => {
    const messages: SessionExportMessage[] = [
      {
        role: 'assistant',
        content: '结论内容',
        thinkingContent: '先分析问题背景',
        references: [{ fileName: 'ref.pdf' }],
      },
    ]
    const md = buildSessionMarkdown(messages)
    expect(md).toContain('**思考过程：**\n\n先分析问题背景')
    // 分节顺序：助手标题 → 正文 → 思考过程 → 来源
    expect(md.indexOf('**助手：**')).toBeLessThan(md.indexOf('**思考过程：**'))
    // sources 缺省时回退 references
    expect(md).toContain('> 来源：ref.pdf')
  })

  it('未知角色按原值作前缀；content 空值容错为空串', () => {
    const md = buildSessionMarkdown([
      { role: 'system', content: '' },
      { role: 'user', content: 'hi' },
    ])
    expect(md).toContain('**system：**')
    expect(md).toContain('**用户：**\n\nhi')
  })
})
