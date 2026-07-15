import { ref } from 'vue'
import { storage } from '@/utils/storage'

// ==================== 类型定义 ====================

export interface ToolCallEvent {
  id: string
  name: string
  arguments: string
  index: number
  status: string
  intercepted?: boolean
}

export interface ToolResultEvent {
  id: string
  name: string
  success: boolean
  output: string
  durationMs: number
  error?: string
  intercepted?: boolean
}

export interface AgentStateEvent {
  todos?: Array<{ content: string; status: string }>
  artifacts?: string[]
}

/**
 * 应用对话流式回调接口
 */
export interface AppChatCallbacks {
  onChunk: (content: string) => void
  onEnd: (fullContent: string, data: Record<string, unknown>) => void
  onError: (message: string) => void
  onThinking?: (content: string) => void
  onToolCall?: (toolCall: ToolCallEvent) => void
  onToolResult?: (result: ToolResultEvent) => void
  onAgentState?: (state: AgentStateEvent) => void
}

/**
 * SSE 流式对话 composable
 * 用于应用中心的流式对话功能
 * 支持扩展事件：thinking / tool_call / tool_result / agent_state
 */
export function useAppChatStream() {
  const isStreaming = ref(false)
  const abortController = ref<AbortController | null>(null)

  /**
   * 发送消息并通过 SSE 流式接收回答
   * @param appId 应用ID
   * @param query 用户消息
   * @param sessionId 会话ID
   * @param onChunk 每个文本增量的回调
   * @param onEnd 流式完成的回调
   * @param onError 错误回调
   * @param onThinking 思考过程增量回调（可选）
   * @param onToolCall 工具调用开始回调（可选）
   * @param onToolResult 工具执行结果回调（可选）
   * @param onAgentState 智能体状态变更回调（可选）
   */
  async function sendMessage(
    appId: string,
    query: string,
    sessionId: string | null,
    onChunk: (content: string) => void,
    onEnd: (fullContent: string, data: Record<string, unknown>) => void,
    onError: (message: string) => void,
    onThinking?: (content: string) => void,
    onToolCall?: (toolCall: ToolCallEvent) => void,
    onToolResult?: (result: ToolResultEvent) => void,
    onAgentState?: (state: AgentStateEvent) => void
  ) {
    abortController.value = new AbortController()
    isStreaming.value = true

    const token = storage.get<string>('token')
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    const url = `${baseUrl}/apps/${appId}/chat/stream`

    let fullContent = ''

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          query,
          sessionId: sessionId || undefined,
        }),
        signal: abortController.value.signal,
      })

      if (!response.ok) {
        const errText = await response.text()
        onError(`请求失败 (${response.status}): ${errText}`)
        isStreaming.value = false
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        onError('无法读取响应流')
        isStreaming.value = false
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // 解析 SSE 格式
        const events = parseSSEBuffer(buffer)
        buffer = events.remainder

        for (const event of events.parsed) {
          switch (event.event) {
            case 'message': {
              try {
                const data = JSON.parse(event.data)
                if (data.content) {
                  fullContent += data.content
                  onChunk(data.content)
                }
              } catch {
                // 非 JSON 格式，直接作为文本
                fullContent += event.data
                onChunk(event.data)
              }
              break
            }
            case 'thinking': {
              if (onThinking) {
                try {
                  const data = JSON.parse(event.data)
                  if (data.content) {
                    onThinking(data.content)
                  }
                } catch {
                  // ignore
                }
              }
              break
            }
            case 'tool_call': {
              if (onToolCall) {
                try {
                  const data = JSON.parse(event.data) as ToolCallEvent
                  onToolCall(data)
                } catch {
                  // ignore
                }
              }
              break
            }
            case 'tool_result': {
              if (onToolResult) {
                try {
                  const data = JSON.parse(event.data) as ToolResultEvent
                  onToolResult(data)
                } catch {
                  // ignore
                }
              }
              break
            }
            case 'agent_state': {
              if (onAgentState) {
                try {
                  const data = JSON.parse(event.data) as AgentStateEvent
                  onAgentState(data)
                } catch {
                  // ignore
                }
              }
              break
            }
            case 'end': {
              if (event.data) {
                try {
                  const data = JSON.parse(event.data)
                  // end 事件中的 content 是完整回答
                  if (data.content && data.content !== fullContent) {
                    fullContent = data.content
                  }
                  onEnd(fullContent, data)
                } catch {
                  onEnd(fullContent, {})
                }
              } else {
                onEnd(fullContent, {})
              }
              // 收到 end 事件，主动结束读取
              reader.cancel().catch(() => {})
              break
            }
            case 'error': {
              if (event.data) {
                try {
                  const data = JSON.parse(event.data)
                  onError(data.message || '未知错误')
                } catch {
                  onError(event.data)
                }
              } else {
                onError('未知错误')
              }
              reader.cancel().catch(() => {})
              break
            }
            case 'init': {
              // 初始化事件，忽略
              break
            }
            default: {
              // 未知事件类型，忽略（向后兼容）
              break
            }
          }
        }
      }

      // 处理 buffer 中剩余的数据
      if (buffer.trim()) {
        const events = parseSSEBuffer(buffer)
        for (const event of events.parsed) {
          if (event.event === 'end' && event.data) {
            try {
              const data = JSON.parse(event.data)
              if (data.content && data.content !== fullContent) {
                fullContent = data.content
              }
              onEnd(fullContent, data)
            } catch {
              onEnd(fullContent, {})
            }
          }
        }
      }
    } catch (e: any) {
      if (e.name === 'AbortError') {
        // 用户主动取消
        onEnd(fullContent, { aborted: true })
      } else {
        onError(e.message || '网络错误')
      }
    } finally {
      isStreaming.value = false
      abortController.value = null
    }
  }

  /** 停止流式输出 */
  function stopStream() {
    if (abortController.value) {
      abortController.value.abort()
      abortController.value = null
    }
    isStreaming.value = false
  }

  return {
    isStreaming,
    sendMessage,
    stopStream,
  }
}

// ==================== SSE 解析辅助函数 ====================

interface SSEEvent {
  event: string
  data: string
}

interface SSEParseResult {
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
function parseSSEBuffer(buffer: string): SSEParseResult {
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

function parseSingleSSEEvent(text: string): SSEEvent | null {
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
