import { ref } from 'vue'
import { storage } from '@/utils/storage'
import { parseSSEBuffer } from '@/utils/sse'

// 事件词汇与 useAppChatStream 完全一致（REQ-C02 零新增），类型直接复用
export type { ToolCallEvent, ToolResultEvent, AgentStateEvent, SourceItem } from '@/composables/useAppChatStream'
import type { ToolCallEvent, ToolResultEvent, AgentStateEvent, SourceItem } from '@/composables/useAppChatStream'

// ===========================================================================
// 会话中心流式对话 composable（new-conversation 新通道）
//
// fetch POST /conversations/{id}/stream（fetch 直连不走 axios，先例
// api/index.ts aiChunkStreamUrl / useAppChatStream）。后端契约：
// fastrag-backend docs/contract/interfaces.md ## new-conversation
// runStreamByConversation —— 事件序列与旧通道同词汇（九类），零新增。
// ===========================================================================

/** 单轮请求态快照（随消息提交，发送成功不本地回写，以后端固化 request_state 为准） */
export interface ConversationTurnPayload {
  query: string
  appId?: string | null
  kbIds?: string[]
  modelId?: string | null
  /** 直聊消费（B02 演进）：装配进执行上下文的工具/MCP/技能 ID；挂应用时能力走应用绑定不传 */
  toolIds?: string[]
  mcpIds?: string[]
  skillIds?: string[]
}

/**
 * 会话中心流式回调接口（与 AppChatCallbacks 同构）
 */
export interface ConversationStreamCallbacks {
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

export function useConversationStream() {
  const isStreaming = ref(false)
  const abortController = ref<AbortController | null>(null)

  /**
   * 发送一轮消息并通过 SSE 流式接收回答
   * @param conversationId 会话ID（随首条消息建会话后由页面传入）
     * @param payload 本轮请求态快照 { query, appId?, kbIds?, modelId?, toolIds?, mcpIds?, skillIds? }
   * @param callbacks 事件回调（onChunk/onEnd/onError 必填，其余可选）
   */
  async function send(
    conversationId: string,
    payload: ConversationTurnPayload,
    callbacks: ConversationStreamCallbacks
  ) {
    abortController.value = new AbortController()
    isStreaming.value = true

    const token = storage.get<string>('token')
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
    const url = `${baseUrl}/conversations/${conversationId}/stream`

    const { onChunk, onEnd, onError, onThinking, onToolCall, onToolResult, onAgentState, onSources } = callbacks

    let fullContent = ''

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          query: payload.query,
          appId: payload.appId || undefined,
          kbIds: payload.kbIds && payload.kbIds.length > 0 ? payload.kbIds : undefined,
          modelId: payload.modelId || undefined,
          // 直聊能力消费（B02 演进）：空数组发 undefined（不占契约字段面）
          toolIds: payload.toolIds && payload.toolIds.length > 0 ? payload.toolIds : undefined,
          mcpIds: payload.mcpIds && payload.mcpIds.length > 0 ? payload.mcpIds : undefined,
          skillIds: payload.skillIds && payload.skillIds.length > 0 ? payload.skillIds : undefined,
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

  /** 停止流式输出（前端断流；后端 stop 接口由页面另行调用，双保险） */
  function stopStream() {
    if (abortController.value) {
      abortController.value.abort()
      abortController.value = null
    }
    isStreaming.value = false
  }

  return {
    isStreaming,
    send,
    stopStream,
  }
}
