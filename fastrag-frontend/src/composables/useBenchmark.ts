import { ref } from 'vue'
import type {
  Benchmark,
  BenchmarkQuestion,
  BenchmarkGenerateConfig,
} from '@/types/evaluation'
import {
  fetchBenchmarks,
  fetchBenchmarkDetail,
  createBenchmarkApi,
  generateBenchmarkApi,
  deleteBenchmarkApi,
} from '@/api'

export function useBenchmark(kbId: string = 'default') {
  const benchmarks = ref<Benchmark[]>([])
  const loading = ref(false)

  /** 基准下拉选项（供 RAG 评估面板使用，与基准列表同步） */
  const benchmarkOptions = ref<{ label: string; value: string }[]>([])

  async function load() {
    loading.value = true
    try {
      benchmarks.value = await fetchBenchmarks(kbId)
      syncOptions()
    } finally {
      loading.value = false
    }
  }

  function syncOptions() {
    benchmarkOptions.value = benchmarks.value.map((b) => ({
      label: `${b.name} (${b.questionCount} 个问题)`,
      value: b.name,
    }))
  }

  async function create(form: { name: string; description: string }, questionCount: number) {
    const bench = await createBenchmarkApi(kbId, form, questionCount)
    benchmarks.value.unshift(bench)
    syncOptions()
    return bench
  }

  async function generate(config: BenchmarkGenerateConfig) {
    const bench = await generateBenchmarkApi(kbId, config)
    benchmarks.value.unshift(bench)
    syncOptions()
    return bench
  }

  async function remove(id: string) {
    await deleteBenchmarkApi(kbId, id)
    benchmarks.value = benchmarks.value.filter((b) => b.id !== id)
    syncOptions()
  }

  /** 获取基准详情（问题列表） */
  async function detail(id: string): Promise<BenchmarkQuestion[]> {
    return fetchBenchmarkDetail(kbId, id)
  }

  return {
    benchmarks,
    benchmarkOptions,
    loading,
    load,
    create,
    generate,
    remove,
    detail,
  }
}
