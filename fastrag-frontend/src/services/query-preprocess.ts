/**
 * 查询预处理服务
 *
 * 检索前对用户输入进行纠错、重写。优先调用后端真实接口
 * （智能搜索纠错规则 / 查询规则库），接口异常时回退本地词表。
 */

import request from '@/utils/request'
import { getSuggestion } from '@/mock/search-correction'
import { applyQueryRules as mockApplyRules } from '@/mock/query-rules'

export interface CorrectResult {
  corrected: string
  reason: string
}

/**
 * 自动纠错：优先后端智能搜索纠错规则（GET /kb/{kbId}/search-associations/auto-correct），
 * 失败回退本地错别字表。
 *
 * @returns 若发现纠错则返回 { corrected, reason }，否则返回 null
 */
export async function autoCorrect(query: string, kbId?: string): Promise<CorrectResult | null> {
  if (query && kbId) {
    try {
      const res: any = await request.get(`/kb/${kbId}/search-associations/auto-correct`, { params: { q: query } })
      const corrected = res?.corrected ?? res?.correctText ?? res?.suggestedQuery
      if (corrected && corrected !== query) {
        return { corrected, reason: res?.reason || '命中纠错规则' }
      }
    } catch {
      // 后端异常时回退本地词表
    }
  }
  // 后端规则未命中时，回退前端本地错别字词表（两级纠错，覆盖规则库未穷举的错别字）
  const result = getSuggestion(query)
  if (result.suggestedQuery && result.suggestedQuery !== query) {
    return { corrected: result.suggestedQuery, reason: result.reason }
  }
  return null
}

/**
 * 查询重写：优先后端查询规则库（POST /api/query-rules/apply），失败回退本地规则。
 *
 * @returns { rewritten, appliedRules } — rewritten 为重写后的查询
 */
export async function rewriteQuery(query: string): Promise<{ rewritten: string; appliedRules: string[] }> {
  try {
    const res: any = await request.post('/query-rules/apply', { query })
    if (res && typeof res.rewritten === 'string' && res.rewritten !== query) {
      return { rewritten: res.rewritten, appliedRules: Array.isArray(res.appliedRules) ? res.appliedRules : [] }
    }
    if (res && typeof res.rewritten === 'string') {
      return { rewritten: res.rewritten, appliedRules: [] }
    }
  } catch {
    // 后端异常时回退本地规则
  }
  return mockApplyRules(query)
}
