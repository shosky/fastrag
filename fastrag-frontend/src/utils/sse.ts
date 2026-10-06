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
 */
export function parseSSEBuffer(buffer: string): SSEParseResult {
  const result: SSEParseResult = { parsed: [], remainder: '' }

  // 按双换行分割事件
  const parts = buffer.split('\n\n')
  const lastPart = parts.pop() || ''

  for (const part of parts) {
    const event = parseSingleSSEEvent(part.trim())
    if (event) {
      result.parsed.push(event)
    }
  }

  // 最后一段可能不完整，保留在 remainder 中
  if (lastPart.trim()) {
    // 尝试解析最后一个完整事件
    const event = parseSingleSSEEvent(lastPart.trim())
    if (event) {
      result.parsed.push(event)
    } else {
      result.remainder = lastPart
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
