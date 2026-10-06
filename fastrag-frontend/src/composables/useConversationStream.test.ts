import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useConversationStream } from './useConversationStream'

// storage 为模块级依赖：文件级 mock（node 环境无 localStorage）
vi.mock('@/utils/storage', () => ({ storage: { get: vi.fn(() => 'test-token') } }))

// 契约锚点：docs/contract/ui.md 特性 19（new-conversation）State Contract + API Dependency——
// POST /conversations/{id}/stream，九类事件词汇与 useAppChatStream 同一封闭集（REQ-C02）。

const encoder = new TextEncoder()

/** 构造 fetch Response 形状的最小替身：按 chunks 顺序吐 SSE 文本帧 */
function sseResponse(chunks: string[]) {
  let i = 0
  return {
    ok: true,
    status: 200,
    text: async () => '',
    body: {
      getReader: () => ({
        read: async () =>
          i < chunks.length
            ? { done: false, value: encoder.encode(chunks[i++]) }
            : { done: true, value: undefined },
        cancel: async () => {},
      }),
    },
  }
}

describe('useConversationStream', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('happy path：增量累计、sources/end 分发、isStreaming 复位', async () => {
    fetchMock.mockResolvedValue(
      sseResponse([
        'event: sources\ndata: {"sources":[{"fileName":"a.pdf","score":0.9}]}\n\n',
        'event: message\ndata: {"content":"知识图谱"}\n\n',
        'event: message\ndata: {"content":"是实体关系网络。"}\n\nevent: end\ndata: {"content":"知识图谱是实体关系网络。"}\n\n',
      ])
    )
    const chunks: string[] = []
    let sources: unknown
    let ended: unknown

    const { send, isStreaming } = useConversationStream()
    const pending = send(
      'conv-1',
      { query: '什么是知识图谱', kbIds: ['kb-1'] },
      {
        onChunk: (c) => chunks.push(c),
        onSources: (s) => (sources = s),
        onEnd: (_c, d) => (ended = d),
        onError: () => expect.fail('不应触发 onError'),
      }
    )
    await pending
    await vi.waitFor(() => expect(isStreaming.value).toBe(false))

    expect(chunks).toEqual(['知识图谱', '是实体关系网络。'])
    expect(sources).toEqual([{ fileName: 'a.pdf', score: 0.9 }])
    expect((ended as Record<string, unknown>).content).toBe('知识图谱是实体关系网络。')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/conversations/conv-1/stream'),
      expect.objectContaining({ method: 'POST' })
    )
  })

  it('replace=true 时整段替换累计内容（敏感词修正语义）', async () => {
    fetchMock.mockResolvedValue(
      sseResponse([
        'event: message\ndata: {"content":"原始"}\n\n',
        'event: message\ndata: {"content":"过滤后全文","replace":true}\n\n',
        'event: end\ndata: {}\n\n',
      ])
    )
    const chunks: string[] = []
    let final = ''
    const { send } = useConversationStream()
    await send('c1', { query: 'q' }, { onChunk: (c, r) => chunks.push(r ? `R:${c}` : c), onEnd: (c) => (final = c), onError: () => {} })

    expect(chunks).toEqual(['原始', 'R:过滤后全文'])
    expect(final).toBe('过滤后全文')
  })

  it('error 事件走 onError 并终止读取', async () => {
    fetchMock.mockResolvedValue(
      sseResponse(['event: error\ndata: {"message":"模型不可用"}\n\n'])
    )
    const errors: string[] = []
    const { send } = useConversationStream()
    await send('c1', { query: 'q' }, { onChunk: () => {}, onEnd: () => {}, onError: (m) => errors.push(m) })

    expect(errors).toEqual(['模型不可用'])
  })

  it('非 2xx 响应走 onError 并携带状态码', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 404, text: async () => 'not found' })
    const errors: string[] = []
    const { send } = useConversationStream()
    await send('missing', { query: 'q' }, { onChunk: () => {}, onEnd: () => {}, onError: (m) => errors.push(m) })

    expect(errors[0]).toContain('404')
  })

  it('stopStream 触发 AbortError → onEnd({aborted:true})，已生成部分保留', async () => {
    fetchMock.mockImplementation(
      (_url: string, init?: { signal?: AbortSignal }) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted')
            e.name = 'AbortError'
            reject(e)
          })
        })
    )
    const ends: Array<Record<string, unknown>> = []
    const { send, stopStream, isStreaming } = useConversationStream()
    const pending = send('c1', { query: 'q' }, {
      onChunk: () => {},
      onEnd: (c, d) => ends.push({ ...d, content: c }),
      onError: () => expect.fail('不应触发 onError'),
    })
    stopStream()
    await pending

    expect(ends[0]?.aborted).toBe(true)
    expect(isStreaming.value).toBe(false)
  })

  it('直聊能力三字段随请求体透传（B02 演进）；空数组发 undefined', async () => {
    fetchMock.mockResolvedValue(sseResponse(['event: end\ndata: {}\n\n']))
    const { send } = useConversationStream()
    const noop = { onChunk: () => {}, onEnd: () => {}, onError: () => {} }

    // 非空：原样透传
    await send('c1', { query: 'q', toolIds: ['t1'], mcpIds: ['m1'], skillIds: ['s1'] }, noop)
    const withIds = JSON.parse((fetchMock.mock.calls[0]?.[1] as { body: string }).body)
    expect(withIds).toMatchObject({ toolIds: ['t1'], mcpIds: ['m1'], skillIds: ['s1'] })

    // 空数组：归一为 undefined（不占契约字段面）
    await send('c1', { query: 'q', toolIds: [], mcpIds: [], skillIds: [] }, noop)
    const empty = JSON.parse((fetchMock.mock.calls[1]?.[1] as { body: string }).body)
    expect(empty).not.toHaveProperty('toolIds')
    expect(empty).not.toHaveProperty('mcpIds')
    expect(empty).not.toHaveProperty('skillIds')
  })
})
