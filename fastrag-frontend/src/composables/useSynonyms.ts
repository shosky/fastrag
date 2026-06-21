import { ref } from 'vue'
import { expandQueryWithSynonyms } from '@/mock/terminology'

// ===========================================================================
// 同义词联想 composable
//
// 在检索前对 query 进行同义词扩展，提升召回率。
// 返回扩展后的查询词 + 联想词详情，供 SearchTestPanel 展示。
// ===========================================================================

export function useSynonyms() {
  const matchedTerms = ref<string[]>([])
  const addedTerms = ref<string[]>([])
  const expandedQuery = ref('')

  /**
   * 对 query 进行同义词扩展。
   * 返回扩展后的查询词（含同义词），同时更新 reactive 状态供 UI 展示。
   */
  function expandQuery(query: string): string {
    const result = expandQueryWithSynonyms(query)
    matchedTerms.value = result.matchedTerms
    addedTerms.value = result.addedTerms
    expandedQuery.value = result.expandedQuery
    return result.expandedQuery
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
