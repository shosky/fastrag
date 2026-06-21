import { ref } from 'vue'
import type { ParseStrategy, ParseStrategyForm } from '@/types/knowledge'
import {
  getStrategies,
  getStrategy,
  createStrategy as mockCreate,
  updateStrategy as mockUpdate,
  deleteStrategy as mockDelete,
  setDefault as mockSetDefault,
  resolveByExtension as mockResolve,
  detectConflicts as mockDetectConflicts,
} from '@/mock/parse-strategy'

export function useParseStrategy(kbId: string = 'default') {
  const strategies = ref<ParseStrategy[]>([])
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      strategies.value = getStrategies(kbId)
    } finally {
      loading.value = false
    }
  }

  function create(form: ParseStrategyForm): ParseStrategy {
    const s = mockCreate(kbId, form)
    strategies.value = getStrategies(kbId)
    return s
  }

  function update(id: string, form: ParseStrategyForm): ParseStrategy | null {
    const s = mockUpdate(kbId, id, form)
    strategies.value = getStrategies(kbId)
    return s
  }

  function remove(id: string): { success: boolean; message?: string } {
    const result = mockDelete(kbId, id)
    if (result.success) {
      strategies.value = getStrategies(kbId)
    }
    return result
  }

  function setDefault(id: string): { success: boolean; message?: string } {
    const result = mockSetDefault(kbId, id)
    if (result.success) {
      strategies.value = getStrategies(kbId)
    }
    return result
  }

  function detail(id: string): ParseStrategy | null {
    return getStrategy(kbId, id)
  }

  function resolveByExtension(extension: string): ParseStrategy | null {
    return mockResolve(kbId, extension)
  }

  function detectConflicts(extensions: string[], excludeId?: string): ParseStrategy[] {
    return mockDetectConflicts(kbId, extensions, excludeId)
  }

  return {
    strategies,
    loading,
    load,
    create,
    update,
    remove,
    setDefault,
    detail,
    resolveByExtension,
    detectConflicts,
  }
}
