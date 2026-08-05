import type { ApiGroup } from './types'
import { authGroup } from './auth'
import { knowledgeGroup } from './knowledge'
import { documentsGroup } from './documents'
import { chunksGroup } from './chunks'
import { retrievalGroup } from './retrieval'

/** 所有 API 分组汇总 */
export const allApiGroups: ApiGroup[] = [
  authGroup,
  knowledgeGroup,
  documentsGroup,
  chunksGroup,
  retrievalGroup,
]

/** 按分组 key 查找 */
export function getApiGroup(key: string): ApiGroup | undefined {
  return allApiGroups.find(g => g.key === key)
}

/** 全局搜索接口 */
export function searchEndpoints(keyword: string): ApiGroup[] {
  const lower = keyword.toLowerCase()
  return allApiGroups
    .map(group => ({
      ...group,
      endpoints: group.endpoints.filter(
        ep =>
          ep.summary.toLowerCase().includes(lower) ||
          ep.description.toLowerCase().includes(lower) ||
          ep.path.toLowerCase().includes(lower)
      ),
    }))
    .filter(group => group.endpoints.length > 0)
}
