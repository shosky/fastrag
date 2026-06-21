import type { SearchResultItem } from '@/types/evaluation'
import type { RetrievalConfig } from '@/types/knowledge'

// ===========================================================================
// 知识库 chunk 内容池 + 关键词检索 mock
//
// 替代 SearchTestPanel / chunks.vue 里各自的硬编码 chunk。
// 内容取自现有 mockResults 与 goldAnswer 对应的业务文档。
// ===========================================================================

export interface MockChunk {
  fileId: string
  fileName: string
  chunkIndex: number
  content: string
}

/** 默认知识库的 chunk 池（节选，覆盖评估基准涉及的问题） */
export const mockChunks: MockChunk[] = [
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 0,
    content:
      '小微ICT是响应集团推进中小企业标准ICT服务要求打造的产品，通过标准化产品、属地化自主交付服务提升差异化、稳固两线，带动战新业务规模发展。适用场景包括中小企业、校园、政企客户的组网、监控、布线、机房托管等需求。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 1,
    content:
      '电信员保障全省业务交付与售后，10009 线上 24 小时客服在线，2-6 小时上门服务。网络方面，电信针对视联网构建了高稳定高上行的专用网络，且价格优惠。终端方面，电信新装终端两年只换不修，视联网平台支持海康、大华等一线品牌接入。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 2,
    content:
      '全光组网标准化礼包-小微ICT标准包：1主1从标准包价格 2299 元。包含设备-主光猫（华为B866-S2）1078 元、设备-从光猫（华为B671-S2）619 元等。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 3,
    content:
      '视频监控标准月付型礼包：4个摄像头标准月付型 1399 元，含3个月套餐费，折合 459 元/月；4个摄像头小微ICT模式 1399 元，含3个月套餐费，折合 445 元/月。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 5,
    content:
      '综合布线标准化礼包：20点位标准包 2799 元；每叠加 1 个点位 180 元。如客户怕综合布线繁琐，可强调电信团队专业、施工高效。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 7,
    content:
      '视频监控（室外）有避雷、浪涌防护要求时：4个摄像头标准包 6018 元（包含避雷浪涌设备等），每叠加 1 个摄像头 1418 元。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 10,
    content:
      '关于标品合同编码：分公司若还未来得及申请本公司小微ICT的标品合同编码，可以先填"1"通过受理，之后再后台补录。后期正常受理请务必填写正确的标品合同编码，因为这是跟财务成本结算强相关的参数。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 14,
    content:
      '小微ICT项目奖的阶梯激励方案：利润率 ≥ 70%，系数 = 2；45% ≤ 利润率 < 70%，系数 = 1.5；30% ≤ 利润率 < 45%，系数 = 1。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 17,
    content:
      '案例分析：各类型客户均有成功案例。如某县街道办事处智能组网 & 机房托管项目，客户需求是解决便民 wifi 掉线和机房升级改造问题，商机源于日常拜访，通过提供智能组网和机房托管服务提升了客户上网体验。',
  },
  {
    fileId: '1',
    fileName: 'AIS 平台用户手册.pdf',
    chunkIndex: 19,
    content:
      '六促销售技巧：如客户担心综合布线繁琐，可强调电信团队专业、施工高效、售后有保障。通过对比第三方突出电信一站式服务优势。',
  },
]

/** HTML 转义，避免预览片段注入 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 中文分词（简易：按字 bigram + 关键词切分） */
function tokenize(text: string): string[] {
  // 提取 2 字以上的中文片段 + 英文/数字 token
  const tokens = new Set<string>()
  const cnMatches = text.match(/[\u4e00-\u9fa5]{2,}/g) || []
  cnMatches.forEach((seg) => {
    // 生成 bigram
    for (let i = 0; i < seg.length - 1; i++) {
      tokens.add(seg.slice(i, i + 2))
    }
  })
  const enMatches = text.match(/[a-zA-Z0-9]+/g) || []
  enMatches.forEach((t) => tokens.add(t.toLowerCase()))
  return Array.from(tokens)
}

/** 简易 BM25 风格打分：query token 与 chunk token 命中数 */
function scoreChunk(queryTokens: string[], chunkTokens: string[]): number {
  const chunkSet = new Set(chunkTokens)
  let hits = 0
  queryTokens.forEach((t) => {
    if (chunkSet.has(t)) hits++
  })
  return hits
}

/** 返回命中的 query token 列表（用于高亮） */
function getHitTokens(queryTokens: string[], chunkTokens: string[]): string[] {
  const chunkSet = new Set(chunkTokens)
  return queryTokens.filter((t) => chunkSet.has(t))
}

/**
 * 生成带高亮标记的预览片段。
 * 截取首个命中词附近 ±60 字的上下文，命中的词用 <mark> 包裹。
 */
function buildPreviewSnippet(content: string, hitTokens: string[]): string {
  if (hitTokens.length === 0) {
    // 无命中：截取前 120 字
    const snippet = content.slice(0, 120)
    return escapeHtml(snippet) + (content.length > 120 ? '…' : '')
  }

  // 找首个命中词的位置（最长优先，避免短 bigram 误命中）
  const sortedTokens = [...hitTokens].sort((a, b) => b.length - a.length)
  let firstHit = -1
  for (const token of sortedTokens) {
    const idx = content.indexOf(token)
    if (idx !== -1) {
      firstHit = idx
      break
    }
  }
  if (firstHit === -1) {
    return escapeHtml(content.slice(0, 120))
  }

  // 截取上下文窗口
  const windowSize = 60
  const start = Math.max(0, firstHit - windowSize)
  const end = Math.min(content.length, firstHit + windowSize * 2)
  const snippet = content.slice(start, end)
  const prefix = start > 0 ? '…' : ''
  const suffix = end < content.length ? '…' : ''

  // 转义后再包裹命中的词（最长优先，避免嵌套替换）
  // 用 split/join 替换，避免正则元字符转义问题
  let html = escapeHtml(snippet)
  sortedTokens.forEach((token) => {
    const escaped = escapeHtml(token)
    const wrapped = '<mark>' + escaped + '</mark>'
    html = html.split(escaped).join(wrapped)
  })

  return prefix + html + suffix
}

/**
 * 关键词检索 mock。
 * 根据 query 命中的 token 数对 chunk 打分排序，返回 topK。
 * 当 query 完全无法命中任何 chunk 时，返回按默认顺序的前 topK 条（模拟兜底召回）。
 */
export function searchChunks(
  query: string,
  config: RetrievalConfig,
  kbId: string = 'default',
): SearchResultItem[] {
  const queryTokens = tokenize(query)
  const scored = mockChunks.map((chunk) => ({
    chunk,
    score: scoreChunk(queryTokens, tokenize(chunk.content)),
  }))

  scored.sort((a, b) => b.score - a.score)

  const topK = Math.min(config.topK, mockChunks.length)
  let picked = scored.slice(0, topK)

  // 兜底：若全部 0 分，取前 topK
  if (picked.every((p) => p.score === 0)) {
    picked = scored.slice(0, topK)
  }

  const maxScore = picked[0]?.score || 1

  const mapped = picked.map((p, i) => {
    // 把命中分数映射成 0-100 的相似度（避免除 0）
    const similarity = maxScore > 0
      ? 30 + (p.score / maxScore) * 65 // 30~95 区间，更像真实检索分布
      : 30 + Math.random() * 10
    const distance = Math.max(0, 1 - similarity / 100)
    const chunkTokens = tokenize(p.chunk.content)
    const highlights = getHitTokens(queryTokens, chunkTokens)
    return {
      index: i + 1,
      similarity: Number(similarity.toFixed(2)),
      content: p.chunk.content,
      source: p.chunk.fileName,
      fileId: p.chunk.fileId,
      chunkIndex: p.chunk.chunkIndex,
      distance: Number(distance.toFixed(4)),
      highlights,
      previewSnippet: buildPreviewSnippet(p.chunk.content, highlights),
    }
  })

  // 应用相似度阈值过滤（阈值是 0-1 的比例，相似度是 0-100 的百分比）
  // 兜底场景（全 0 分）不应用阈值，避免把兜底结果也滤掉导致空结果
  const hasRealHits = picked.some((p) => p.score > 0)
  if (hasRealHits && (config.similarityThreshold ?? 0) > 0) {
    const thresholdPercent = (config.similarityThreshold ?? 0) * 100
    const filtered = mapped.filter((r) => r.similarity >= thresholdPercent)
    if (filtered.length > 0) {
      // 重新编号
      return filtered.map((r, i) => ({ ...r, index: i + 1 }))
    }
  }

  return mapped
}

// ===========================================================================
// 多路召回 + 融合排序
// ===========================================================================

import type { RetrievalSettingConfig } from '@/types/knowledge'
import type { QaPair } from '@/types/knowledge'
import { getQaPairs } from './qa-pairs'

/** 多路召回选项 */
export interface MultiRetrievalOptions {
  enableMultiRetrieval: boolean
  vectorRecallCount: number
  fulltextRecallCount: number
  graphRecallCount: number
  qaRecallCount: number
  fusionStrategy: 'rrf' | 'weighted' | 'interleave'
  topK: number
}

/**
 * 向量召回（mock：用 bigram 匹配，模拟语义相似度）
 */
function vectorRecall(query: string, count: number): Array<{ chunk: MockChunk; score: number }> {
  const tokens = tokenize(query)
  const scored = mockChunks.map((chunk) => ({
    chunk,
    score: scoreChunk(tokens, tokenize(chunk.content)),
  }))
  scored.sort((a, b) => b.score - a.score)
  return scored.slice(0, count)
}

/**
 * 全文召回（mock：用原始 query 关键词精确匹配）
 */
function fulltextRecall(query: string, count: number): Array<{ chunk: MockChunk; score: number }> {
  // 去除 query 中的分词符，做精确子串匹配
  const cleanedQuery = query.replace(/[，。、；：？！\s]/g, '')
  const scored = mockChunks.map((chunk) => {
    let hits = 0
    // 对 query 中每个 2 字片段检查是否出现在 content 中
    for (let i = 0; i < cleanedQuery.length - 1; i++) {
      if (chunk.content.includes(cleanedQuery.slice(i, i + 2))) hits++
    }
    return { chunk, score: hits }
  })
  scored.sort((a, b) => b.score - a.score)
  return scored.slice(0, count)
}

/**
 * 图谱子图召回（mock：根据查询中的实体名匹配 chunk）
 */
function graphSubgraphRecall(query: string, count: number): Array<{ chunk: MockChunk; score: number }> {
  // 简单模拟：检查 query 是否包含图谱实体名（来自 chunks 内容的关键词）
  const entityKeywords = ['视频监控', '综合布线', '全光组网', '机房托管', '阶梯激励', '标品合同编码']
  const matchedEntities = entityKeywords.filter((e) => query.includes(e))
  if (matchedEntities.length === 0) return []

  const scored = mockChunks.map((chunk) => {
    let score = 0
    matchedEntities.forEach((e) => {
      if (chunk.content.includes(e)) score += 2
    })
    return { chunk, score }
  })
  scored.sort((a, b) => b.score - a.score)
  return scored.filter((s) => s.score > 0).slice(0, count)
}

/**
 * QA 对召回（从问答知识库直接匹配）
 */
function qaRecall(query: string, kbId: string, count: number): Array<{ chunk: MockChunk; score: number }> {
  const qaPairs = getQaPairs(kbId)
  if (qaPairs.length === 0) return []

  const scored = qaPairs.map((qa) => {
    const qTokens = tokenize(qa.question)
    const queryTokens = tokenize(query)
    const common = qTokens.filter((t) => queryTokens.includes(t)).length
    return { qa, score: common }
  })
  scored.sort((a, b) => b.score - a.score)

  // 把命中的 QA 对转为 MockChunk 格式
  return scored
    .filter((s) => s.score > 0)
    .slice(0, count)
    .map((s) => ({
      chunk: {
        fileId: s.qa.fileId || 'qa_pool',
        fileName: s.qa.fileName || '问答知识库',
        chunkIndex: -1,
        content: `问：${s.qa.question}\n答：${s.qa.answer}`,
      },
      score: s.score * 3, // QA 匹配加权
    }))
}

/**
 * RRF（Reciprocal Rank Fusion）融合多路召回结果
 */
function fuseRRF(
  channels: Array<Array<{ chunk: MockChunk; score: number }>>,
  k: number = 60,
): Array<{ chunk: MockChunk; score: number }> {
  const scoreMap = new Map<string, { chunk: MockChunk; rrfScore: number }>()

  channels.forEach((channel) => {
    channel.forEach((item, rank) => {
      const key = `${item.chunk.fileId}_${item.chunk.chunkIndex}`
      const rrfScore = 1 / (k + rank + 1)
      if (!scoreMap.has(key)) {
        scoreMap.set(key, { chunk: item.chunk, rrfScore: 0 })
      }
      scoreMap.get(key)!.rrfScore += rrfScore
    })
  })

  const results = Array.from(scoreMap.values())
  results.sort((a, b) => b.rrfScore - a.rrfScore)
  return results.map((r) => ({ chunk: r.chunk, score: r.rrfScore * 100 }))
}

/**
 * 加权融合
 */
function fuseWeighted(
  channels: Array<{ results: Array<{ chunk: MockChunk; score: number }>; weight: number }>,
): Array<{ chunk: MockChunk; score: number }> {
  const scoreMap = new Map<string, { chunk: MockChunk; totalScore: number }>()

  channels.forEach(({ results, weight }) => {
    const maxScore = Math.max(...results.map((r) => r.score), 1)
    results.forEach((item) => {
      const key = `${item.chunk.fileId}_${item.chunk.chunkIndex}`
      const normalized = (item.score / maxScore) * weight
      if (!scoreMap.has(key)) {
        scoreMap.set(key, { chunk: item.chunk, totalScore: 0 })
      }
      scoreMap.get(key)!.totalScore += normalized
    })
  })

  const results = Array.from(scoreMap.values())
  results.sort((a, b) => b.totalScore - a.totalScore)
  return results.map((r) => ({ chunk: r.chunk, score: r.totalScore * 30 }))
}

/**
 * 交错融合：轮流从各通道取结果
 */
function fuseInterleave(
  channels: Array<Array<{ chunk: MockChunk; score: number }>>,
): Array<{ chunk: MockChunk; score: number }> {
  const seen = new Set<string>()
  const result: Array<{ chunk: MockChunk; score: number }> = []
  const maxLen = Math.max(...channels.map((c) => c.length), 0)

  for (let i = 0; i < maxLen; i++) {
    for (const channel of channels) {
      if (i < channel.length) {
        const key = `${channel[i].chunk.fileId}_${channel[i].chunk.chunkIndex}`
        if (!seen.has(key)) {
          seen.add(key)
          result.push(channel[i])
        }
      }
    }
  }
  return result
}

/**
 * 多路召回检索：并行执行多通道召回，融合排序后返回 topK。
 */
export function multiRetrievalSearch(
  query: string,
  options: MultiRetrievalOptions,
  kbId: string = 'default',
): SearchResultItem[] {
  const channels: Array<{ chunk: MockChunk; score: number }>[] = []

  // 各通道召回
  channels.push(vectorRecall(query, options.vectorRecallCount))
  channels.push(fulltextRecall(query, options.fulltextRecallCount))

  const graphResults = graphSubgraphRecall(query, options.graphRecallCount)
  if (graphResults.length > 0) channels.push(graphResults)

  const qaResults = qaRecall(query, kbId, options.qaRecallCount)
  if (qaResults.length > 0) channels.push(qaResults)

  // 融合
  let fused: Array<{ chunk: MockChunk; score: number }>
  switch (options.fusionStrategy) {
    case 'weighted':
      fused = fuseWeighted(channels.map((results, i) => ({
        results,
        weight: i === 0 ? 0.4 : i === 1 ? 0.3 : i === 2 ? 0.2 : 0.1,
      })))
      break
    case 'interleave':
      fused = fuseInterleave(channels)
      break
    case 'rrf':
    default:
      fused = fuseRRF(channels)
      break
  }

  // 取 topK
  const topK = Math.min(options.topK, fused.length)
  const picked = fused.slice(0, topK)
  const maxScore = picked[0]?.score || 1

  // 映射为 SearchResultItem（含高亮）
  const queryTokens = tokenize(query)
  return picked.map((p, i) => {
    const similarity = maxScore > 0
      ? 30 + (p.score / maxScore) * 65
      : 30 + Math.random() * 10
    const distance = Math.max(0, 1 - similarity / 100)
    const highlights = getHitTokens(queryTokens, tokenize(p.chunk.content))
    return {
      index: i + 1,
      similarity: Number(similarity.toFixed(2)),
      content: p.chunk.content,
      source: p.chunk.fileName,
      fileId: p.chunk.fileId,
      chunkIndex: p.chunk.chunkIndex,
      distance: Number(distance.toFixed(4)),
      highlights,
      previewSnippet: buildPreviewSnippet(p.chunk.content, highlights),
    }
  })
}

// ===========================================================================
// MMR（Maximal Marginal Relevance）多样性控制
// ===========================================================================

/**
 * 计算两个 chunk 之间的相似度（基于 bigram 重叠度）
 */
function chunkSimilarity(a: MockChunk, b: MockChunk): number {
  const aTokens = new Set(tokenize(a.content))
  const bTokens = tokenize(b.content)
  const overlap = bTokens.filter((t) => aTokens.has(t)).length
  return overlap / Math.max(bTokens.length, 1)
}

/**
 * MMR 算法：在相关性和多样性之间取平衡。
 * lambda=1 时纯按相关性排序，lambda=0 时纯按多样性排序。
 */
export function applyMMR(
  items: Array<{ chunk: MockChunk; score: number }>,
  lambda: number,
  topK: number,
): Array<{ chunk: MockChunk; score: number }> {
  if (items.length === 0) return []
  if (lambda >= 1) return items.slice(0, topK) // 纯相关性

  const selected: Array<{ chunk: MockChunk; score: number }> = []
  const candidates = [...items]

  // 第一个：直接取最高分
  selected.push(candidates.shift()!)

  while (selected.length < topK && candidates.length > 0) {
    let bestIdx = 0
    let bestScore = -Infinity

    for (let i = 0; i < candidates.length; i++) {
      const relevance = candidates[i].score
      // 与已选结果中最大的相似度
      let maxSim = 0
      for (const s of selected) {
        const sim = chunkSimilarity(candidates[i].chunk, s.chunk)
        if (sim > maxSim) maxSim = sim
      }
      // MMR 公式：λ * relevance - (1-λ) * maxSimilarity
      const mmr = lambda * relevance - (1 - lambda) * maxSim
      if (mmr > bestScore) {
        bestScore = mmr
        bestIdx = i
      }
    }

    selected.push(candidates.splice(bestIdx, 1)[0])
  }

  return selected
}

// ===========================================================================
// LLM 重排序（mock：用查询-文档语义相关度模拟 LLM 打分）
// ===========================================================================

/**
 * LLM Rerank（mock 实现）。
 * 真实场景调用大模型 API 对 (query, doc) 对打分。
 * 这里用 token 重叠度 + 长度惩罚模拟 LLM 的语义理解能力。
 */
export function llmRerank(
  query: string,
  items: Array<{ chunk: MockChunk; score: number }>,
): Array<{ chunk: MockChunk; score: number }> {
  const queryTokens = tokenize(query)

  return items
    .map((item) => {
      const docTokens = tokenize(item.chunk.content)
      // 精确匹配得分
      const exactHits = queryTokens.filter((t) => docTokens.includes(t)).length
      // 部分匹配（子串）
      let partialHits = 0
      for (const qt of queryTokens) {
        if (item.chunk.content.includes(qt)) partialHits++
      }
      // 长度惩罚：过长的 chunk 稀释了相关性
      const lengthPenalty = Math.max(0.5, 1 - item.chunk.content.length / 2000)
      // 模拟 LLM 综合打分
      const llmScore = (exactHits * 3 + partialHits) * lengthPenalty
      return { chunk: item.chunk, score: llmScore }
    })
    .sort((a, b) => b.score - a.score)
}

/** 根据 fileId + chunkIndex 取 chunk 标识（gold chunk 格式） */
export function chunkId(fileId: string, chunkIndex: number): string {
  return `${fileId}_chunk_${chunkIndex}`
}

/** 获取默认知识库的 chunk 总数（供 IndexManagementPanel / 基准生成展示用） */
export function getChunkCount(kbId: string = 'default'): number {
  return mockChunks.length
}
