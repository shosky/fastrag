import { ref } from 'vue'
import * as api from '@/api'

// ===========================================================================
// 同义词联想 composable
//
// 在检索前对 query 进行同义词扩展，提升召回率。
// 已切换为真实 API 调用。
// ===========================================================================

export function useSynonyms() {
  const matchedTerms = ref<string[]>([])
  const addedTerms = ref<string[]>([])
  const expandedQuery = ref('')

  /**
   * 对 query 进行同义词扩展。
   * 返回扩展后的查询词（含同义词），同时更新 reactive 状态供 UI 展示。
   */
  async function expandQuery(query: string): Promise<string> {
    try {
      const synonyms = await api.expandSynonyms(query)
      addedTerms.value = (synonyms as string[]) || []
      matchedTerms.value = (synonyms as string[]) || []
      expandedQuery.value = synonyms.length > 0
        ? `${query} ${synonyms.join(' ')}`
        : query
    } catch {
      expandedQuery.value = query
    }
    return expandedQuery.value
  }

  /** 清除联想状态 */
  function clear() {
    matchedTerms.value = []
    addedTerms.value = []
    expandedQuery.value = ''
  }

  /** 是否有联想词命中 */
  const hasMatches = computed(() => matchedTerms.value.length > 0)

  return {
    matchedTerms,
    addedTerms,
    expandedQuery,
    hasMatches,
    expandQuery,
    clear,
  }
}
