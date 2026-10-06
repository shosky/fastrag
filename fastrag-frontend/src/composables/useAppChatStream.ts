import { ref } from 'vue'
import { storage } from '@/utils/storage'
import { parseSSEBuffer } from '@/utils/sse'

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
  /** 结构化数据（app-chat-optimize 二批）：后端透传，专用渲染器消费，前端不加工 */
  data?: Record<string, unknown>
}

export interface AgentStateEvent {
  todos?: Array<{ content: string; status: string }>
  artifacts?: string[]
}

/** 知识来源条目（RAG 检索结果） */
export interface SourceItem {
  fileName?: string
  score?: number
  content?: string
  fileId?: string
  kbId?: string
  chunkIndex?: number
  chunkType?: string
  imageKeys?: string[]
  /** 本地 UI 状态：是否展开全文（非后端字段） */
  _expanded?: boolean
}

/**
 * 应用对话流式回调接口
 */
export interface AppChatCallbacks {
  /** 文本增量回调；isReplace=true 时 content 为整段替换后的全文（敏感词过滤修正） */
  onChunk: (content: string, isReplace?: boolean) => void
  onEnd: (fullContent: string, data: Record<string, unknown>) => void
  onError: (message: string) => void
  onThinking?: (content: string) => void
  onToolCall?: (toolCall: ToolCallEvent) => void
  onToolResult?: (result: ToolResultEvent) => void
  onAgentState?: (state: AgentStateEvent) => void
  onSources?: (sources: SourceItem[]) => void
}

/**
 * SSE 流式对话 composable
 * 用于应用中心的流式对话功能
 * 支持扩展事件：thinking / tool_call / tool_result / agent_state / sources
 */
export function useAppChatStream() {
  const isStreaming = ref(false)
  const abortController = ref<AbortController | null>(null)

  /**
   * 发送消息并通过 SSE 流式接收回答
   * @param appId 应用ID
   * @param query 用户消息
   * @param sessionId 会话ID
   * @param onChunk 每个文本增量回调；第二参 isReplace=true 表示整段替换（敏感词过滤修正）
   * @param onEnd 流式完成的回调
   * @param onError 错误回调
   * @param onThinking 思考过程增量回调（可选）
   * @param onToolCall 工具调用开始回调（可选）
   * @param onToolResult 工具执行结果回调（可选）
   * @param onSources 知识来源回调（可选）
   * @param onAgentState 智能体状态变更回调（可选）
   */
  async function sendMessage(
    appId: string,
    query: string,
    sessionId: string | null,
    onChunk: (content: string, isReplace?: boolean) => void,
    onEnd: (fullContent: string, data: Record<string, unknown>) => void,
    onError: (message: string) => void,
    onThinking?: (content: string) => void,
    onToolCall?: (toolCall: ToolCallEvent) => void,
    onToolResult?: (result: ToolResultEvent) => void,
    onSources?: (sources: SourceItem[]) => void,
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
                  if (data.replace) {
                    // 敏感词过滤修正：整段替换已累计内容
                    fullContent = data.content
                    onChunk(data.content, true)
                  } else {
                    fullContent += data.content
                    onChunk(data.content, false)
                  }
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
            case 'sources': {
              if (onSources) {
                try {
                  const data = JSON.parse(event.data) as { sources?: SourceItem[] }
                  onSources(Array.isArray(data.sources) ? data.sources : [])
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
// parseSSEBuffer / parseSingleSSEEvent 已抽取至 @/utils/sse 共享（会话中心等消费方复用）
