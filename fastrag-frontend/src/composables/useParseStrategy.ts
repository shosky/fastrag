import { ref } from 'vue'
import type { ParseStrategy, ParseStrategyForm } from '@/types/knowledge'
import * as api from '@/api'

export function useParseStrategy(kbId: string = 'default') {
  const strategies = ref<ParseStrategy[]>([])
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await api.fetchStrategies(kbId)
      strategies.value = (res as any) || []
    } finally {
      loading.value = false
    }
  }

  async function create(form: ParseStrategyForm): Promise<ParseStrategy> {
    const s = await api.createStrategyApi(kbId, form)
    await load()
    return s as ParseStrategy
  }

  async function update(id: string, form: ParseStrategyForm): Promise<ParseStrategy | null> {
    const s = await api.updateStrategyApi(kbId, id, form)
    await load()
    return s as ParseStrategy
  }

  async function remove(id: string): Promise<{ success: boolean; message?: string }> {
    try {
      await api.deleteStrategyApi(kbId, id)
      await load()
      return { success: true }
    } catch (e: any) {
      return { success: false, message: e.message }
    }
  }

  async function setDefault(id: string): Promise<{ success: boolean; message?: string }> {
    try {
      await api.setDefaultStrategyApi(kbId, id)
      await load()
      return { success: true }
    } catch (e: any) {
      return { success: false, message: e.message }
    }
  }

  async function detail(id: string): Promise<ParseStrategy | null> {
    try {
      return (await api.fetchStrategyDetail(kbId, id)) as ParseStrategy
    } catch {
      return null
    }
  }

  async function resolveByExtension(extension: string): Promise<ParseStrategy | null> {
    try {
      return (await api.resolveStrategy(kbId, extension)) as ParseStrategy
    } catch {
      return null
    }
  }

  async function detectConflicts(extensions: string[], excludeId?: string): Promise<ParseStrategy[]> {
    try {
      return ((await api.detectStrategyConflictsApi(kbId, extensions, excludeId)) as ParseStrategy[]) || []
    } catch {
      return []
    }
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
