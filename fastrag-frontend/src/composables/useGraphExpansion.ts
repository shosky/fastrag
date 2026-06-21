import { ref } from 'vue'
import type { GraphExpansionResult } from '@/types/knowledge'
import { expandQueryWithGraph } from '@/api'

export function useGraphExpansion(knowledgeId: string = '1') {
  const expansionResult = ref<GraphExpansionResult | null>(null)
  const isExpanding = ref(false)
  const expandedQuery = ref('')

  // 扩展缓存
  const cache = new Map<string, GraphExpansionResult>()

  /**
   * 执行图谱扩展
   * @param query 原始查询
   * @returns 扩展后的查询
   */
  async function expandQuery(query: string): Promise<string> {
    // 检查缓存
    if (cache.has(query)) {
      const cached = cache.get(query)!
      expansionResult.value = cached
      expandedQuery.value = cached.expandedQuery
      return cached.expandedQuery
    }

    isExpanding.value = true

    try {
      // 调用API
      const result = await expandQueryWithGraph(knowledgeId, query)

      // 缓存结果
      cache.set(query, result)

      expansionResult.value = result
      expandedQuery.value = result.expandedQuery

      return result.expandedQuery
    } finally {
      isExpanding.value = false
    }
  }

  /**
   * 清除缓存
   */
  function clearCache() {
    cache.clear()
  }

  return {
    expandQuery,
    expansionResult,
    isExpanding,
    expandedQuery,
    clearCache,
  }
}
