import { ref, computed } from 'vue'
import type { SearchResultItem, RetrievalRequest } from '@/types/evaluation'
import type { RetrievalConfig } from '@/types/knowledge'
import { searchRetrieval } from '@/api'

// ===========================================================================
// 多轮对话 composable
//
// 管理对话消息列表、上下文窗口、多轮检索（当前问题 + 历史摘要拼接查询）。
// ===========================================================================

export interface ConversationMessage {
  role: 'user' | 'assistant'
  content: string
  /** 回答引用的检索结果 */
  sources?: SearchResultItem[]
  /** 模型名称 */
  model?: string
  /** 响应耗时 */
  time?: string
  /** 时间戳 */
  timestamp: string
}

/** 上下文窗口大小：保留最近 N 轮对话作为上下文 */
const CONTEXT_WINDOW = 3

export function useConversation(kbId: string = 'default') {
  const messages = ref<ConversationMessage[]>([])
  const loading = ref(false)

  /** 对话轮数 */
  const turnCount = computed(() =>
    messages.value.filter((m) => m.role === 'user').length,
  )

  /** 指代消解：简单规则，把"它/这/该"替换为上一轮提到的实体名词 */
  function resolveReference(query: string): string {
    if (messages.value.length === 0) return query
    // 找上一轮 assistant 的回复中出现的名词性短语
    const lastAssistant = [...messages.value].reverse().find((m) => m.role === 'assistant')
    if (!lastAssistant) return query

    // 简单规则：如果 query 以"它/这/该/其"开头，且有上一轮 sources，取 source 名称
    const pronounMatch = query.match(/^(它|这|该|其|这个|那个|上述)/)
    if (pronounMatch && lastAssistant.sources && lastAssistant.sources.length > 0) {
      const entity = lastAssistant.sources[0].source.replace(/\.[^.]+$/, '') // 去掉扩展名
      return query.replace(pronounMatch[0], entity) + ' ' + query
    }
    return query
  }

  /** 构建多轮上下文查询 */
  function buildContextQuery(currentQuery: string): string {
    if (messages.value.length === 0) return currentQuery

    // 只在指代消解生效时（resolveReference 已把代词替换为实体）使用 currentQuery
    // 不再把历史问题拼接到 query 中，避免稀释当前问题的检索相关性
    // 历史上下文通过上一轮 sources 的内容隐式传递（resolveReference 已处理）
    return currentQuery
  }

  /**
   * 发送消息并获取回答。
   * 流程：指代消解 → 多轮上下文拼接 → 检索 → 组装回答。
   */
  async function sendMessage(
    query: string,
    config: RetrievalConfig,
    model: string = 'qwen3-32b',
  ) {
    if (!query.trim()) return

    // 添加用户消息
    const userMsg: ConversationMessage = {
      role: 'user',
      content: query,
      timestamp: new Date().toLocaleTimeString('zh-CN'),
    }
    messages.value.push(userMsg)
    loading.value = true

    try {
      // 1. 指代消解
      const resolvedQuery = resolveReference(query)

      // 2. 多轮上下文拼接
      const contextQuery = buildContextQuery(resolvedQuery)

      // 3. 检索
      const startTime = Date.now()
      const sources = await searchRetrieval({
        knowledgeId: kbId,
        query: contextQuery,
        config,
      })
      const elapsed = ((Date.now() - startTime) / 1000).toFixed(1)

      // 4. 组装回答（从 sources 拼接，模拟 LLM 生成）
      const answer = generateAnswer(query, sources)

      const assistantMsg: ConversationMessage = {
        role: 'assistant',
        content: answer,
        sources,
        model,
        time: `${elapsed}s`,
        timestamp: new Date().toLocaleTimeString('zh-CN'),
      }
      messages.value.push(assistantMsg)
    } catch {
      const errorMsg: ConversationMessage = {
        role: 'assistant',
        content: '抱歉，检索过程中出现错误，请稍后重试。',
        timestamp: new Date().toLocaleTimeString('zh-CN'),
      }
      messages.value.push(errorMsg)
    } finally {
      loading.value = false
    }
  }

  /** 清空对话历史 */
  function clear() {
    messages.value = []
  }

  return {
    messages,
    loading,
    turnCount,
    sendMessage,
    clear,
  }
}

/**
 * 模拟 LLM 生成回答：从检索结果中拼接上下文，生成简洁回答。
 * 真实环境应调用 LLM API，这里用规则拼接。
 */
function generateAnswer(question: string, sources: SearchResultItem[]): string {
  if (sources.length === 0) {
    return `抱歉，知识库中暂未找到与"${question}"相关的内容。建议您尝试换一种表述方式提问。`
  }

  // 取 top 3 结果拼接
  const topSources = sources.slice(0, 3)
  const contextParts = topSources.map((s) => s.content)

  // 根据问题类型生成不同风格的回答
  const isWhatQuestion = question.includes('什么') || question.includes('是什么')
  const isHowQuestion = question.includes('怎么') || question.includes('如何')
  const isPriceQuestion = question.includes('价格') || question.includes('多少钱') || question.includes('费用')

  let intro = ''
  if (isWhatQuestion) {
    intro = `关于"${question.replace(/[？?]/, '')}"：`
  } else if (isHowQuestion) {
    intro = `关于您的问题，以下是相关信息：`
  } else if (isPriceQuestion) {
    intro = `根据知识库信息，相关内容如下：`
  } else {
    intro = `根据知识库中的信息：`
  }

  // 从 sources 中提取关键句子
  const keySentences = contextParts.map((content) => {
    // 取前 2 个句子
    const sentences = content.split(/[。.！!]/).filter((s) => s.trim().length > 4)
    return sentences.slice(0, 2).join('。') + '。'
  })

  return intro + '\n\n' + keySentences.join('\n\n')
}
