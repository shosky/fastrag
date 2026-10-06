import { describe, it, expect } from 'vitest'
import { parseSSEBuffer, parseSingleSSEEvent } from './sse'

// 契约锚点：docs/contract/ui.md 特性 19（new-conversation）Data Mapping——
// 九类 SSE 事件的分帧解析为 useAppChatStream / useConversationStream 共享实现。

describe('parseSingleSSEEvent', () => {
  it('解析 event+data 并默认 message 事件名', () => {
    expect(parseSingleSSEEvent('event: sources\ndata: {"a":1}')).toEqual({
      event: 'sources',
      data: '{"a":1}',
    })
    expect(parseSingleSSEEvent('data: 纯文本')).toEqual({ event: 'message', data: '纯文本' })
  })

  it('无 data 字段的事件返回 null（如纯 id/retry 行）', () => {
    expect(parseSingleSSEEvent('event: ping')).toBeNull()
    expect(parseSingleSSEEvent('id: 42\nretry: 100')).toBeNull()
  })

  it('多行 data 以换行拼接', () => {
    expect(parseSingleSSEEvent('data: 行一\ndata: 行二')).toEqual({
      event: 'message',
      data: '行一\n行二',
    })
  })
})

describe('parseSSEBuffer', () => {
  it('解析完整多事件 buffer', () => {
    const result = parseSSEBuffer(
      'event: init\ndata: {}\n\nevent: message\ndata: {"content":"你好"}\n\n'
    )
    expect(result.parsed).toHaveLength(2)
    expect(result.parsed[0]).toEqual({ event: 'init', data: '{}' })
    expect(result.parsed[1]).toEqual({ event: 'message', data: '{"content":"你好"}' })
    expect(result.remainder).toBe('')
  })

  it('无 data 行的尾帧保留在 remainder（跨 chunk 事件不丢失）', () => {
    // 实现口径：尾帧仅含 event 行（data 行尚未到达）时才留 remainder；
    // 已含 data 的尾帧按可解析事件交付（截断 JSON 由消费方 JSON.parse 容错）
    const result = parseSSEBuffer(
      'event: message\ndata: {"content":"完整"}\n\nevent: message\n'
    )
    expect(result.parsed).toHaveLength(1)
    expect(result.remainder).toContain('event: message')
  })

  it('尾帧已含 data 时按可解析事件交付（固化现状行为）', () => {
    const result = parseSSEBuffer(
      'event: message\ndata: {"content":"完整"}\n\nevent: message\ndata: {"content":"尾帧"}'
    )
    expect(result.parsed).toHaveLength(2)
    expect(result.remainder).toBe('')
  })

  it('空 buffer 与纯空白返回空结果', () => {
    expect(parseSSEBuffer('').parsed).toHaveLength(0)
    expect(parseSSEBuffer('\n\n').parsed).toHaveLength(0)
  })

  it('end 事件后跟随的事件仍按帧独立解析', () => {
    // 流式链路：end 后 reader.cancel，但解析器本身不截断——正交职责
    const result = parseSSEBuffer('event: end\ndata: {"content":"答"}\n\nevent: error\ndata: {"message":"x"}\n\n')
    expect(result.parsed.map((e) => e.event)).toEqual(['end', 'error'])
  })
})
