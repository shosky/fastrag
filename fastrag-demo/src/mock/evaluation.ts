import type {
  Evaluation,
  EvaluationDetail,
  EvaluationResult,
  EvaluationStartConfig,
  AnswerJudgeResult,
  SearchResultItem,
} from '@/types/evaluation'
import type { RetrievalConfig } from '@/types/knowledge'
import { getBenchmarks, getBenchmarkDetail } from './benchmark'
import { searchChunks } from './chunks'

// ===========================================================================
// RAG 评估 —— 召回 + 拼接生成 + 规则评判 闭环
// ===========================================================================

/** 默认检索配置（评估内部用） */
const defaultRetrievalConfig: RetrievalConfig = {
  mode: 'vector',
  topK: 10,
  similarityThreshold: 0,
  bm25RecallCount: 50,
  vectorWeight: 0.7,
  bm25Weight: 0.3,
  bm25SparseDropRate: 0,
}

/** 按 kbId 存放评估记录 */
const evaluationStore: Record<string, Evaluation[]> = {
  default: [],
}

let evalSeq = 0

function genId(prefix: string): string {
  evalSeq++
  return `${prefix}-${Date.now().toString(36)}-${evalSeq}`
}

/**
 * 拼接生成答案（mock）：取 top-1 召回 chunk 的内容，截取前 ~120 字作为答案。
 * 真实 LLM 的替代品 —— 不依赖外部服务，但让 generatedAnswer 有实质内容。
 */
export function generateAnswer(
  _question: string,
  retrieved: SearchResultItem[],
): string {
  if (retrieved.length === 0) return ''
  const top = retrieved[0]
  const content = top.content.replace(/\s+/g, ' ').trim()
  // 截到 120 字附近的一个完整标点
  const slice = content.slice(0, 120)
  const lastPunct = Math.max(
    slice.lastIndexOf('。'),
    slice.lastIndexOf('；'),
    slice.lastIndexOf('，'),
    slice.lastIndexOf(' '),
  )
  return lastPunct > 40 ? slice.slice(0, lastPunct + 1) : slice
}

/**
 * 规则评判（mock）：用 goldAnswer 的关键数字/词组匹配 generatedAnswer。
 * 抽取数字与 2-4 字关键词，命中率 ≥ 60% 视为正确。
 */
export function judgeAnswer(generatedAnswer: string, goldAnswer: string): AnswerJudgeResult {
  if (!generatedAnswer) {
    return { isCorrect: false, reason: '未生成答案' }
  }

  // 抽取 gold 中的数字（价格、系数等核心事实）
  const goldNumbers = (goldAnswer.match(/\d+(\.\d+)?/g) || []).filter((n) => n.length > 1)
  const genNumbers = new Set(generatedAnswer.match(/\d+(\.\d+)?/g) || [])

  if (goldNumbers.length > 0) {
    const hit = goldNumbers.filter((n) => genNumbers.has(n)).length
    const rate = hit / goldNumbers.length
    return {
      isCorrect: rate >= 0.6,
      reason: `关键数字命中 ${hit}/${goldNumbers.length}（${Math.round(rate * 100)}%）`,
    }
  }

  // 没有数字时，用 2-4 字关键词匹配
  const goldKeywords = (goldAnswer.match(/[\u4e00-\u9fa5]{2,4}/g) || [])
    .filter((kw, i, arr) => arr.indexOf(kw) === i)
  if (goldKeywords.length === 0) {
    return { isCorrect: false, reason: 'gold 答案无可校验内容' }
  }
  const hit = goldKeywords.filter((kw) => generatedAnswer.includes(kw)).length
  const rate = hit / goldKeywords.length
  return {
    isCorrect: rate >= 0.6,
    reason: `关键词命中 ${hit}/${goldKeywords.length}（${Math.round(rate * 100)}%）`,
  }
}

/** 检查 gold chunk 是否在召回结果中（用于 Recall@K） */
function recallAtK(goldChunkId: string, retrieved: SearchResultItem[], k: number): boolean {
  const topK = retrieved.slice(0, k)
  return topK.some(
    (r) => `${r.fileId}_chunk_${r.chunkIndex}` === goldChunkId,
  )
}

/**
 * 核心闭环：对基准中每个问题执行 召回→生成→评判，并聚合指标。
 */
export function runEvaluation(
  kbId: string,
  config: EvaluationStartConfig,
): EvaluationDetail {
  const benches = getBenchmarks(kbId)
  const bench = benches.find((b) => b.name === config.benchmark) || benches[0]
  const questions = getBenchmarkDetail(kbId, bench.id)

  const startTime = Date.now()
  const results: EvaluationResult[] = []
  let recall1Hit = 0, recall3Hit = 0, recall5Hit = 0, recall10Hit = 0
  let answerCorrect = 0

  questions.forEach((q) => {
    // 1. 召回
    const retrieved = searchChunks(q.question, defaultRetrievalConfig, kbId)

    // 2. 指标计算
    const r1 = recallAtK(q.goldChunks, retrieved, 1)
    const r3 = recallAtK(q.goldChunks, retrieved, 3)
    const r5 = recallAtK(q.goldChunks, retrieved, 5)
    const r10 = recallAtK(q.goldChunks, retrieved, 10)
    if (r1) recall1Hit++
    if (r3) recall3Hit++
    if (r5) recall5Hit++
    if (r10) recall10Hit++

    // 3. 生成
    const generated = generateAnswer(q.question, retrieved)

    // 4. 评判
    const judgment = judgeAnswer(generated, q.goldAnswer)
    if (judgment.isCorrect) answerCorrect++

    results.push({
      question: q.question,
      generatedAnswer: generated || '（未召回到相关内容）',
      retrievalMetrics: `R@1 ${r1 ? 1 : 0}.000  R@3 ${r3 ? 1 : 0}.000  R@5 ${r5 ? 1 : 0}.000  R@10 ${r10 ? 1 : 0}.000`,
      answerJudgment: judgment,
    })
  })

  const total = questions.length
  const durationMs = Date.now() - startTime
  const recallAt1 = total ? recall1Hit / total : 0
  const recallAt3 = total ? recall3Hit / total : 0
  const recallAt5 = total ? recall5Hit / total : 0
  const recallAt10 = total ? recall10Hit / total : 0
  const answerAccuracy = total ? answerCorrect / total : 0

  // 综合评分 = 召回(70%) + 答案准确率(30%)，与各项指标自洽
  const overallScore = Math.round((recallAt10 * 0.7 + answerAccuracy * 0.3) * 100)

  const id = genId('eval')
  const runId = `Run run_${Math.random().toString(36).slice(2, 10)}`
  const createdAt = new Date().toLocaleString('zh-CN')

  // 写入列表记录
  const evalRecord: Evaluation = {
    id,
    name: config.name,
    benchmark: bench.name,
    benchmarkCount: total,
    dataCount: total,
    completedCount: total,
    duration: `${(durationMs / 1000).toFixed(1)}秒`,
    recallAt10,
    overallScore,
    status: 'completed',
    createdAt,
    runId,
  }
  if (!evaluationStore[kbId]) evaluationStore[kbId] = []
  evaluationStore[kbId].unshift(evalRecord)

  return {
    id,
    name: config.name,
    runId,
    status: '已完成',
    overallScore,
    totalQuestions: total,
    completedCount: total,
    duration: evalRecord.duration,
    recallAt1,
    recallAt3,
    recallAt5,
    recallAt10,
    answerAccuracy,
    results,
  }
}

/** 获取评估记录列表 */
export function getEvaluations(kbId: string): Evaluation[] {
  return (evaluationStore[kbId] || evaluationStore.default).map((e) => ({ ...e }))
}

/** 删除评估记录 */
export function deleteEvaluation(kbId: string, evalId: string): void {
  if (evaluationStore[kbId]) {
    evaluationStore[kbId] = evaluationStore[kbId].filter((e) => e.id !== evalId)
  }
}

// ===========================================================================
// 评估详情缓存：首次查看时基于记录重算（保持结果可复现）
// 用记录 id 作 key 存详情，避免重复计算且数字一致
// ===========================================================================
const detailCache = new Map<string, EvaluationDetail>()

/** 缓存详情 */
export function cacheEvaluationDetail(evalId: string, detail: EvaluationDetail): void {
  detailCache.set(evalId, detail)
}

/** 取缓存详情 */
export function getCachedDetail(evalId: string): EvaluationDetail | null {
  return detailCache.get(evalId) || null
}
