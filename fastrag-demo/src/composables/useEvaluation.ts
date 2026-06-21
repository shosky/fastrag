import { ref, computed } from 'vue'
import type {
  Evaluation,
  EvaluationDetail,
  EvaluationStartConfig,
} from '@/types/evaluation'
import {
  fetchEvaluations,
  runEvaluationApi,
  deleteEvaluationApi,
} from '@/api'

export function useEvaluation(kbId: string = 'default') {
  const evaluations = ref<Evaluation[]>([])
  const loading = ref(false)
  const running = ref(false)

  const latest = computed<Evaluation | null>(() => evaluations.value[0] || null)

  async function load() {
    loading.value = true
    try {
      evaluations.value = await fetchEvaluations(kbId)
    } finally {
      loading.value = false
    }
  }

  /** 发起评估（真实闭环：召回→生成→评判→打分） */
  async function start(config: EvaluationStartConfig): Promise<EvaluationDetail> {
    running.value = true
    try {
      const detail = await runEvaluationApi(kbId, config)
      // 重新拉取列表以反映新增记录
      await load()
      return detail
    } finally {
      running.value = false
    }
  }

  async function remove(id: string) {
    await deleteEvaluationApi(kbId, id)
    evaluations.value = evaluations.value.filter((e) => e.id !== id)
  }

  return {
    evaluations,
    latest,
    loading,
    running,
    load,
    start,
    remove,
  }
}
