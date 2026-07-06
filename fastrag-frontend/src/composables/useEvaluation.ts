import { ref, computed } from 'vue'
import type { Evaluation, EvaluationDetail, EvaluationStartConfig } from '@/types/evaluation'
import {
  fetchEvaluations,
  runEvaluationApi,
  deleteEvaluationApi,
  fetchEvaluationStatus,
} from '@/api'

const POLL_INTERVAL = 3000
const CACHE_KEY = 'fastrag_evaluation_detail_cache'

// ========== localStorage 缓存持久化 ==========

interface CachedDetail {
  detail: EvaluationDetail
  timestamp: number
}

function loadCache(): Record<string, CachedDetail> {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

function saveCache(cache: Record<string, CachedDetail>) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(cache))
  } catch {
    // localStorage 满了就丢弃缓存
  }
}

export function useEvaluation(kbId: string = 'default') {
  const evaluations = ref<Evaluation[]>([])
  const loading = ref(false)
  const running = ref(false)
  const pollingTimer = ref<ReturnType<typeof setInterval> | null>(null)
  const runningEvalIds = ref<Set<string>>(new Set())

  const latest = computed<Evaluation | null>(() => evaluations.value[0] || null)

  async function load() {
    loading.value = true
    try {
      const data = await fetchEvaluations(kbId)
      evaluations.value = (data as any)?.list || (data as any) || []
    } finally {
      loading.value = false
    }
  }

  async function start(config: EvaluationStartConfig): Promise<EvaluationDetail> {
    running.value = true
    try {
      console.log('[Evaluation] API call: POST /kb/' + kbId + '/evaluations/run', config)
      const detail = await runEvaluationApi(kbId, config)
      console.log('[Evaluation] API response:', detail)
      // 缓存详情
      const cache = loadCache()
      cache[detail.id] = { detail, timestamp: Date.now() }
      saveCache(cache)
      // 重新拉取列表
      await load()
      return detail
    } finally {
      running.value = false
    }
  }

  async function remove(id: string) {
    await deleteEvaluationApi(kbId, id)
    evaluations.value = evaluations.value.filter((e) => e.id !== id)
    // 清理缓存
    const cache = loadCache()
    delete cache[id]
    saveCache(cache)
  }

  /** 获取评估详情（优先从缓存取，缓存命中且未过期则直接返回） */
  function getCachedDetail(evalId: string): EvaluationDetail | null {
    const cache = loadCache()
    const entry = cache[evalId]
    if (!entry) return null
    // 缓存有效期 30 分钟
    if (Date.now() - entry.timestamp > 30 * 60 * 1000) {
      delete cache[evalId]
      saveCache(cache)
      return null
    }
    return entry.detail
  }

  /** 开始轮询运行中的评估状态 */
  function startPolling(evalId: string) {
    runningEvalIds.value.add(evalId)
    if (pollingTimer.value !== null) return // 已在轮询

    pollingTimer.value = setInterval(async () => {
      if (runningEvalIds.value.size === 0) {
        stopPolling()
        return
      }
      try {
        // 轮询每个运行中的评估
        for (const eid of runningEvalIds.value) {
          const statusRes: any = await fetchEvaluationStatus(kbId, eid)
          console.log('[Evaluation] Polling status:', eid, statusRes)
          const status = statusRes?.status
          if (status === 'completed' || status === 'failed') {
            runningEvalIds.value.delete(eid)
            // 清除缓存，避免下次查看时拿到过期数据
            const cache = loadCache()
            delete cache[eid]
            saveCache(cache)
            // 刷新列表
            await load()
          }
        }
      } catch {
        // 轮询失败不阻断
      }
    }, POLL_INTERVAL)
  }

  function stopPolling() {
    if (pollingTimer.value !== null) {
      clearInterval(pollingTimer.value)
      pollingTimer.value = null
    }
    runningEvalIds.value.clear()
  }

  return {
    evaluations,
    latest,
    loading,
    running,
    load,
    start,
    remove,
    getCachedDetail,
    startPolling,
    stopPolling,
  }
}
