// ===========================================================================
// SSE 解析共享工具
//
// 抽取自 useAppChatStream.ts（原 334-378 行），供应用对话流（useAppChatStream）、
// AI 分片流（AiChunkPanel）与会话中心流（useConversationStream）等 fetch SSE
// 消费方共用，避免第三份手写分帧（feature-map :117 在案）。
// ===========================================================================

export interface SSEEvent {
  event: string
  data: string
}

export interface SSEParseResult {
  parsed: SSEEvent[]
  remainder: string
}

/**
 * 解析 SSE 格式的 buffer
 * SSE 格式：
 *   event: xxx\n
 *   data: xxx\n
 *   \n（空行分隔事件）
 *
 * 严格分帧（R5，2026-10-20）：仅交付「空行收尾」的完整事件；最后一段无论是否已含
 * data 行，一律留 remainder 等后续 chunk 补全。旧行为会把跨 TCP 包截断的半截 JSON
 * 事件交付给消费方——JSON.parse 失败被 catch 静默丢弃且残余字节无法找回，
 * sources / tool_result 这类大事件几乎必然中招（引用卡片不显示、工具卡片卡
 * 「执行中」即此因）。流结束时的未收尾尾帧由消费方自行兜底解析（见 useAppChatStream）。
 */
export function parseSSEBuffer(buffer: string): SSEParseResult {
  const result: SSEParseResult = { parsed: [], remainder: '' }

  // 按双换行分割事件：最后一段是未确认收尾的尾帧，恒留 remainder
  const parts = buffer.split('\n\n')
  result.remainder = parts.pop() || ''

  for (const part of parts) {
    const event = parseSingleSSEEvent(part.trim())
    if (event) {
      result.parsed.push(event)
    }
  }

  return result
}

export function parseSingleSSEEvent(text: string): SSEEvent | null {
  const lines = text.split('\n')
  let event = ''
  let data = ''

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.substring(6).trim()
    } else if (line.startsWith('data:')) {
      data += (data ? '\n' : '') + line.substring(5).trim()
    }
    // 忽略 id:、retry: 等其他字段
  }

  if (!data) return null
  return { event: event || 'message', data }
}
