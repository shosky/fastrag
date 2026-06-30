/**
 * 查询预处理服务
 *
 * 在检索测试前对用户输入进行纠错、重写、扩展等预处理。
 * 使用本地 mock 数据层实现，避免依赖后端 stub 接口。
 */

import { getSuggestion } from '@/mock/search-correction'
import { applyQueryRules as mockApplyRules } from '@/mock/query-rules'

/**
 * 自动纠错
 * 基于错别字表 + 拼音映射对查询进行纠错。
 *
 * @returns 若发现纠错则返回 { corrected, reason }，否则返回 null
 */
export function autoCorrect(query: string): { corrected: string; reason: string } | null {
  const result = getSuggestion(query)
  if (result.suggestedQuery && result.suggestedQuery !== query) {
    return { corrected: result.suggestedQuery, reason: result.reason }
  }
  return null
}

/**
 * 查询重写
 * 将口语/简写转换为规范术语，并追加相关上下文。
 *
 * @returns { rewritten, appliedRules } — rewritten 为重写后的查询
 */
export function rewriteQuery(query: string): { rewritten: string; appliedRules: string[] } {
  return mockApplyRules(query)
}
